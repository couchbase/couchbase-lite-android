//
// DefaultExecutor.java
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
package com.couchbase.lite;

import android.support.annotation.NonNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;


final class DefaultExecutor implements Executor {
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    private static final int CORE_POOL_SIZE = Math.max(2, Math.min(CPU_COUNT - 1, 4));
    private static final int MAX_POOL_SIZE = CPU_COUNT * 2 + 1;
    private static final int KEEP_ALIVE_SECS = 30;
    private static final int MAX_QUEUE_SIZE = 64;

    private static final ThreadFactory THREAD_FACTORY = new ThreadFactory() {
        private final AtomicInteger mCount = new AtomicInteger(1);
        public Thread newThread(@NonNull Runnable r) { return new Thread(r, "CBLite#" + mCount.getAndIncrement()); }
    };

    private static final BlockingQueue<Runnable> WORK_QUEUE = new LinkedBlockingQueue<>(MAX_QUEUE_SIZE);

    /**
     * An {@link Executor} that can be used to execute tasks in parallel.
     */
    public static final Executor EXECUTOR;
    static {
        final ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
            CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_SECS, TimeUnit.SECONDS, WORK_QUEUE, THREAD_FACTORY);
        threadPoolExecutor.allowCoreThreadTimeOut(true);
        EXECUTOR = threadPoolExecutor;
    }

    private static volatile DefaultExecutor instance;

    public static DefaultExecutor getInstance() {
        if (instance == null) { instance = new DefaultExecutor(); }
        return instance;
    }

    private DefaultExecutor() { }

    @Override
    public void execute(@NonNull Runnable task) {
        EXECUTOR.execute(task);
    }
}
