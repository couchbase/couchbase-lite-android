//
// ExecutorUtils.java
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
package com.couchbase.lite.internal.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.support.Log;


public class ExecutorUtils {
    private static final Object lock = new Object();

    public static void shutdownAndAwaitTermination(ExecutorService pool, int waitSec) {
        // Disable new tasks from being submitted
        final boolean isShutdown;
        synchronized (lock) {
            isShutdown = pool.isTerminated() || pool.isShutdown();
            pool.shutdown();
        }
        if (isShutdown) { return; }

        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(waitSec, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(waitSec, TimeUnit.SECONDS)) {
                    Log.e(LogDomain.DATABASE, "Pool did not terminate");
                }
            }
        }
        catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    private ExecutorUtils() {
    }
}
