package com.couchbase.lite.syncgateway;

import com.couchbase.lite.Attachment;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCase;
import com.couchbase.lite.support.Base64;
import com.couchbase.lite.util.Log;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by hideki on 5/7/15.
 */
public class Test02_GzippedAttachment extends LiteTestCase {

    public static final String TAG = "Test02_GzippedAttachment";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!syncgatewayTestsEnabled()) {
            return;
        }
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/197
     * Gzipped attachment support with Replicator does not seem to be working
     * <p/>
     * https://github.com/couchbase/couchbase-lite-android/blob/master/src/androidTest/java/com/couchbase/lite/replicator/ReplicationTest.java#L2071
     */
    public void testGzippedAttachment() throws Exception {
        if (!syncgatewayTestsEnabled()) {
            return;
        }

        URL remote = getReplicationURL();

        Database pushDB = manager.getDatabase("pushdb");
        //String docIdTimestamp = Long.toString(System.currentTimeMillis());
        //final String doc1Id = String.format("doc1-%s", docIdTimestamp);
        String attachmentName = "attachment.png";
        Document doc = addDocWithId(pushDB, attachmentName);
        String docId = doc.getId();

        /*
        // push
        final CountDownLatch latch1 = new CountDownLatch(1);
        Replication pusher = pushDB.createPushReplication(remote);
        pusher.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Log.e(TAG, "push 1:" + event.toString());
                if (event.getCompletedChangeCount() == 1) {
                    latch1.countDown();
                }
            }
        });
        runReplication(pusher);
        assertEquals(0, latch1.getCount());

        // pull
        Replication puller = database.createPullReplication(remote);
        final CountDownLatch latch2 = new CountDownLatch(1);
        puller.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Log.e(TAG, "pull 1:" + event.toString());
                if (event.getCompletedChangeCount() == 1) {
                    latch2.countDown();
                }
            }
        });
        runReplication(puller);
        assertEquals(0, latch2.getCount());
        */

        Log.d(TAG, "Fetching doc1 via id: " + docId);
        //Document doc1 = database.getDocument(doc1Id);
        Document doc1 = pushDB.getDocument(docId);
        assertNotNull(doc1);
        assertTrue(doc1.getCurrentRevisionId().startsWith("1-"));
        Attachment attachment = doc1.getCurrentRevision().getAttachment(attachmentName);
        assertTrue(attachment.getLength() > 0);
        //assertTrue(attachment.getGZipped());
        InputStream is = attachment.getContent();
        assertNotNull(is);
        byte[] receivedBytes = getBytesFromInputStream(is);
        assertNotNull(receivedBytes);
        assertTrue(receivedBytes.length > 0);
        is.close();

        InputStream attachmentStream = getAsset(attachmentName);
        byte[] actualBytes = getBytesFromInputStream(attachmentStream);
        assertEquals(actualBytes.length, receivedBytes.length);
        assertTrue(Arrays.equals(actualBytes, receivedBytes));
    }

    private static byte[] getBytesFromInputStream(InputStream is){
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024*8];
        int len = 0;
        try {
            while((len = is.read(buffer)) > 0){
                os.write(buffer, 0, len);
            }
            os.flush();
        } catch (IOException e) {
            Log.e(Log.TAG, "is.read(buffer) or os.flush() error", e);
            return null;
        }
        return os.toByteArray();
    }

    private Document addDocWithId(Database db, String attachmentName) throws IOException, CouchbaseLiteException {
        // add attachment to document
        InputStream attachmentStream = getAsset(attachmentName);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtils.copy(attachmentStream, baos);
        baos.close();
        attachmentStream.close();

        byte[] bytes = baos.toByteArray();
        String attachmentBase64 = Base64.encodeBytes(bytes, Base64.GZIP);
        //String attachmentBase64 = Base64.encodeBytes(bytes);
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);

        Map<String, Object> attachment = new HashMap<String, Object>();
        attachment.put("content_type", "image/png");
        attachment.put("data", attachmentBase64);
        attachment.put("encoding", "gzip");
        attachment.put("length", bytes.length);

        Map<String, Object> attachments = new HashMap<String, Object>();
        attachments.put(attachmentName, attachment);
        documentProperties.put("_attachments", attachments);

        Document doc = db.createDocument();
        doc.putProperties(documentProperties);
        return doc;
    }

}
