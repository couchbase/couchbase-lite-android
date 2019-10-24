//
// ExecutionService.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite.internal;

import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public final class AndroidExecutionService implements ExecutionService {
    private static final String TAG = "EXEC_SVC";

    public static class TrackableTask implements Runnable {
        private final Exception origin = new Exception();
        private final Runnable task;
        private final Runnable onComplete;

        private final long createdAt = System.currentTimeMillis();
        private long startedAt;
        private long finishedAt;
        private long completedAt;

        public TrackableTask(Runnable task, Runnable onComplete) {
            this.task = task;
            this.onComplete = onComplete;
        }

        public void run() {
            try {
                startedAt = System.currentTimeMillis();
                task.run();
                finishedAt = System.currentTimeMillis();
            }
            finally {
                onComplete.run();
            }
            completedAt = System.currentTimeMillis();
        }

        public String toString() {
            return "task[" + createdAt + "," + startedAt + "," + finishedAt + "," + completedAt + " @" + task + "]";
        }
    }

    // thin wrapper around the AsyncTask's THREAD_POOL_EXECUTOR
    private static class ConcurrentExecutor implements CloseableExecutor {
        private final Executor executor;
        private CountDownLatch stopLatch;
        private int running;

        private ConcurrentExecutor(Executor executor) { this.executor = executor; }

        @Override
        public synchronized void execute(Runnable task) {
            if (stopLatch != null) { throw new RejectedExecutionException("Executor has been stopped"); }

            try {
                executor.execute(new TrackableTask(task, this::finishTask));
                running++;
            }
            catch (RejectedExecutionException e) {
                dumpThreadState(executor, e, "size: " + running);
                throw e;
            }
        }

        @Override
        public boolean stop(long timeout, @NonNull TimeUnit unit) {
            final CountDownLatch latch;
            synchronized (this) {
                if (stopLatch == null) { stopLatch = new CountDownLatch(running); }
                if (running <= 0) { return true; }
                latch = stopLatch;
            }

            try { return latch.await(timeout, unit); }
            catch (InterruptedException ignore) { }

            return false;
        }

        private void finishTask() {
            final CountDownLatch latch;
            synchronized (this) {
                running--;
                latch = stopLatch;
            }

            if (latch != null) { latch.countDown(); }
        }
    }

    // Patterned after AsyncTask's executor
    private static class SerialExecutor implements ExecutionService.CloseableExecutor {
        private final ArrayDeque<TrackableTask> tasks = new ArrayDeque<>();
        private final Executor executor;
        private CountDownLatch stopLatch;

        private TrackableTask running;

        private SerialExecutor(Executor executor) { this.executor = executor; }

        public synchronized void execute(Runnable task) {
            if (stopLatch != null) { throw new RejectedExecutionException("Executor has been stopped"); }

            tasks.offer(new TrackableTask(task, this::scheduleNext));

            if (running == null) { scheduleNext(); }
        }

        @Override
        public boolean stop(long timeout, @NonNull TimeUnit unit) {
            final CountDownLatch latch;
            synchronized (this) {
                if (stopLatch == null) {
                    stopLatch = new CountDownLatch((running == null) ? 0 : tasks.size() + 1);
                }

                latch = stopLatch;
            }

            if (latch.getCount() <= 0) { return true; }

            try { return latch.await(timeout, unit); }
            catch (InterruptedException ignore) { }

            return false;
        }

        private synchronized void scheduleNext() {
            if (stopLatch != null) { stopLatch.countDown(); }

            final TrackableTask prev = running;
            running = tasks.poll();
            if (running == null) { return; }

            try { executor.execute(running); }
            catch (RejectedExecutionException e) {
                dumpThreadState(executor, e, "size: " + tasks.size());
                dumpQueue(prev, running, tasks);
                running = null;
            }
        }

        private void dumpQueue(TrackableTask prev, TrackableTask current, Deque<TrackableTask> tasks) {
            android.util.Log.d(TAG, "\n==== Serial Executor status: " + this);

            if (prev != null) { android.util.Log.d(TAG, "\n== Previous task: " + prev, prev.origin); }

            if (current != null) { android.util.Log.d(TAG, "\n== Current task: " + current, current.origin); }

            final ArrayList<TrackableTask> waiting = new ArrayList<>(tasks);
            android.util.Log.d(TAG, "\n== Waiting tasks: " + waiting.size());
            int n = 0;
            for (TrackableTask t : waiting) { android.util.Log.d(TAG, "@" + (++n) + ": " + t, t.origin); }
        }
    }


    private final Handler mainHandler;
    private final ThreadPoolExecutor baseExecutor;
    private final Executor mainThreadExecutor;
    private final CloseableExecutor concurrentExecutor;

    public AndroidExecutionService() { this((ThreadPoolExecutor) AsyncTask.THREAD_POOL_EXECUTOR); }

    @VisibleForTesting
    public AndroidExecutionService(ThreadPoolExecutor executor) {
        baseExecutor = executor;

        this.concurrentExecutor = new ConcurrentExecutor(baseExecutor);

        mainHandler = new Handler(Looper.getMainLooper());

        mainThreadExecutor = mainHandler::post;
    }

    @NonNull
    @Override
    public Executor getMainExecutor() { return mainThreadExecutor; }

    public CloseableExecutor getSerialExecutor() { return new SerialExecutor(baseExecutor); }

    @NonNull
    @Override
    public CloseableExecutor getConcurrentExecutor() { return concurrentExecutor; }

    /**
     * This runs a task on Android's main thread for just long enough to enough to enqueue the passed task
     * on the passed executor.  It occupies space in the main looper's message queue
     * but takes no significant time processing, there.
     * If the target executor refuses the task, it is just dropped on the floor.
     *
     * @param delayMs  delay before posting the task.  There may be additional queue delays in the executor.
     * @param executor an executor on which to execute the task.
     * @param task     the task to be executed.
     */
    @NonNull
    @Override
    public Runnable postDelayedOnExecutor(long delayMs, @NonNull Executor executor, @NonNull Runnable task) {
        if (null == task) { throw new IllegalArgumentException("Task may not be null"); }
        if (null == executor) { throw new IllegalArgumentException("Executor may not be null"); }

        final Exception origin = new Exception();
        final Runnable delayedTask = () -> {
            try { executor.execute(task); }
            catch (RejectedExecutionException e) { dumpThreadState(executor, e, origin, "after: " + delayMs); }
        };

        mainHandler.postDelayed(delayedTask, delayMs);

        return delayedTask;
    }

    /**
     * Best effort, delete the passed task (obtained from postDelayedOnExecutor, above)
     * from the wait queue.  If it is already in the Executor, well, there you go.
     *
     * @param task the task to be executed.
     */
    @Override
    public void cancelDelayedTask(@NonNull Runnable task) {
        if (null == task) { throw new IllegalArgumentException("Task may not be null"); }
        mainHandler.removeCallbacks(task);
    }

    @VisibleForTesting
    public ThreadPoolExecutor getBaseExecutor() { return baseExecutor; }

    private static void dumpThreadState(Executor ex, Exception e, String msg) { dumpThreadState(ex, e, null, msg); }

    private static void dumpThreadState(Executor ex, Exception e, Exception origin, String msg) {
        android.util.Log.d(TAG, "!!!! Catastrophic failure on executor " + ex + ": " + msg, e);
        if (origin != null) { android.util.Log.d(TAG, "!! Origin: ", origin); }

        final Map<Thread, StackTraceElement[]> stackTraces = Thread.getAllStackTraces();
        android.util.Log.d(TAG, "\n==== Threads: " + stackTraces.size());
        for (Map.Entry<Thread, StackTraceElement[]> stack : stackTraces.entrySet()) {
            android.util.Log.d(TAG, "\n== Thread: " + stack.getKey());
            for (StackTraceElement frame : stack.getValue()) { android.util.Log.d(TAG, "      at " + frame); }
        }

        if (!(ex instanceof ThreadPoolExecutor)) { return; }

        final ArrayList<Runnable> waiting = new ArrayList<>(((ThreadPoolExecutor) ex).getQueue());
        android.util.Log.d(TAG, "\n==== Executor queue: " + waiting.size());
        int n = 0;
        for (Runnable r : waiting) {
            final Exception orig = (!(r instanceof TrackableTask)) ? null : ((TrackableTask) r).origin;
            android.util.Log.d(TAG, "@" + (n++) + ": " + r, orig);
        }
    }
}
