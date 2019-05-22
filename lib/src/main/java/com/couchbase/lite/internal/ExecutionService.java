//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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

import android.support.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;


public interface ExecutionService {
    /**
     * Like an ExecutorService, but simpler.
     * It is not up to the client to decide whether to terminate the backing Executor or not.
     * They simply get to say that they are done with it
     */
    interface CloseableExecutor extends Executor {
        /**
         * The executor will accept no more tasks.
         * It will complete executing all currently enqueued tasks, if possible.
         * This method will return when all tasks have run or when the timeout elapses, whichever comes first.
         *
         * @param timeout time to wait for shutdown
         * @param unit time unit for shutdown wait
         */
        void stop(long timeout, @NonNull TimeUnit unit);
    }

    /**
     * Get the main executor.  It is guaranteed to be a single thread.
     * The thread on which most of the application runs.
     * Suitable for any task that doesn't take a long time to complete.
     *
     * @return the main executor.
     */
    @NonNull
    Executor getMainExecutor();

    /**
     * Get a new, serial executor.  Not a single thread but does guarantee serial execution.
     * Suitable for heavyweight that must be executed in order.  That is most of them.
     *
     * @return a serial executor.
     */
    @NonNull
    CloseableExecutor getSerialExecutor();

    /**
     * Get the concurrent execution service.  Executes tasks on a multi-threaded Executor.
     * Suitable for heavyweight tasks.  There is no guarantee for order of execution.
     *
     * @return the background thread-pool executor.
     */
    @NonNull
    CloseableExecutor getConcurrentExecutor();

    /**
     * Run the passed task on the passed executor, after a delay
     *
     * @param delayMs  delay before posting the task.  There may be additional queue delays in the executor.
     * @param executor a executor on which to execute the task.
     * @param task     the task to be executed.
     * @return a cancellable task
     */
    Runnable postDelayedOnExecutor(long delayMs, @NonNull Executor executor, @NonNull Runnable task);

    /**
     * Best effort cancellation of a delayed task.
     *
     * @param cancellableTask returned by a previous call to postDelayedOnExecutor.
     */
    void cancelDelayedTask(@NonNull Runnable cancellableTask);
}
