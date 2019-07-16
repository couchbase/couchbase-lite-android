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

import com.couchbase.lite.internal.utils.Preconditions;

/**
 * ExecutionService for Android.
 */
public final class AndroidExecutionService extends AbstractExecutionService {
    private static class CancellableTask implements Cancellable {
        private Handler handler;
        private Runnable task;

        private CancellableTask(@NonNull Handler handler, @NonNull Runnable task) {
            Preconditions.checkArgNotNull(handler, "handler");
            Preconditions.checkArgNotNull(task, "task");
            this.handler = handler;
            this.task = task;
        }

        @Override
        public void cancel() {
            handler.removeCallbacks(task);
        }
    }

    private final Handler mainHandler;
    private final Executor mainThreadExecutor;

    public AndroidExecutionService() {
        mainHandler = new Handler(Looper.getMainLooper());
        mainThreadExecutor = mainHandler::post;
    }

    @NonNull
    @Override
    public Executor getThreadPoolExecutor() { return AsyncTask.THREAD_POOL_EXECUTOR; }

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
        Preconditions.checkArgNotNull(executor, "executor");
        Preconditions.checkArgNotNull(task, "task");
        final Runnable delayedTask = () -> {
            try { executor.execute(task); }
            catch (RejectedExecutionException ignored) { }
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
        Preconditions.checkArgNotNull(cancellableTask, "cancellableTask");
        cancellableTask.cancel();
    }
}
