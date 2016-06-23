//
// Copyright (c) 2016 Couchbase, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.replicator;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.auth.FacebookAuthorizer;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests for the new state machine based replicator
 */
public class ReplicationTest extends LiteTestCaseWithDB {

    /**
     * TestCase(CreateReplicators) in ReplicationAPITests.m
     */
    public void testCreateReplicators() throws Exception {
        URL fakeRemoteURL = new URL("http://fake.fake/fakedb");

        // Create a replication:
        assertEquals(0, database.getAllReplications().size());
        Replication r1 = database.createPushReplication(fakeRemoteURL);
        assertNotNull(r1);

        // Check the replication's properties:
        assertEquals(database, r1.getLocalDatabase());
        assertEquals(fakeRemoteURL, r1.getRemoteUrl());
        assertFalse(r1.isPull());
        assertFalse(r1.isContinuous());
        assertFalse(r1.shouldCreateTarget());
        assertNull(r1.getFilter());
        assertNull(r1.getFilterParams());
        assertNull(r1.getDocIds());
        assertEquals(0, r1.getHeaders().size());

        // Check that the replication hasn't started running:
        assertFalse(r1.isRunning());
        assertEquals(Replication.ReplicationStatus.REPLICATION_STOPPED, r1.getStatus());
        assertEquals(0, r1.getChangesCount());
        assertEquals(0, r1.getCompletedChangesCount());
        assertNull(r1.getLastError());

        // Create another replication:
        Replication r2 = database.createPullReplication(fakeRemoteURL);
        assertNotNull(r2);
        assertTrue(r1 != r2);

        // Check the replication's properties:
        assertEquals(database, r2.getLocalDatabase());
        assertEquals(fakeRemoteURL, r2.getRemoteUrl());
        assertTrue(r2.isPull());

        Replication r3 = database.createPullReplication(fakeRemoteURL);
        assertNotNull(r3);
        assertTrue(r3 != r2);
        r3.setDocIds(Arrays.asList("doc1", "doc2"));

        Replication repl = database.getManager().getReplicator(r3.getProperties());
        assertEquals(r3.getDocIds(), repl.getDocIds());
    }

