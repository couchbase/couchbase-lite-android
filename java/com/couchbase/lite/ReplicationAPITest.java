package com.couchbase.lite;

import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.util.Log;

import org.apache.http.client.HttpResponseException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by andrey on 12/12/13.
 */
public class ReplicationAPITest extends LiteTestCase {

//    static Database  startDatabase(void) {
//        CBLManager* mgr = [CBLManager createEmptyAtTemporaryPath: @"CBL_ReplicatorTests"];
//        NSError* error;
//        Database  db = [mgr databaseNamed: @"db" error: &error];
//        assertTrue(db);
//        return db;
//    }


    static void runReplication(Replication repl) throws InterruptedException{
        Log.i(TAG, "Waiting for " + repl + " to finish...");
        boolean started = false, done = false;
        repl.start();;
        long lastTime = System.currentTimeMillis();;
        while (!done) {
            if (repl.isRunning()) {
                started = true;
            }
            if (started && (!repl.isContinuous() || !repl.isRunning())){
                done = true;
            }


            // Replication runs on a background thread, so the main runloop should not be blocked.
            // Make sure it's spinning in a timely manner:
            //TODO getMode() always throws UnsupportedOperationException (see ios test)
            long now = System.currentTimeMillis();
            if (lastTime > 0 && now-lastTime > 25)
                Log.w(TAG,"Runloop was blocked for " + (now-lastTime)*100 + " sec");
            lastTime = now;
            Thread.sleep(100);
            break;

        }
        if(repl.getLastError()==null) {
            Log.i(TAG, String.format("...replicator finished. progress %d/%d without error", repl.getCompletedChangesCount(), repl.getChangesCount()));
        } else{
            Log.i(TAG, String.format("...replicator finished. progress %d/%d, error=%s", repl.getCompletedChangesCount(), repl.getChangesCount(), repl.getLastError().toString()));
        }
    }


    public void failingCreateReplicators() throws MalformedURLException {
        URL fakeRemoteURL = new URL("http://fake.fake/fakedb");
        Database  db = startDatabase();

        // Create a replication:
        assertEquals(new ArrayList<Replication>(), db.getAllReplications());
        Replication r1 = db.getPushReplication(fakeRemoteURL);
        assertNotNull(r1);
        List<Replication> repls = new ArrayList<Replication>();
        repls.add(r1);
        assertEquals(repls, db.getAllReplications());
        assertEquals(r1, db.getPushReplication(fakeRemoteURL));   // 2nd call returns same replicator instance

        // Check the replication's properties:
        assertEquals(db, r1.getLocalDatabase());
        assertEquals(fakeRemoteURL, r1.getRemoteUrl());
        assertTrue(!r1.isPull());
        //assertTrue(!r1.ispersistent);TODO do we have the equivalent?
        assertTrue(r1.isContinuous());//r1.isContinuous() is False on iOS
        assertTrue(!r1.shouldCreateTarget());
        assertNull(r1.getFilter());
        assertNull(r1.getFilterParams());
        assertNull(r1.getDocsIds());
        assertNull(r1.getHeaders());

        // Check that the replication hasn't started running:
        assertTrue(!r1.isRunning());
        assertEquals(Replication.ReplicationMode.REPLICATION_STOPPED, r1.getMode()); // TODO UnsupportedOperationException
        assertEquals(0 ,r1.getCompletedChangesCount());
        assertEquals(0, r1.getChangesCount());
        assertNull(r1.getLastError());

        // Create another replication:
        Replication r2 = db.getPullReplication(fakeRemoteURL);
        assertNotNull(r2);
        assertTrue(r2 != r1);
        repls.add(r2);
        assertEquals(db.getAllReplications(), repls);
        assertEquals(r2, db.getPullReplication(fakeRemoteURL));

        // Check the replication's properties:
        assertEquals(db, r2.getLocalDatabase());
        assertEquals(fakeRemoteURL, r2.getRemoteUrl());
        assertTrue(r2.isPull());

        Replication r3 =database.getReplicator(fakeRemoteURL, true, false, manager.getWorkExecutor());
        assertTrue(r3 != r2);
        List<String> documentIDs = new ArrayList<String>();
        documentIDs.add("doc1");
        documentIDs.add("doc2");
        r3.setDocIds(documentIDs);
        Replication repl = db.getManager().replicationWithDatabase(db, fakeRemoteURL, true, false, true);
        assertEquals(repl.getDocsIds(), r3.getDocsIds());
    }

/* //TODO RemoteTestDBURL should be added based on iOS code
    public void testRunPushReplication() {
        NSURL* remoteDbURL = RemoteTestDBURL(kPushThenPullDBName);
        if (!remoteDbURL) {
            Warn(@"Skipping test RunPushReplication (no remote test DB URL)");
            return;
        }
        DeleteRemoteDB(remoteDbURL);

        Log(@"Creating %d documents...", kNDocuments);
        Database  db = startDatabase();
        [db inTransaction:^BOOL{
            for (int i = 1; i <= kNDocuments; i++) {
                @autoreleasepool {
                    CBLDocument* doc = db[ $sprintf(@"doc-%d", i) ];
                    NSError* error;
                    [doc putProperties: @{@"index": @(i), @"bar": $false} error: &error];
                    AssertNil(error);
                }
            }
            return YES;
        }];

        Log(@"Pushing...");
        CBLReplication* repl = [db replicationToURL: remoteDbURL];
        repl.createTarget = YES;
        runReplication(repl);
        AssertNil(repl.lastError);
    }


    public void testRunPullReplication() {
        NSURL* remoteDbURL = RemoteTestDBURL(kPushThenPullDBName);
        if (!remoteDbURL) {
            Warn(@"Skipping test RunPullReplication (no remote test DB URL)");
            return;
        }
        Database  db = startDatabase();

        Log(@"Pulling...");
        CBLReplication* repl = [db replicationFromURL: remoteDbURL];
        runReplication(repl);
        AssertNil(repl.lastError);

        Log(@"Verifying documents...");
        for (int i = 1; i <= kNDocuments; i++) {
            CBLDocument* doc = db[ $sprintf(@"doc-%d", i) ];
            AssertEqual(doc[@"index"], @(i));
            AssertEqual(doc[@"bar"], $false);
        }
    }
    */

