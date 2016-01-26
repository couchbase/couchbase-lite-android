package com.couchbase.lite.syncgateway;

import com.couchbase.lite.Attachment;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseOptions;
import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.replicator.Replication;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Created by hideki on 5/7/15.
 */
public class GzippedAttachmentTest extends LiteTestCaseWithDB {

    public static final String TAG = "GzippedAttachmentTest";

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

        Database pushDB = manager.getDatabase("pushdb");
        Database pullDB = manager.getDatabase("pulldb");

        String attachmentName = "attachment.png";

        // 1. store attachment with doc
        // 1.a load attachment data from asset
        InputStream attachmentStream = getAsset(attachmentName);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        IOUtils.copy(attachmentStream, baos);
        baos.close();
        attachmentStream.close();
        byte[] bytes = baos.toByteArray();

        // 1.b apply GZIP + Base64
        String attachmentBase64 = Base64.encodeBytes(bytes, Base64.GZIP);

        // 1.c attachment Map object
        Map<String, Object> attachmentMap = new HashMap<String, Object>();
        attachmentMap.put("content_type", "image/png");
        attachmentMap.put("data", attachmentBase64);
        attachmentMap.put("encoding", "gzip");
        attachmentMap.put("length", bytes.length);

        // 1.d attachments Map object
        Map<String, Object> attachmentsMap = new HashMap<String, Object>();
        attachmentsMap.put(attachmentName, attachmentMap);

        // 1.e document property Map object
        Map<String, Object> propsMap = new HashMap<String, Object>();
        propsMap.put("_attachments", attachmentsMap);

        // 1.f store document into database
        Document putDoc = pushDB.createDocument();
        putDoc.putProperties(propsMap);
        String docId = putDoc.getId();

        URL remote = getReplicationURL();

        // push
        final CountDownLatch latch1 = new CountDownLatch(1);
        Replication pusher = pushDB.createPushReplication(remote);
        pusher.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Log.e(TAG, "push 1:" + event.toString());
                if (event.getCompletedChangeCount() > 0) {
                    latch1.countDown();
                }
            }
        });
        runReplication(pusher);
        assertTrue(latch1.await(30, TimeUnit.SECONDS));

        // pull
        Replication puller = pullDB.createPullReplication(remote);
        final CountDownLatch latch2 = new CountDownLatch(1);
        puller.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Log.e(TAG, "pull 1:" + event.toString());
                if (event.getCompletedChangeCount() > 0) {
                    latch2.countDown();
                }
            }
        });
        runReplication(puller);
        assertTrue(latch2.await(30, TimeUnit.SECONDS));

        Log.e(TAG, "Fetching doc1 via id: " + docId);
        Document pullDoc = pullDB.getDocument(docId);
        assertNotNull(pullDoc);
        assertTrue(pullDoc.getCurrentRevisionId().startsWith("1-"));
        Attachment attachment = pullDoc.getCurrentRevision().getAttachment(attachmentName);

        assertEquals(bytes.length, attachment.getLength());
        assertEquals("image/png", attachment.getContentType());
        assertEquals("gzip", attachment.getMetadata().get("encoding"));

        InputStream is = attachment.getContent();
        byte[] receivedBytes = getBytesFromInputStream(is);
        assertEquals(bytes.length, receivedBytes.length);
        is.close();

        assertTrue(Arrays.equals(bytes, receivedBytes));

        pushDB.close();
        pullDB.close();

        pushDB.delete();
        pullDB.delete();
    }

    private static byte[] getBytesFromInputStream(InputStream is) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024 * 8];
        int len = 0;
        try {
            while ((len = is.read(buffer)) > 0) {
                os.write(buffer, 0, len);
            }
            os.flush();
        } catch (IOException e) {
            Log.e(Log.TAG, "is.read(buffer) or os.flush() error", e);
            return null;
        }
        return os.toByteArray();
    }

    public void testImageAttachmentReplication() throws Exception {
        if (!syncgatewayTestsEnabled()) {
            return;
        }

        URL remote = getReplicationURL();

        Database pushDB = getDatabase("pushdb");
        pushDB.delete();
        pushDB = getDatabase("pushdb");

        Database pullDB = getDatabase("pulldb");
        pullDB.delete();
        pullDB = getDatabase("pulldb");

        // Create a document with an image attached:
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("foo", "bar");

        Document doc = pushDB.createDocument();
        doc.putProperties(props);
        UnsavedRevision newRev = doc.createRevision();
        newRev.setAttachment("attachment", "image/png", getAsset("attachment.png"));
        newRev.save();
        String docId = doc.getId();

        // Push:
        final CountDownLatch latch1 = new CountDownLatch(1);
        Replication pusher = pushDB.createPushReplication(remote);
        pusher.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Log.e(TAG, "push 1:" + event.toString());
                if (event.getCompletedChangeCount() > 0) {
                    latch1.countDown();
                }
            }
        });
        runReplication(pusher);
        assertTrue(latch1.await(5, TimeUnit.SECONDS));

        // Pull:
        Replication puller = pullDB.createPullReplication(remote);
        final CountDownLatch latch2 = new CountDownLatch(1);
        puller.addChangeListener(new Replication.ChangeListener() {
            @Override
            public void changed(Replication.ChangeEvent event) {
                Log.e(TAG, "pull 1:" + event.toString());
                if (event.getCompletedChangeCount() > 0) {
                    latch2.countDown();
                }
            }
        });
        runReplication(puller);
        assertTrue(latch2.await(5, TimeUnit.SECONDS));

        // Check document:
        Document pullDoc = pullDB.getDocument(docId);
        assertNotNull(pullDoc);
        assertTrue(pullDoc.getCurrentRevisionId().startsWith("2-"));

        // Check attachment:
        Attachment attachment = pullDoc.getCurrentRevision().getAttachment("attachment");
        byte[] originalBytes = getBytesFromInputStream(getAsset("attachment.png"));
        assertEquals(originalBytes.length, attachment.getLength());
        assertEquals("image/png", attachment.getContentType());
        assertTrue(Arrays.equals(originalBytes, getBytesFromInputStream(attachment.getContent())));

        pushDB.close();
        pullDB.close();

        pushDB.delete();
        pullDB.delete();
    }

    private Database getDatabase(String name) throws CouchbaseLiteException {
        DatabaseOptions options = new DatabaseOptions();
        options.setCreate(true);
        if (isEncryptionTestEnabled())
            options.setEncryptionKey("seekrit");
        return manager.openDatabase(name, options);
    }
}
