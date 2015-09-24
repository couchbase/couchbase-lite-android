package com.couchbase.lite.syncgateway;

import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.QueryOptions;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.auth.AuthenticatorFactory;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.util.Log;

import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Steps to run this test
 * 1. Run sync gateway with assets/configs/backfilltest_config.json
 * 2. In assets/test.properties
 * set "true" for syncgatewayTestsEnabled
 * set "backfill" for replicationDatabase
 * set "Sync Gateway's IP address" for replicationServer
 */
public class BackFillTest extends LiteTestCaseWithDB {
    public static final String TAG = "BackFillTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (!syncgatewayTestsEnabled()) {
            return;
        }
    }

    public void testPullReplWithRevsAndAtts() throws Exception {
        if (!syncgatewayTestsEnabled()) {
            return;
        }

        // create push & pull two separate databases
        Database pushDB = manager.getDatabase("pushdb");
        Database pullDB = manager.getDatabase("pulldb");

        // sync_gateway URL
        URL remote = getReplicationURL();

        // Create pull Replication and execute it.
        Replication pullRepl = pullDB.createPullReplication(remote);
        pullRepl.setAuthenticator(AuthenticatorFactory.createBasicAuthenticator("Hideki", "passw0rd"));
        runReplication(pullRepl);
        // nothing received, doc count and sequence number should be 0
        assertEquals(0, pullDB.getDocumentCount());
        assertEquals(0, pullDB.getLastSequenceNumber());

        // Create push Replication and execute
        Replication pushRepl = pushDB.createPushReplication(remote);
        pushRepl.setAuthenticator(AuthenticatorFactory.createBasicAuthenticator("Hideki", "passw0rd"));
        runReplication(pushRepl);
        // doc count and sequence number should be 0
        assertEquals(0, pushDB.getDocumentCount());
        assertEquals(0, pushDB.getLastSequenceNumber());

        // create 3 documents for NBC channel  and replicate them.
        String doc1Id = "doc1";
        Document doc1 = pushDB.getDocument(doc1Id);
        Map<String, Object> props1 = new HashMap<String, Object>();
        props1.put("key", "doc1");
        props1.put("channels", Arrays.asList("NBC"));
        doc1.putProperties(props1);
        runReplication(pushRepl);

        String doc2Id = "doc2";
        Document doc2 = pushDB.getDocument(doc2Id);
        Map<String, Object> props2 = new HashMap<String, Object>();
        props2.put("key", "doc2");
        props2.put("channels", Arrays.asList("NBC"));
        doc2.putProperties(props2);
        runReplication(pushRepl);

        String doc3Id = "doc3";
        Document doc3 = pushDB.getDocument(doc3Id);
        Map<String, Object> props3 = new HashMap<String, Object>();
        props3.put("key", "doc3");
        props3.put("channels", Arrays.asList("NBC"));
        doc3.putProperties(props3);
        runReplication(pushRepl);

        // doc count and sequence number should be 3
        assertEquals(3, pushDB.getDocumentCount());
        assertEquals(3, pushDB.getLastSequenceNumber());

        // execute pull replication, doc count and sequence number still be 0
        runReplication(pullRepl);
        assertEquals(0, pullDB.getDocumentCount());
        assertEquals(0, pullDB.getLastSequenceNumber());

        // Create accessGrant document and push
        Document grantDoc = pushDB.createDocument();
        Map<String, Object> propsGrant = new HashMap<String, Object>();
        propsGrant.put("type", "accessGrant");
        propsGrant.put("accessChannel", "NBC");
        propsGrant.put("user", "Hideki");
        grantDoc.putProperties(propsGrant);
        runReplication(pushRepl);


        // make sure push DB's sequence number should be 4 because added 4 document
        assertEquals(4, pushDB.getDocumentCount());
        assertEquals(4, pushDB.getLastSequenceNumber());

        // make sure pull db's doc count and sequence is still 0.
        assertEquals(0, pullDB.getDocumentCount());
        assertEquals(0, pullDB.getLastSequenceNumber());

        // make sure pull db's sequence number is higher than sync_gateway's sequence number and push db's sequence number
        for (int i = 0; i < 10; i++) {
            Document doc = pullDB.createDocument();
            Map<String, Object> props = new HashMap<String, Object>();
            props.put("value", i);
            doc.putProperties(props);
        }

        // make sure pull db's sequence is still 10 after adding 10 docs.
        assertEquals(10, pullDB.getDocumentCount());
        assertEquals(10, pullDB.getLastSequenceNumber());

        // do pull replication
        runReplication(pullRepl);

        // total doc should be 13 (10 + 3)
        assertEquals(13, pullDB.getDocumentCount());
        assertEquals(13, pullDB.getLastSequenceNumber());

        Document pullDoc1 = pullDB.getDocument(doc1Id);
        Document pullDoc2 = pullDB.getDocument(doc2Id);
        Document pullDoc3 = pullDB.getDocument(doc3Id);
        assertNotNull(pullDoc1);
        assertNotNull(pullDoc2);
        assertNotNull(pullDoc3);
        assertEquals("doc1", pullDoc1.getProperties().get("key"));
        assertEquals("doc2", pullDoc2.getProperties().get("key"));
        assertEquals("doc3", pullDoc3.getProperties().get("key"));

        // following codes are just for debugging purpose
        QueryOptions options = new QueryOptions();
        options.setIncludeDocs(true);
        Map<String, Object> docs = pullDB.getAllDocs(options);
        List<QueryRow> rows = (List<QueryRow>) docs.get("rows");
        for (int i = 0; i < rows.size(); i++) {
            QueryRow row = rows.get(i);
            Log.v(Log.TAG, "docID=" + row.getDocumentId());
            Log.v(Log.TAG, "sequenceNumber=" + row.getSequenceNumber());
            Log.v(Log.TAG, "properties=" + row.getDocument().getProperties().toString());
        }
    }
}
