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
package com.couchbase.lite;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.junit.Ignore;
import org.junit.Test;

import com.couchbase.lite.internal.AbstractExecutionService;
import com.couchbase.lite.internal.AndroidExecutionService;


public class AndroidExecutionServiceTest {
    @Ignore("This is not actually a test.  Use it to verify logcat output")
    @Test(expected = RejectedExecutionException.class)
    public void testSerialExecutorFailure() {
        AndroidExecutionService execSvc = new AndroidExecutionService();
        ThreadPoolExecutor exec = execSvc.getBaseExecutor();
        Executor serialExcecutor = execSvc.getSerialExecutor();

        // hang the queue
        final CountDownLatch latch = new CountDownLatch(1);
        serialExcecutor.execute(() -> {
            try { latch.await(2, TimeUnit.SECONDS); }
            catch (InterruptedException ignore) { }
        });

        // put some stuff in the serial executor queue
        for (int i = 1; i < 10; i++) { serialExcecutor.execute(() -> {}); }

        // fill the base executor.
        try {
            while (true) {
                exec.execute(
                    new AbstractExecutionService.InstrumentedTask(
                        () -> {
                            try { Thread.sleep(10 * 1000); }
                            catch (InterruptedException ignore) {}
                        },
                        () -> {}));
            }
        }
        catch (RejectedExecutionException ignore) { }

        // this should free the running serial job,
        // which should fail trying to start the next job
        latch.countDown();
    }

    @Ignore("This is not actually a test.  Use it to verify logcat output")
    @Test(expected = RejectedExecutionException.class)
    public void testConcurrentExecutorFailure() {
        AndroidExecutionService execSvc = new AndroidExecutionService();
        ThreadPoolExecutor exec = execSvc.getBaseExecutor();

        // fill the executor
        try {
            while (true) {
                exec.execute(
                    new AndroidExecutionService.InstrumentedTask(
                        () -> {
                            try { Thread.sleep(10 * 1000); }
                            catch (InterruptedException ignore) {}
                        },
                        () -> {}));
            }
        }
        catch (RejectedExecutionException ignore) { }

        // this should fail because the executor is full
        execSvc.getConcurrentExecutor().execute(() -> {});
    }
}

