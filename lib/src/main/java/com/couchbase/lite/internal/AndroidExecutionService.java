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

import java.util.ArrayDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;


public final class AndroidExecutionService implements ExecutionService {

    // thin wrapper around the AsyncTask's THREAD_POOL_EXECUTOR
    private static class ConcurrentExecutor implements CloseableExecutor {
        private final Executor executor;
        private CountDownLatch stopLatch;
        private int running;

        private ConcurrentExecutor(Executor executor) { this.executor = executor; }

        @Override
        public synchronized void execute(Runnable task) {
            if (stopLatch != null) { throw new RejectedExecutionException("Executor has been stopped"); }

            running++;

            executor.execute(() -> {
                try { task.run(); }
                finally { finishTask(); }
            });
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
    private static class SerialExecutor implements CloseableExecutor {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        private final Executor executor;
        private CountDownLatch stopLatch;

        private Runnable running;

        private SerialExecutor(Executor executor) { this.executor = executor; }

        public synchronized void execute(Runnable task) {
            if (stopLatch != null) { throw new RejectedExecutionException("Executor has been stopped"); }

            tasks.offer(() -> {
                try { task.run(); }
                finally { scheduleNext(); }
            });

            if (running == null) { scheduleNext(); }
        }

        @Override
        public boolean stop(long timeout, @NonNull TimeUnit unit) {
            final CountDownLatch latch;
            final int n;
            synchronized (this) {
                n = (running == null) ? 0 : tasks.size() + 1;

                if (stopLatch == null) { stopLatch = new CountDownLatch(n); }

                if (n <= 0) { return true; }

                latch = stopLatch;
            }

            try { return latch.await(timeout, unit); }
            catch (InterruptedException ignore) { }

            return false;
        }

        private synchronized void scheduleNext() {
            if (stopLatch != null) { stopLatch.countDown(); }

            running = tasks.poll();

            if (running != null) { executor.execute(running); }
        }
    }


    private final Handler mainHandler;
    private final Executor mainThreadExecutor;
    private final CloseableExecutor concurrentExecutor;

    public AndroidExecutionService() {
        mainHandler = new Handler(Looper.getMainLooper());

        mainThreadExecutor = mainHandler::post;

        concurrentExecutor = new ConcurrentExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @NonNull
    @Override
    public Executor getMainExecutor() { return mainThreadExecutor; }

    public CloseableExecutor getSerialExecutor() { return new SerialExecutor(AsyncTask.THREAD_POOL_EXECUTOR); }

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

        final Runnable delayedTask = () -> {
            try { executor.execute(task); }
            catch (RejectedExecutionException ignored) { }
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
}
