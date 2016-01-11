package com.couchbase.lite.support;

import com.couchbase.lite.Database;
import com.couchbase.lite.LiteTestCase;
import com.couchbase.lite.Manager;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class BatcherTest extends LiteTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Manager.enableLogging(Log.TAG_BATCHER, Log.VERBOSE);
    }

    /**
     * Add 100 items in a batcher and make sure that the processor
     * is correctly called back with the first batch.
     */
    public void testBatcherSingleBatch() throws Exception {
        int numBatches = 3;
        final CountDownLatch doneSignal = new CountDownLatch(numBatches);

        ScheduledExecutorService workExecutor = new ScheduledThreadPoolExecutor(1);

        int inboxCapacity = 10;
        int processorDelay = 200;

        Batcher batcher = new Batcher<String>(workExecutor, inboxCapacity, processorDelay, new BatchProcessor<String>() {

            @Override
            public void process(List<String> itemsToProcess) {
                Log.e(TAG, "process called with: " + itemsToProcess);
                assertEquals(10, itemsToProcess.size());
                assertNumbersConsecutive(itemsToProcess);
                doneSignal.countDown();
            }
        });

        ArrayList<String> objectsToQueue = new ArrayList<String>();
        for (int i = 0; i < inboxCapacity * numBatches; i++) {
            objectsToQueue.add(Integer.toString(i));
        }
        batcher.queueObjects(objectsToQueue);

        boolean didNotTimeOut = doneSignal.await(35, TimeUnit.SECONDS);
        assertTrue(didNotTimeOut);

        // Note: ExecutorService should be called shutdown()
        Utils.shutdownAndAwaitTermination(workExecutor);
    }

    /**
     * With a batcher that has an inbox of size 10, add 10 * x items in batches
     * of 5.  Make sure that the processor is called back with all 10 * x items.
     * Also make sure that they appear in the correct order within a batch.
     */
    public void testBatcherBatchSize5() throws Exception {
        ScheduledExecutorService workExecutor = new ScheduledThreadPoolExecutor(1);

        int inboxCapacity = 10;
        int numItemsToSubmit = inboxCapacity * 2;
        final int processorDelay = 0;

        final CountDownLatch doneSignal = new CountDownLatch(numItemsToSubmit);

        Batcher batcher = new Batcher<String>(workExecutor, inboxCapacity, processorDelay, new BatchProcessor<String>() {

            @Override
            public void process(List<String> itemsToProcess) {
                Log.v(Database.TAG, "process called with: " + itemsToProcess);

                assertNumbersConsecutive(itemsToProcess);

                for (String item : itemsToProcess) {
                    doneSignal.countDown();
                }

                Log.v(Database.TAG, "doneSignal: " + doneSignal.getCount());

            }

        });

        ArrayList<String> objectsToQueue = new ArrayList<String>();
        for (int i = 0; i < numItemsToSubmit; i++) {
            objectsToQueue.add(Integer.toString(i));
            if (objectsToQueue.size() == 5) {
                batcher.queueObjects(objectsToQueue);
                objectsToQueue = new ArrayList<String>();
            }

        }

        boolean didNotTimeOut = doneSignal.await(35, TimeUnit.SECONDS);
        assertTrue(didNotTimeOut);

        // Note: ExecutorService should be called shutdown()
        Utils.shutdownAndAwaitTermination(workExecutor);
    }

    /**
     * Reproduce issue:
     * https://github.com/couchbase/couchbase-lite-java-core/issues/283
     * <p/>
     * This sporadically fails on the genymotion emulator and Nexus 5 device.
     */
    public void testBatcherThreadSafe() throws Exception {
        // 10 threads using the same batcher
        // each thread queues a bunch of items and makes sure they were all processed

        ScheduledExecutorService workExecutor = new ScheduledThreadPoolExecutor(1);
        int inboxCapacity = 10;
        final int processorDelay = 200;

        int numThreads = 5;
        final int numItemsPerThread = 20;
        int numItemsTotal = numThreads * numItemsPerThread;
        final AtomicInteger numItemsProcessed = new AtomicInteger(0);

        final CountDownLatch allItemsProcessed = new CountDownLatch(numItemsTotal);

        final Batcher batcher = new Batcher<String>(workExecutor, inboxCapacity, processorDelay, new BatchProcessor<String>() {

            @Override
            public void process(List<String> itemsToProcess) {
                for (String item : itemsToProcess) {
                    int curVal = numItemsProcessed.incrementAndGet();
                    Log.d(Log.TAG, "%d items processed so far", curVal);
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    allItemsProcessed.countDown();
                }
            }
        });

        for (int i = 0; i < numThreads; i++) {
            final String iStr = Integer.toString(i);
            Runnable runnable = new Runnable() {
                @Override
                public void run() {

                    for (int j = 0; j < numItemsPerThread; j++) {
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        String item = String.format("%s-item:%d", iStr, j);
                        batcher.queueObject(item);
                    }
                }
            };
            new Thread(runnable).start();
        }

        Log.d(TAG, "waiting for allItemsProcessed");
        boolean success = allItemsProcessed.await(120, TimeUnit.SECONDS);
        assertTrue(success);
        Log.d(TAG, "/waiting for allItemsProcessed");

        assertEquals(numItemsTotal, numItemsProcessed.get());
        assertEquals(0, batcher.count());

        Log.d(TAG, "waiting for pending futures");
        batcher.waitForPendingFutures();
        Log.d(TAG, "/waiting for pending futures");

        // Note: ExecutorService should be called shutdown()
        Utils.shutdownAndAwaitTermination(workExecutor);
    }

    /**
     * - Fill batcher up to capacity
     * - Expected behavior: should invoke BatchProcessor almost immediately
     * - Add a single element to batcher
     * - Expected behavior: after processing delay has expired, should invoke BatchProcessor
     * <p/>
     * https://github.com/couchbase/couchbase-lite-java-core/issues/329
     */
    public void testBatcherWaitsForProcessorDelay1() throws Exception {
        long timeBeforeQueue;
        long timeAfterCallback;
        long delta;
        boolean success;

        ScheduledExecutorService workExecutor = new ScheduledThreadPoolExecutor(1);
        int inboxCapacity = 5;
        int processorDelay = 1000;

        final CountDownLatch latch1 = new CountDownLatch(1);
        final CountDownLatch latch2 = new CountDownLatch(2);

        final Batcher batcher = new Batcher<String>(workExecutor, inboxCapacity, processorDelay, new BatchProcessor<String>() {
            @Override
            public void process(List<String> itemsToProcess) {
                Log.d(TAG, "process() called with %d items", itemsToProcess.size());
                latch1.countDown();
                latch2.countDown();
            }
        });

        // add a single object
        timeBeforeQueue = System.currentTimeMillis();
        batcher.queueObject(new String());

        // we shouldn't see latch close until processorDelay milliseconds has passed
        success = latch1.await(5, TimeUnit.SECONDS);
        assertTrue(success);
        //timeAfterCallback = System.currentTimeMillis();
        //delta = timeAfterCallback - timeBeforeQueue;

        // add a single object
        timeBeforeQueue = System.currentTimeMillis();
        batcher.queueObject(new String());
        // we shouldn't see latch close until processorDelay milliseconds has passed
        success = latch2.await(5, TimeUnit.SECONDS);
        assertTrue(success);
        timeAfterCallback = System.currentTimeMillis();
        delta = timeAfterCallback - timeBeforeQueue;
        assertTrue(delta > 0);
        assertTrue(delta >= processorDelay);

        // Note: ExecutorService should be called shutdown()
        Utils.shutdownAndAwaitTermination(workExecutor);
    }


    /**
     * - Fill batcher up to capacity
     * - Expected behavior: should invoke BatchProcessor almost immediately
     * - Add a single element to batcher
     * - Expected behavior: after processing delay has expired, should invoke BatchProcessor
     * <p/>
     * https://github.com/couchbase/couchbase-lite-java-core/issues/329
     */
    public void testBatcherWaitsForProcessorDelay2() throws Exception {
        ScheduledExecutorService workExecutor = new ScheduledThreadPoolExecutor(1);
        int inboxCapacity = 5;
        int processorDelay = 1000;

        final BlockingQueue<CountDownLatch> latches = new LinkedBlockingQueue<CountDownLatch>();
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        latches.add(latch1);
        latches.add(latch2);

        Batcher batcher = new Batcher<String>(workExecutor, inboxCapacity,
                processorDelay,
                new BatchProcessor<String>() {
                    @Override
                    public void process(List<String> itemsToProcess) {
                        try {
                            Log.d(TAG, "process() called with %d items", itemsToProcess.size());
                            CountDownLatch latch = latches.take();
                            latch.countDown();
                        } catch (InterruptedException e) {
                            assertFalse(true);
                            throw new RuntimeException(e);
                        }
                    }
                });

        // fill up batcher capacity
        for (int i = 0; i < inboxCapacity; i++)
            batcher.queueObject(new String());

        // latch should have been closed nearly immediately
        boolean success = latch1.await(500, TimeUnit.MILLISECONDS);
        assertTrue(success);

        long timeBeforeQueue = System.currentTimeMillis();

        // add another object
        batcher.queueObject(new String());

        // we shouldn't see latch close until processorDelay milliseconds has passed
        success = latch2.await(5, TimeUnit.SECONDS);
        assertTrue(success);
        long delta = System.currentTimeMillis() - timeBeforeQueue;
        Log.d(TAG,"delta => " + delta + "ms");
        assertTrue(delta >= processorDelay);

        // Note: ExecutorService should be called shutdown()
        Utils.shutdownAndAwaitTermination(workExecutor);
    }

    /**
     * - Add jobs with 10x the capacity
     * - Call waitForPendingFutures
     * - Make sure all jobs are processed before waitForPendingFutures returns
     * <p/>
     * https://github.com/couchbase/couchbase-lite-java-core/issues/329
     */
    public void testWaitForPendingFutures() throws Exception {
        ScheduledExecutorService workExecutor = new ScheduledThreadPoolExecutor(1);
        int inboxCapacity = 3;
        int processorDelay = 100;
        int numItemsToSubmit = 30;

        final CountDownLatch latch = new CountDownLatch(numItemsToSubmit);
        Batcher batcher = new Batcher<String>(workExecutor, inboxCapacity, processorDelay, new BatchProcessor<String>() {
            @Override
            public void process(List<String> itemsToProcess) {
                Log.d(TAG, "process() called with %d items", itemsToProcess.size());
                for (String item : itemsToProcess) {
                    latch.countDown();
                }
            }
        });

        // add numItemsToSubmit to batcher in one swoop
        ArrayList<String> objectsToQueue = new ArrayList<String>();
        for (int i = 0; i < numItemsToSubmit; i++) {
            objectsToQueue.add(Integer.toString(i));
        }
        batcher.queueObjects(objectsToQueue);

        // wait until all work drains
        batcher.waitForPendingFutures();

        // at this point, the countdown latch should be 0
        assertEquals(0, latch.getCount());

        // Note: ExecutorService should be called shutdown()
        Utils.shutdownAndAwaitTermination(workExecutor);
    }

    /**
     * - Call batcher to queue a single item in a fast loop
     * - As soon as we've hit capacity, it should call processor shortly after
     */
    public void testInvokeProcessorAfterReachingCapacity() throws Exception {
        ScheduledExecutorService workExecutor = new ScheduledThreadPoolExecutor(1);
        final int inboxCapacity = 5;
        final int numItemsToSubmit = 100;
        final int processorDelay = 1000; // 1000ms

        final CountDownLatch latchFirstProcess = new CountDownLatch(1);
        final CountDownLatch latchSubmittedCapacity = new CountDownLatch(1);

        final Batcher batcher = new Batcher<String>(workExecutor,
                inboxCapacity,
                processorDelay,
                new BatchProcessor<String>() {
                    @Override
                    public void process(List<String> itemsToProcess) {
                        Log.d(TAG, "process() called with %d items", itemsToProcess.size());
                        if (latchFirstProcess.getCount() > 0)
                            latchFirstProcess.countDown();
                    }
                });

        final CountDownLatch monitorThread = new CountDownLatch(1);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = 0; i < numItemsToSubmit; i++) {
                    if (i == inboxCapacity)
                        latchSubmittedCapacity.countDown();
                    batcher.queueObject(Integer.toString(i));
                    Log.d(TAG, "Submitted object %d", i);
                }
                monitorThread.countDown();
            }
        });
        t.start();

        // NOTE: 5sec could be too long
        boolean success = latchSubmittedCapacity.await(5, TimeUnit.SECONDS);
        assertTrue(success);

        // since we've already submitted up to capacity, our processor should
        // be called nearly immediately afterwards
        // NOTE: latchFirstProcess should be 0 after between 50ms and 1000ms.
        //       But it seems 100ms is not good enough for slow simulator.
        //       This is reason that currently waits 500ms.
        success = latchFirstProcess.await(500, TimeUnit.MILLISECONDS);
        assertTrue(success);

        monitorThread.await();

        batcher.waitForPendingFutures();

        // Note: ExecutorService should be called shutdown()
        Utils.shutdownAndAwaitTermination(workExecutor);
    }

    private static void assertNumbersConsecutive(List<String> itemsToProcess) {
        int previousItemNumber = -1;
        for (String itemString : itemsToProcess) {
            if (previousItemNumber == -1) {
                previousItemNumber = Integer.parseInt(itemString);
            } else {
                int curItemNumber = Integer.parseInt(itemString);
                assertTrue(curItemNumber == previousItemNumber + 1);
                previousItemNumber = curItemNumber;
            }
        }
    }
}
