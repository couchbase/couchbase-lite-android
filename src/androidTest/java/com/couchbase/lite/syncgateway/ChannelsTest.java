package com.couchbase.lite.syncgateway;

import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.auth.AuthenticatorFactory;
import com.couchbase.lite.replicator.Replication;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

//
// Run Sync Gateway with basic-sync-function.json
//
public class ChannelsTest  extends LiteTestCaseWithDB {
    public static final String TAG = "ChannelsTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (!syncgatewayTestsEnabled()) {
            return;
        }
    }

    //
    public void testRestartPullReplWithChannel() throws Exception {
        if (!syncgatewayTestsEnabled()) {
            return;
        }

        Database pushDB = manager.getDatabase("pushdb");
        Database pullDB = manager.getDatabase("pulldb");

        Document doc1 = pushDB.getDocument("doc1");
        Map<String, Object> props1 = new HashMap<String, Object>();
        props1.put("channels", "channel-pull");
        props1.put("value", "1");
        doc1.putProperties(props1);

        Document doc2 = pushDB.getDocument("doc2");
        Map<String, Object> props2 = new HashMap<String, Object>();
        props2.put("channels", "other");
        props2.put("value", "2");
        doc2.putProperties(props2);

        // sync_gateway URL
        URL remote = getReplicationURL();

        // Create push Replication and execute
        Replication pushRepl = pushDB.createPushReplication(remote);
        runReplication(pushRepl);

        // Create pull Replication and execute it.
        Replication pullRepl = pullDB.createPullReplication(remote);
        pullRepl.setChannels(Arrays.asList("channel-pull"));
        runReplication(pullRepl);

        assertEquals(2, pushDB.getDocumentCount());
        assertEquals(1, pullDB.getDocumentCount());

        Document doc3 = pushDB.getDocument("doc3");
        Map<String, Object> props3 = new HashMap<String, Object>();
        props3.put("channels", "channel-pull");
        props3.put("value", "3");
        doc3.putProperties(props1);

        Document doc4 = pushDB.getDocument("doc4");
        Map<String, Object> props4 = new HashMap<String, Object>();
        props4.put("channels", "other");
        props4.put("value", "4");
        doc4.putProperties(props2);

        // Create push Replication and execute
        runReplication(pushRepl);

        // Create pull Replication and execute it.
        runReplication(pullRepl);

        assertEquals(4, pushDB.getDocumentCount());
        assertEquals(2, pullDB.getDocumentCount());
        assertNotNull(pullDB.getExistingDocument("doc1"));
        assertNull(pullDB.getExistingDocument("doc2"));
        assertNotNull(pullDB.getExistingDocument("doc3"));
        assertNull(pullDB.getExistingDocument("doc4"));
    }
}