    /**
     * Start continuous replication with a closed db.
     * <p/>
     * Expected behavior:
     * - Receive replication finished callback
     * - Replication lastError will contain an exception
     */
    public void testStartReplicationClosedDb() throws Exception {
        Database db = this.manager.getDatabase("closed");
        final CountDownLatch countDownLatch = new CountDownLatch(1);
        final Replication replication = db.createPullReplication(new URL("http://fake.com/foo"));
        replication.setContinuous(true);
        replication.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Log.d(TAG, "changed event: %s", event);
                if (replication.isRunning() == false) {
                    countDownLatch.countDown();
                }
            }
        });

        db.close();
        replication.start();

        boolean success = countDownLatch.await(60, TimeUnit.SECONDS);
        assertTrue(success);
        assertTrue(replication.getLastError() != null);
    }

    public void testServerIsSyncGatewayVersion() throws Exception {
        Replication pusher = database.createPushReplication(getReplicationURL());
        assertFalse(pusher.serverIsSyncGatewayVersion("0.01"));
        pusher.setServerType("Couchbase Sync Gateway/0.93");
        assertTrue(pusher.serverIsSyncGatewayVersion("0.92"));
        assertFalse(pusher.serverIsSyncGatewayVersion("0.94"));
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/243
     */
    public void testDifferentCheckpointsFilteredReplication() throws Exception {
        Replication pullerNoFilter = database.createPullReplication(getReplicationURL());
        String noFilterCheckpointDocId = pullerNoFilter.remoteCheckpointDocID();

        Replication pullerWithFilter1 = database.createPullReplication(getReplicationURL());
        pullerWithFilter1.setFilter("foo/bar");
        Map<String, Object> filterParams = new HashMap<String, Object>();
        filterParams.put("a", "aval");
        filterParams.put("b", "bval");
        List<String> docIds = Arrays.asList("doc3", "doc1", "doc2");
        pullerWithFilter1.setDocIds(docIds);
        assertEquals(docIds, pullerWithFilter1.getDocIds());
        pullerWithFilter1.setFilterParams(filterParams);

        String withFilterCheckpointDocId = pullerWithFilter1.remoteCheckpointDocID();
        assertFalse(withFilterCheckpointDocId.equals(noFilterCheckpointDocId));

        Replication pullerWithFilter2 = database.createPullReplication(getReplicationURL());
        pullerWithFilter2.setFilter("foo/bar");
        filterParams = new HashMap<String, Object>();
        filterParams.put("b", "bval");
        filterParams.put("a", "aval");
        pullerWithFilter2.setDocIds(Arrays.asList("doc2", "doc3", "doc1"));
        pullerWithFilter2.setFilterParams(filterParams);

        String withFilterCheckpointDocId2 = pullerWithFilter2.remoteCheckpointDocID();
        assertTrue(withFilterCheckpointDocId.equals(withFilterCheckpointDocId2));
    }

    public void testBuildRelativeURLString() throws Exception {
        String dbUrlString = "http://10.0.0.3:4984/todos/";
        Replication replication = database.createPullReplication(new URL(dbUrlString));
        String relativeUrlString = replication.buildRelativeURLString("foo");
        String expected = "http://10.0.0.3:4984/todos/foo";
        Assert.assertEquals(expected, relativeUrlString);
    }

    public void testBuildRelativeURLStringWithLeadingSlash() throws Exception {
        String dbUrlString = "http://10.0.0.3:4984/todos/";
        Replication replication = database.createPullReplication(new URL(dbUrlString));
        String relativeUrlString = replication.buildRelativeURLString("/foo");
        String expected = "http://10.0.0.3:4984/foo";
        Assert.assertEquals(expected, relativeUrlString);
        relativeUrlString = replication.buildRelativeURLString("foo");
        expected = "http://10.0.0.3:4984/todos/foo";
        Assert.assertEquals(expected, relativeUrlString);
    }

    public void testChannels() throws Exception {
        URL remote = getReplicationURL();
        Replication replicator = database.createPullReplication(remote);
        List<String> channels = new ArrayList<String>();
        channels.add("chan1");
        channels.add("chan2");
        replicator.setChannels(channels);
        Assert.assertEquals(channels, replicator.getChannels());
        replicator.setChannels(null);
        Assert.assertTrue(replicator.getChannels().isEmpty());
    }

    public void testChannelsMore() throws MalformedURLException, CouchbaseLiteException {
        Database db = startDatabase();
        URL fakeRemoteURL = new URL("http://couchbase.com/no_such_db");
        Replication r1 = db.createPullReplication(fakeRemoteURL);

        assertTrue(r1.getChannels().isEmpty());
        r1.setFilter("foo/bar");
        assertTrue(r1.getChannels().isEmpty());
        Map<String, Object> filterParams = new HashMap<String, Object>();
        filterParams.put("a", "b");
        r1.setFilterParams(filterParams);
        assertTrue(r1.getChannels().isEmpty());

        r1.setChannels(null);
        assertEquals("foo/bar", r1.getFilter());
        assertEquals(filterParams, r1.getFilterParams());

        List<String> channels = new ArrayList<String>();
        channels.add("NBC");
        channels.add("MTV");
        r1.setChannels(channels);
        assertEquals(channels, r1.getChannels());
        assertEquals("sync_gateway/bychannel", r1.getFilter());
        filterParams = new HashMap<String, Object>();
        filterParams.put("channels", "NBC,MTV");
        assertEquals(filterParams, r1.getFilterParams());

        r1.setChannels(null);
        assertEquals(r1.getFilter(), null);
        assertEquals(null, r1.getFilterParams());
    }

    public void testGetReplicatorWithAuth() throws Throwable {
        Map<String, Object> authProperties = getReplicationAuthParsedJson();

        Map<String, Object> targetProperties = new HashMap<String, Object>();
        targetProperties.put("url", getReplicationURL().toExternalForm());
        targetProperties.put("auth", authProperties);

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("source", DEFAULT_TEST_DB);
        properties.put("target", targetProperties);

        Replication replicator = manager.getReplicator(properties);
        assertNotNull(replicator);
        assertNotNull(replicator.getAuthenticator());
        assertTrue(replicator.getAuthenticator() instanceof FacebookAuthorizer);
    }

    // ReplicatorInternal.m: test_UseRemoteUUID
    public void testUseRemoteUUID() throws Exception {
        URL remoteURL1 = new URL("http://alice.local:55555/db");
        Replication r1 = database.createPullReplication(remoteURL1);
        r1.setRemoteUUID("cafebabe");
        String check1 = r1.replicationInternal.remoteCheckpointDocID();

        // Different URL, but same remoteUUID:
        URL remoteURL2 = new URL("http://alice17.local:44444/db");
        Replication r2 = database.createPullReplication(remoteURL2);
        r2.setRemoteUUID("cafebabe");
        String check2 = r2.replicationInternal.remoteCheckpointDocID();
        assertEquals(check1, check2);

        // Same UUID but different filter settings:
        Replication r3 = database.createPullReplication(remoteURL2);
        r3.setRemoteUUID("cafebabe");
        r3.setFilter("Melitta");
        String check3 = r3.replicationInternal.remoteCheckpointDocID();
        assertNotSame(check2, check3);
    }
}