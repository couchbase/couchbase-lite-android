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

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * ExecutionService for Android.
 */
public final class AndroidExecutionService extends AbstractExecutionService {

    //---------------------------------------------
    // Types
    //---------------------------------------------
    private static class CancellableTask implements Cancellable {
        private final Handler handler;
        private final Runnable task;

        private CancellableTask(@NonNull Handler handler, @NonNull Runnable task) {
            Preconditions.assertNotNull(handler, "handler");
            Preconditions.assertNotNull(task, "task");
            this.handler = handler;
            this.task = task;
        }

        @Override
        public void cancel() { handler.removeCallbacks(task); }
    }


    //---------------------------------------------
    // Instance variables
    //---------------------------------------------
    private final Handler mainHandler;
    private final Executor mainThreadExecutor;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------
    public AndroidExecutionService() {
        super((ThreadPoolExecutor) AsyncTask.THREAD_POOL_EXECUTOR);
        mainHandler = new Handler(Looper.getMainLooper());
        mainThreadExecutor = mainHandler::post;
    }

    //---------------------------------------------
    // Public methods
    //---------------------------------------------
    @NonNull
    @Override
    public Executor getMainExecutor() { return mainThreadExecutor; }

    /**
     * This runs a task on Android's main thread for just long enough to enough to enqueue the passed task
     * on the passed executor.  It occupies space in the main looper's message queue
     * but takes no significant time processing, there.
     * If the target executor refuses the task, it is just dropped on the floor.
     *
     * @param delayMs  delay before posting the task.  There may be additional queue delays in the executor.
     * @param executor an executor on which to execute the task.
     * @param task     the task to be executed.
     * @return a cancellable task
     */
    @NonNull
    @Override
    public Cancellable postDelayedOnExecutor(long delayMs, @NonNull Executor executor, @NonNull Runnable task) {
        Preconditions.assertNotNull(executor, "executor");
        Preconditions.assertNotNull(task, "task");

        final Runnable delayedTask = () -> {
            try { executor.execute(task); }
            catch (CloseableExecutor.ExecutorClosedException e) {
                Log.w(LogDomain.DATABASE, "Scheduled on closed executor: " + task + ", " + executor);
            }
            catch (RejectedExecutionException e) {
                if (!throttled()) { dumpServiceState(executor, "after: " + delayMs, e); }
            }
        };

        mainHandler.postDelayed(delayedTask, delayMs);

        return new CancellableTask(mainHandler, delayedTask);
    }

    /**
     * Best effort, delete the passed task (obtained from postDelayedOnExecutor, above)
     * from the wait queue.  If it is already in the Executor, well, there you go.
     *
     * @param cancellableTask returned by a previous call to postDelayedOnExecutor.
     */
    @Override
    public void cancelDelayedTask(@NonNull Cancellable cancellableTask) {
        Preconditions.assertNotNull(cancellableTask, "cancellableTask");
        cancellableTask.cancel();
    }
}
