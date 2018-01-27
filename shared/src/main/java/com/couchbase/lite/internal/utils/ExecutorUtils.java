package com.couchbase.lite.internal.utils;

import com.couchbase.lite.internal.support.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class ExecutorUtils {
    private ExecutorUtils() {
    }

    public static void shutdownAndAwaitTermination(ExecutorService pool, int waitSec) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(waitSec, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(waitSec, TimeUnit.SECONDS))
                    Log.e(Log.DATABASE, "Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }
}
