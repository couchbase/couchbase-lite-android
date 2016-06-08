/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite.replicator;

import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.mockserver.MockChangesFeedNoResponse;
import com.couchbase.lite.mockserver.MockCheckpointPut;
import com.couchbase.lite.mockserver.MockDispatcher;
import com.couchbase.lite.mockserver.MockHelper;
import com.couchbase.lite.util.Log;

import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockWebServer;

public class ManualReplicationTest extends LiteTestCaseWithDB {

    // https://github.com/couchbase/couchbase-lite-java-core/issues/1116
    // Multiple ChangeTrackers are started (Scenario 3)
    //
    // This test is not solid test.  I set this as manual test. Not run on Jenkins.
    public void manualTestMultipleChangeTrackerScenario3() throws Exception {
        Log.d(Log.TAG, "testMultipleChangeTrackerScenario3");

        Replication pullReplication = null;
        // create mock server
        MockWebServer server = new MockWebServer();
        MockDispatcher dispatcher = new MockDispatcher();
        try {
            dispatcher.setServerType(MockDispatcher.ServerType.SYNC_GW);
            server.setDispatcher(dispatcher);
            server.start();

            // checkpoint PUT or GET response (sticky)
            MockCheckpointPut mockCheckpointPut = new MockCheckpointPut();
            mockCheckpointPut.setSticky(true);
            mockCheckpointPut.setDelayMs(10 * 1000); // 10sec
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHECKPOINT, mockCheckpointPut);

            // long poll changes feed no response balock 5 min
            MockChangesFeedNoResponse mockChangesFeedNoResponse = new MockChangesFeedNoResponse();
            mockChangesFeedNoResponse.setSticky(true);
            mockChangesFeedNoResponse.setDelayMs(300 * 1000); // 60 sec
            dispatcher.enqueueResponse(MockHelper.PATH_REGEX_CHANGES, mockChangesFeedNoResponse);

            // create and start replication
            pullReplication = database.createPullReplication(server.url("/db").url());
            pullReplication.setContinuous(true);
            pullReplication.start();
            Log.d(Log.TAG, "Started pullReplication: %s", pullReplication);

            // offline
            putReplicationOffline(pullReplication);

            // online
            putReplicationOnline(pullReplication);

            // wait
            try {
                Thread.sleep(30 * 1000);
            } catch (Exception e) {
            }

            // all threads which are associated with replicators should be terminated.
            int numChangeTracker = 0;
            Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            for (Thread t : threadSet) {
                if (t.isAlive()) {
                    if (t.getName().indexOf("ChangeTracker-") != -1) {
                        numChangeTracker++;
                    }
                }
            }
            assertEquals(1, numChangeTracker);

        } finally {
            stopReplication(pullReplication);
            assertTrue(MockHelper.shutdown(server, dispatcher));
        }
    }

    private void putReplicationOffline(Replication replication) throws InterruptedException {
        Log.d(Log.TAG, "putReplicationOffline: %s", replication);

        // this was a useless test, the replication wasn't even started
        final CountDownLatch wentOffline = new CountDownLatch(1);
        Replication.ChangeListener changeListener = new ReplicationOfflineObserver(wentOffline);
        replication.addChangeListener(changeListener);

        replication.goOffline();
        boolean succeeded = wentOffline.await(30, TimeUnit.SECONDS);
        assertTrue(succeeded);

        replication.removeChangeListener(changeListener);

        Log.d(Log.TAG, "/putReplicationOffline: %s", replication);
    }

    private void putReplicationOnline(Replication replication) throws InterruptedException {
        Log.d(Log.TAG, "putReplicationOnline: %s", replication);

        // this was a useless test, the replication wasn't even started
        final CountDownLatch wentOnline = new CountDownLatch(1);
        Replication.ChangeListener changeListener = new ReplicationRunningObserver(wentOnline);
        replication.addChangeListener(changeListener);

        replication.goOnline();

        boolean succeeded = wentOnline.await(30, TimeUnit.SECONDS);
        assertTrue(succeeded);

        replication.removeChangeListener(changeListener);

        Log.d(Log.TAG, "/putReplicationOnline: %s", replication);
    }
}