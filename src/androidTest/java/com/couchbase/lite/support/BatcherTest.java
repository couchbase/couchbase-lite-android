package com.couchbase.lite.support;

import com.couchbase.lite.Database;
import com.couchbase.lite.LiteTestCase;
import com.couchbase.lite.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BatcherTest extends LiteTestCase {

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

        boolean didNotTimeOut = doneSignal.await(5, TimeUnit.SECONDS);
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
