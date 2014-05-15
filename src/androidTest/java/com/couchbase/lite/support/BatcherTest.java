package com.couchbase.lite.support;

import com.couchbase.lite.Database;
import com.couchbase.lite.LiteTestCase;
import com.couchbase.lite.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class BatcherTest extends LiteTestCase {

    /**
     * Submit 101 objects to batcher, and make sure that batch
     * of first 100 are processed "immediately" (as opposed to being
     * subjected to a delay which would add latency)
     */
    public void testBatcherLatencyInitialBatch() throws Exception {

        final CountDownLatch doneSignal = new CountDownLatch(1);

        ScheduledExecutorService workExecutor = new ScheduledThreadPoolExecutor(1);

        int inboxCapacity = 100;
        int processorDelay = 500;

        final AtomicLong timeProcessed = new AtomicLong();

        Batcher batcher = new Batcher<String>(workExecutor, inboxCapacity, processorDelay, new BatchProcessor<String>() {

            @Override
            public void process(List<String> itemsToProcess) {
                Log.v(Database.TAG, "process called with: " + itemsToProcess);

                timeProcessed.set(System.currentTimeMillis());

                doneSignal.countDown();
            }

        });

        ArrayList<String> objectsToQueue = new ArrayList<String>();
        for (int i=0; i < inboxCapacity + 1; i++) {
            objectsToQueue.add(Integer.toString(i));
        }

        long timeQueued = System.currentTimeMillis();
        batcher.queueObjects(objectsToQueue);


        boolean didNotTimeOut = doneSignal.await(35, TimeUnit.SECONDS);
        assertTrue(didNotTimeOut);

        long delta = timeProcessed.get() - timeQueued;
        assertTrue(delta > 0);

        // we want the delta between the time it was queued until the
        // time it was processed to be as small as possible.  since
        // there is some overhead, rather than using a hardcoded number
        // express it as a ratio of the processor delay, asserting
        // that the entire processor delay never kicked in.
        int acceptableDelta = processorDelay - 1;

        Log.v(Log.TAG, "delta: %d", delta);

        assertTrue(delta < acceptableDelta);


    }

    /**
     * Set batch processing delay to 500 ms, and every second, add a new item
     * to the batcher queue.  Make sure that each item is processed immediately.
     */
    public void testBatcherLatencyTrickleIn() throws Exception {

        final CountDownLatch doneSignal = new CountDownLatch(10);

        ScheduledExecutorService workExecutor = new ScheduledThreadPoolExecutor(1);

        int inboxCapacity = 100;
        int processorDelay = 500;

        final AtomicLong maxObservedDelta = new AtomicLong(-1);

        Batcher batcher = new Batcher<Long>(workExecutor, inboxCapacity, processorDelay, new BatchProcessor<Long>() {

            @Override
            public void process(List<Long> itemsToProcess) {

                if (itemsToProcess.size() != 1) {
                    throw new RuntimeException("Unexpected itemsToProcess");
                }

                Long timeSubmitted = itemsToProcess.get(0);
                long delta = System.currentTimeMillis() - timeSubmitted.longValue();
                if (delta > maxObservedDelta.get()) {
                    maxObservedDelta.set(delta);
                }

                doneSignal.countDown();

            }

        });


        ArrayList<Long> objectsToQueue = new ArrayList<Long>();
        for (int i=0; i < 10; i++) {
            batcher.queueObjects(Arrays.asList(System.currentTimeMillis()));
            Thread.sleep(1000);
        }

        boolean didNotTimeOut = doneSignal.await(35, TimeUnit.SECONDS);
        assertTrue(didNotTimeOut);

        Log.v(Log.TAG, "maxDelta: %d", maxObservedDelta.get());

        // we want the max observed delta between the time it was queued until the
        // time it was processed to be as small as possible.  since
        // there is some overhead, rather than using a hardcoded number
        // express it as a ratio of 1/4th the processor delay, asserting
        // that the entire processor delay never kicked in.
        int acceptableMaxDelta = processorDelay -1;

        Log.v(Log.TAG, "maxObservedDelta: %d", maxObservedDelta.get());

        assertTrue((maxObservedDelta.get() < acceptableMaxDelta));

    }

    public void testBatcherSingleBatch() throws Exception {

        final CountDownLatch doneSignal = new CountDownLatch(10);

        ScheduledExecutorService workExecutor = new ScheduledThreadPoolExecutor(1);

        int inboxCapacity = 10;
        int processorDelay = 1000;

        Batcher batcher = new Batcher<String>(workExecutor, inboxCapacity, processorDelay, new BatchProcessor<String>() {

            @Override
            public void process(List<String> itemsToProcess) {
                Log.v(Database.TAG, "process called with: " + itemsToProcess);

                try {
                    Thread.sleep(100);  // add this to make it a bit more realistic
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                assertNumbersConsecutive(itemsToProcess);

                doneSignal.countDown();
            }

        });

        ArrayList<String> objectsToQueue = new ArrayList<String>();
        for (int i=0; i<inboxCapacity * 10; i++) {
            objectsToQueue.add(Integer.toString(i));
        }
        batcher.queueObjects(objectsToQueue);

        boolean didNotTimeOut = doneSignal.await(35, TimeUnit.SECONDS);
        assertTrue(didNotTimeOut);

    }

    public void testBatcherBatchSize5() throws Exception {

        final CountDownLatch doneSignal = new CountDownLatch(10);

        ScheduledExecutorService workExecutor = new ScheduledThreadPoolExecutor(1);

        int inboxCapacity = 10;
        final int processorDelay = 1000;

        Batcher batcher = new Batcher<String>(workExecutor, inboxCapacity, processorDelay, new BatchProcessor<String>() {

            @Override
            public void process(List<String> itemsToProcess) {
                Log.v(Database.TAG, "process called with: " + itemsToProcess);
                try {
                    Thread.sleep(processorDelay * 2); // add this to make it a bit more realistic
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                assertNumbersConsecutive(itemsToProcess);

                doneSignal.countDown();
            }

        });

        ArrayList<String> objectsToQueue = new ArrayList<String>();
        for (int i=0; i<inboxCapacity * 10; i++) {
            objectsToQueue.add(Integer.toString(i));
            if (objectsToQueue.size() == 5) {
                batcher.queueObjects(objectsToQueue);
                objectsToQueue = new ArrayList<String>();
            }

        }

        boolean didNotTimeOut = doneSignal.await(35, TimeUnit.SECONDS);
        assertTrue(didNotTimeOut);

    }

    public void testBatcherBatchSize1() throws Exception {

        final CountDownLatch doneSignal = new CountDownLatch(1);

        ScheduledExecutorService workExecutor = new ScheduledThreadPoolExecutor(1);

        int inboxCapacity = 100;
        final int processorDelay = 1000;

        Batcher batcher = new Batcher<String>(workExecutor, inboxCapacity, processorDelay, new BatchProcessor<String>() {

            @Override
            public void process(List<String> itemsToProcess) {
                Log.v(Database.TAG, "process called with: " + itemsToProcess);
                try {
                    Thread.sleep(processorDelay * 2); // add this to make it a bit more realistic
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                assertNumbersConsecutive(itemsToProcess);

                doneSignal.countDown();
            }

        });

        ArrayList<String> objectsToQueue = new ArrayList<String>();
        for (int i=0; i<inboxCapacity; i++) {
            objectsToQueue.add(Integer.toString(i));
            if (objectsToQueue.size() == 5) {
                batcher.queueObjects(objectsToQueue);
                objectsToQueue = new ArrayList<String>();
            }

        }

        boolean didNotTimeOut = doneSignal.await(35, TimeUnit.SECONDS);
        assertTrue(didNotTimeOut);

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
