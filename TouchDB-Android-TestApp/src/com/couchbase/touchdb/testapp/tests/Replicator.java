package com.couchbase.touchdb.testapp.tests;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.commons.io.IOUtils;

import junit.framework.Assert;
import android.content.Intent;
import android.util.Log;

import com.couchbase.touchdb.TDBlobStore;
import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDStatus;
import com.couchbase.touchdb.replicator.TDPusher;
import com.couchbase.touchdb.replicator.TDReplicator;
import com.couchbase.touchdb.testapp.CopySampleAttachmentsActivity;

public class Replicator extends TouchDBTestCase {

    public static final String TAG = "Replicator";

    public void testPusher() throws Throwable {

        URL remote = getReplicationURL();

        deleteRemoteDB(remote);

        // Create some documents:
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("_id", "doc1");
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);

        TDBody body = new TDBody(documentProperties);
        TDRevision rev1 = new TDRevision(body);

        TDStatus status = new TDStatus();
        rev1 = database.putRevision(rev1, null, false, status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        documentProperties.put("_rev", rev1.getRevId());
        documentProperties.put("UPDATED", true);

        @SuppressWarnings("unused")
        TDRevision rev2 = database.putRevision(new TDRevision(documentProperties), rev1.getRevId(), false, status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        documentProperties = new HashMap<String, Object>();
        documentProperties.put("_id", "doc2");
        documentProperties.put("baz", 666);
        documentProperties.put("fnord", true);

        database.putRevision(new TDRevision(documentProperties), null, false, status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        final TDReplicator repl = database.getReplicator(remote, true, false);
        ((TDPusher)repl).setCreateTarget(true);
        runTestOnUiThread(new Runnable() {

            @Override
            public void run() {
                // Push them to the remote:
                repl.start();
                Assert.assertTrue(repl.isRunning());
            }
        });

        while(repl.isRunning()) {
            Log.i(TAG, "Waiting for replicator to finish");
            Thread.sleep(1000);
        }
        Assert.assertEquals("3", repl.getLastSequence());
    }

    public void testAttachmentPusher() throws Throwable {

        // clean up remote db 
        URL remote = getReplicationURL();
        deleteRemoteDB(remote);

        // Create some documents:
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("_id", "doc1");
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);

        TDBody body = new TDBody(documentProperties);
        TDRevision rev1 = new TDRevision(body);

        TDStatus status = new TDStatus();
        rev1 = database.putRevision(rev1, null, false, status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        documentProperties.put("_rev", rev1.getRevId());
        documentProperties.put("UPDATED", true);

        @SuppressWarnings("unused")
        TDRevision rev2 = database.putRevision(new TDRevision(documentProperties),
                rev1.getRevId(), false, status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        documentProperties = new HashMap<String, Object>();
        documentProperties.put("_id", "doc2");
        documentProperties.put("baz", 666);
        documentProperties.put("fnord", true);

        TDRevision rev3 = database.putRevision(new TDRevision(documentProperties),
                null, false, status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        // Make sure we have no attachments:
        TDBlobStore attachments = database.getAttachments();
        Assert.assertEquals(0, attachments.count());
        Assert.assertEquals(new HashSet<Object>(), attachments.allKeys());

        // Add a text attachment to the documents:
        byte[] htmlAttachment = "<html>And this is an html attachment.</html>"
                .getBytes();
        status = database.insertAttachmentForSequenceWithNameAndType(
                new ByteArrayInputStream(htmlAttachment), rev3.getSequence(),
                "sample_attachment.html", "text/html", rev3.getGeneration());
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        // Add two image attachments to the documents, these attachments are copied
        // by the CopySampleAttachmentsActivity.
        Intent copyImages = new Intent(getInstrumentation().getContext(),
                CopySampleAttachmentsActivity.class);
        copyImages.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getInstrumentation().startActivitySync(copyImages);

        // Attach sample images to doc2
        FileInputStream fileStream = null;
        FileInputStream fileStream2 = null;
        boolean sampleFilesExistAndWereCopied = true;
        try {
            fileStream = new FileInputStream(
                    "/data/data/com.couchbase.touchdb.testapp/files/sample_attachment_image1.jpg");
            byte[] imageAttachment = IOUtils.toByteArray(fileStream);
            if (imageAttachment.length == 0) {
                sampleFilesExistAndWereCopied = false;
            }
            status = database.insertAttachmentForSequenceWithNameAndType(
                    new ByteArrayInputStream(imageAttachment), rev3.getSequence(),
                    "sample_attachment_image1.jpg", "image/jpeg", rev3.getGeneration());
            Assert.assertEquals(TDStatus.CREATED, status.getCode());

            fileStream2 = new FileInputStream(
                    "/data/data/com.couchbase.touchdb.testapp/files/sample_attachment_image2.jpg");
            byte[] secondImageAttachment = IOUtils.toByteArray(fileStream2);
            if (secondImageAttachment.length == 0) {
                sampleFilesExistAndWereCopied = false;
            }
            status = database.insertAttachmentForSequenceWithNameAndType(
                    new ByteArrayInputStream(secondImageAttachment), rev3.getSequence(),
                    "sample_attachment_image2.jpg", "image/jpeg", rev3.getGeneration());
            Assert.assertEquals(TDStatus.CREATED, status.getCode());

        } catch (FileNotFoundException e) {
            sampleFilesExistAndWereCopied = false;
            e.printStackTrace();
        } catch (IOException e) {
            sampleFilesExistAndWereCopied = false;
            e.printStackTrace();
        } finally {
            IOUtils.closeQuietly(fileStream);
            IOUtils.closeQuietly(fileStream2);
        }
        if (!sampleFilesExistAndWereCopied) {
            Log.e(
                    TAG,
                    "The sample image files for testing multipart attachment upload werent copied to the SDCARD, ");
        }
        /*
         * test failed probably because the sample files weren't copied from the
         * res/raw to the sdacard
         */
        Assert.assertTrue(sampleFilesExistAndWereCopied);

        final TDReplicator repl = database.getReplicator(remote, true, false);
        ((TDPusher) repl).setCreateTarget(true);
        runTestOnUiThread(new Runnable() {

            @Override
            public void run() {
                // Push them to the remote:
                repl.start();
                Assert.assertTrue(repl.isRunning());
            }
        });

        while (repl.isRunning()) {
            Log.i(TAG, "Waiting for replicator to finish");
            Thread.sleep(1000);
        }
        Assert.assertEquals("3", repl.getLastSequence());
    }
    
    public void testPuller() throws Throwable {

        //force a push first, to ensure that we have data to pull
        testPusher();

        URL remote = getReplicationURL();

        final TDReplicator repl = database.getReplicator(remote, false, false);
        runTestOnUiThread(new Runnable() {

            @Override
            public void run() {
                // Pull them from the remote:
                repl.start();
                Assert.assertTrue(repl.isRunning());
            }
        });

        while(repl.isRunning()) {
            Log.i(TAG, "Waiting for replicator to finish");
            Thread.sleep(1000);
        }
        String lastSequence = repl.getLastSequence();
        Assert.assertTrue("2".equals(lastSequence) || "3".equals(lastSequence));
        Assert.assertEquals(2, database.getDocumentCount());


        //wait for a short time here
        //we want to ensure that the previous replicator has really finished
        //writing its local state to the server
        Thread.sleep(2*1000);

        final TDReplicator repl2 = database.getReplicator(remote, false, false);
        runTestOnUiThread(new Runnable() {

            @Override
            public void run() {
                // Pull them from the remote:
                repl2.start();
                Assert.assertTrue(repl2.isRunning());
            }
        });

        while(repl2.isRunning()) {
            Log.i(TAG, "Waiting for replicator2 to finish");
            Thread.sleep(1000);
        }
        Assert.assertEquals(3, database.getLastSequence());

        TDRevision doc = database.getDocumentWithIDAndRev("doc1", null, EnumSet.noneOf(TDDatabase.TDContentOptions.class));
        Assert.assertNotNull(doc);
        Assert.assertTrue(doc.getRevId().startsWith("2-"));
        Assert.assertEquals(1, doc.getProperties().get("foo"));

        doc = database.getDocumentWithIDAndRev("doc2", null, EnumSet.noneOf(TDDatabase.TDContentOptions.class));
        Assert.assertNotNull(doc);
        Assert.assertTrue(doc.getRevId().startsWith("1-"));
        Assert.assertEquals(true, doc.getProperties().get("fnord"));

    }

}
