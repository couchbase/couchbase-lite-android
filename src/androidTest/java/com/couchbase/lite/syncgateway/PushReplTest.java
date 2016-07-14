/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite.syncgateway;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.Manager;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

/**
 * Created by hideki on 2/25/16.
 */
public class PushReplTest extends LiteTestCaseWithDB {

    public static final String TAG = "PushReplTest";

    @Override
    protected void setUp() throws Exception {
        if (!syncgatewayTestsEnabled()) {
            return;
        }

        super.setUp();
    }

    public void testPushSingleDocument() throws Exception {
        if (!syncgatewayTestsEnabled()) {
            return;
        }

        // Single document without attachment
        _testPushSingleDocument(false, -1); // None
        // Single document with small attachment
        _testPushSingleDocument(true, 10); // 10 B
        // Single document with large attachment
        _testPushSingleDocument(true, 1024 * 10); // 10KB
        // Single document with larger attachment
        _testPushSingleDocument(true, 1024 * 100); // 100KB
    }

    public void _testPushSingleDocument(boolean attachment, int attachmentSize) throws Exception{
        if (!syncgatewayTestsEnabled()) {
            return;
        }

        // create document
        Document doc = database.createDocument();
        Map<String, Object> props = new HashMap<>();
        props.put("key", "Hello World!");
        doc.putProperties(props);
        String docID = doc.getId();


        // add attachment
        if(attachment) {
            char[] chars = new char[attachmentSize];
            Arrays.fill(chars, 'a');
            final String content = new String(chars);
            ByteArrayInputStream body = new ByteArrayInputStream(content.getBytes("UTF-8"));
            try {
                UnsavedRevision newRev = doc.createRevision();
                newRev.setAttachment("attachment1", "text/plain; charset=utf-8", body);
                newRev.save();
            } finally {
                body.close();
            }
        }


        // start push replicator
        pushData(getReplicationURL());

        Map<String, Object> data =  getDocByID(docID);
        assertEquals("Hello World!", data.get("key"));

        // check attachment
        if(attachment){
            Map<String, Object> attachments =  (Map<String, Object>) getDocByID(docID).get("_attachments");
            assertTrue(attachments.containsKey("attachment1"));
        }
    }


    /**
     * Note: For test, needs to restart sync gateway. Default sync gateway does not allow
     * to access admin port from non-local to delete db.
     */
    public void testPushRepl() throws Exception {
        if (!syncgatewayTestsEnabled()) {
            return;
        }

        final int INIT_DOCS = 100;
        final int TOTAL_DOCS = 400;

        // initial 100 docs
        for (int i = 0; i < INIT_DOCS; i++) {
            Document doc = database.getDocument("doc-" + String.format(Locale.ENGLISH, "%03d", i));
            Map<String, Object> props = new HashMap<>();
            props.put("key", i);
            try {
                doc.putProperties(props);
            } catch (CouchbaseLiteException e) {
                Log.e(TAG, "Failed to create new doc", e);
                fail(e.getMessage());
            }
        }

        final CountDownLatch latch = new CountDownLatch(1);

        // thread creates documents during replication.
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                for (int i = INIT_DOCS; i < TOTAL_DOCS; i++) {
                    Document doc = database.getDocument("doc-" + String.format(Locale.ENGLISH, "%03d", i));
                    Map<String, Object> props = new HashMap<>();
                    props.put("key", i);
                    try {
                        doc.putProperties(props);
                    } catch (CouchbaseLiteException e) {
                        Log.e(TAG, "Failed to create new doc", e);
                        fail(e.getMessage());
                    }
                }
                latch.countDown();
            }
        });
        thread.start();
        pushData(getReplicationURL());
        // wait till thread finishes to create all docs.
        latch.await();

        // verify total document number on remote
        assertEquals(TOTAL_DOCS, ((Number) getAllDocs().get("total_rows")).intValue());
        String docID = "doc-" + String.format(Locale.ENGLISH, "%03d", 100);
        Document doc = database.getDocument(docID);
        //Add Attachment 1
        StringBuffer sb = new StringBuffer();
        int size = 50 * 1024;
        for (int i = 0; i < size; i++) {
            sb.append("a");
        }

        ByteArrayInputStream body = new ByteArrayInputStream(sb.toString().getBytes());
        UnsavedRevision newRev = doc.createRevision();
        newRev.setAttachment("attachment1", "text/plain; charset=utf-8", body);
        newRev.save();

        // start push replicator
        pushData(getReplicationURL());

        Map<String, Object> attachments =  (Map<String, Object>) getDocByID(docID).get("_attachments");
        assertTrue(attachments.containsKey("attachment1"));

        //Add Attachment 2
        StringBuffer sb2 = new StringBuffer();
        int size2 = 50 * 1024;
        for (int i = 0; i < size2; i++) {
            sb.append("b");
        }
        ByteArrayInputStream body2 = new ByteArrayInputStream(sb.toString().getBytes());
        UnsavedRevision newRev2 = doc.createRevision();
        newRev2.setAttachment("attachment2", "text/plain; charset=utf-8", body2);
        newRev2.save();

        pushData(getReplicationURL());

        // verify total document number on remote
        Map<String, Object> attachmentsV2 =  (Map<String, Object>) getDocByID(docID).get("_attachments");
        assertTrue(attachmentsV2.containsKey("attachment2"));
    }

    void pushData(URL remote) throws Exception {
        // start push replicator
        final CountDownLatch idle = new CountDownLatch(1);
        final CountDownLatch stop = new CountDownLatch(1);
        ReplicationIdleObserver idleObserver = new ReplicationIdleObserver(idle);
        ReplicationFinishedObserver stopObserver = new ReplicationFinishedObserver(stop);
        Replication push = database.createPushReplication(remote);
        push.setContinuous(true);
        push.addChangeListener(idleObserver);
        push.addChangeListener(stopObserver);
        push.start();
        // wait till push become idle state
        idle.await();
        // stop push
        push.stop();
        // stop till push become stopped state
        stop.await();

        // give sync gateway to process all.
        Thread.sleep(2*1000);
    }

    // GET /{db}/_all_docs
    Map<String, Object> getAllDocs() throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(System.getProperty("replicationUrl") + "/_all_docs");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            InputStream in = connection.getInputStream();
            try {
                return Manager.getObjectMapper().readValue(in, Map.class);
            } finally {
                if (in != null)
                    in.close();
            }
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }

    Map<String, Object> getDocByID(String docId) throws IOException {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(System.getProperty("replicationUrl") + "/" + docId);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            InputStream in = connection.getInputStream();
            try {
                return Manager.getObjectMapper().readValue(in, Map.class);
            } finally {
                if (in != null)
                    in.close();
            }
        } finally {
            if (connection != null)
                connection.disconnect();
        }
    }
}