    public void failingRunReplicationWithError() throws Exception {
        Database  db = startDatabase();
        URL fakeRemoteURL = new URL("http://couchbase.com/no_such_db");

        // Create a replication:
        Replication r1 = db.getPullReplication(fakeRemoteURL);
        runReplication(r1);

        // It should have failed with a 404:
        r1.start();
        assertEquals(Replication.ReplicationMode.REPLICATION_STOPPED, r1.getMode()); //UnsupportedOperationException
        assertEquals(0, r1.getCompletedChangesCount());
        assertEquals(0, r1.getChangesCount());
        assertEquals(r1.getLastError(), "domain.CBLHTTPErrorDomain");
        HttpResponseException err = (HttpResponseException) r1.getLastError();
        assertEquals(404, err.getStatusCode()); // don't support code error like : r1.getLastError().code

    }

    public void failingReplicationChannelsProperty() throws MalformedURLException {
        Database  db = startDatabase();
        URL fakeRemoteURL = new URL("http://couchbase.com/no_such_db");
        Replication r1 = db.getPullReplication(fakeRemoteURL);

        assertNull(r1.getChannels());//TODO UnsupportedOperationException
        r1.setFilter("foo/bar");
        assertNull(r1.getChannels());
        Map<String, Object> filterParams= new HashMap<String, Object>();
        filterParams.put("a", "b");
        r1.setFilterParams(filterParams);
        assertNull(r1.getChannels());

        r1.setChannels(null);
        assertEquals("foo/bar", r1.getFilter());
        assertEquals(filterParams, r1.getFilterParams());


        List<String> channels = new ArrayList<String>();
        channels.add("NBC");
        channels.add("MTV");
        r1.setChannels(channels);
        assertEquals(channels, r1.getChannels());
        assertEquals("sync_gateway/bychannel", r1.getFilter());
        filterParams= new HashMap<String, Object>();
        filterParams.put("channels", "NBC,MTV");
        assertEquals(filterParams, r1.getFilterParams());

        r1.setChannels(null);
        assertEquals(r1.getFilter(), null);
        assertEquals(null ,r1.getFilterParams());

    }
}
