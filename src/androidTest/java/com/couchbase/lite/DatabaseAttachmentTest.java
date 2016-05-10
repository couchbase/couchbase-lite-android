/**
 * Original iOS version by  Jens Alfke
 * Ported to Android by Marty Schoch
 * <p/>
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
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

package com.couchbase.lite;

import com.couchbase.lite.internal.AttachmentInternal;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.support.Base64;
import com.couchbase.lite.support.security.SymmetricKeyException;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.TextUtils;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/**
 * Tests ported from DatabaseAttachment_Tests.m
 */
public class DatabaseAttachmentTest extends LiteTestCaseWithDB {

    public static final String TAG = "Attachments";

    /**
     * in DatabaseAttachment_Tests.m
     * - (void) test10_Attachments
     */
    @SuppressWarnings("unchecked")
    public void testAttachments() throws Exception {
        BlobStore attachments = database.getAttachmentStore();
        Assert.assertEquals(0, attachments.count());
        Assert.assertEquals(new HashSet<Object>(), attachments.allKeys());

        // Add a revision and an attachment to it:
        Status status = new Status(Status.OK);
        byte[] attach1 = "This is the body of attach1".getBytes();
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("foo", 1);
        props.put("bar", false);
        props.put("_attachments", getAttachmentsDict(attach1, "attach", "text/plain", false));
        RevisionInternal rev1 = database.putRevision(new RevisionInternal(props), null, false, status);
        Assert.assertEquals(Status.CREATED, status.getCode());

        AttachmentInternal att = database.getAttachment(rev1, "attach");
        Assert.assertNotNull(att);
        Log.i(TAG, new String(att.getContent()));
        Assert.assertTrue(Arrays.equals(attach1, att.getContent()));
        Assert.assertEquals("text/plain", att.getContentType());
        Assert.assertEquals(AttachmentInternal.AttachmentEncoding.AttachmentEncodingNone, att.getEncoding());

        // Check the attachment dict:
        Map<String, Object> itemDict = new HashMap<String, Object>();
        itemDict.put("content_type", "text/plain");
        itemDict.put("digest", "sha1-gOHUOBmIMoDCrMuGyaLWzf1hQTE=");
        itemDict.put("length", 27);
        itemDict.put("stub", true);
        itemDict.put("revpos", 1);
        Map<String, Object> attachmentDict = new HashMap<String, Object>();
        attachmentDict.put("attach", itemDict);
        RevisionInternal gotRev1 = database.getDocument(rev1.getDocID(), rev1.getRevID(), true);
        Map<String, Object> gotAttachmentDict = (Map<String, Object>) gotRev1.getProperties().get("_attachments");
        Assert.assertEquals(attachmentDict, gotAttachmentDict);

        // Check the attachment dict, with attachments included:
        itemDict.remove("stub");
        itemDict.put("data", Base64.encodeBytes(attach1));
        gotRev1 = database.getDocument(rev1.getDocID(), rev1.getRevID(), true);
        RevisionInternal expandedRev = gotRev1.copy();
        Assert.assertTrue(database.expandAttachments(expandedRev, 0, false, true, status));
        Assert.assertEquals(attachmentDict, expandedRev.getAttachments());

        // Add a second revision that doesn't update the attachment:
        props = new HashMap<String, Object>();
        props.put("_id", rev1.getDocID());
        props.put("foo", 2);
        props.put("bazz", false);
        props.put("_attachments", getAttachmentsStub("attach"));
        RevisionInternal rev2 = database.putRevision(new RevisionInternal(props), rev1.getRevID(), false, status);
        Assert.assertEquals(Status.CREATED, status.getCode());

        // Add a third revision of the same document:
        byte[] attach2 = "<html>And this is attach2</html>".getBytes();
        props = new HashMap<String, Object>();
        props.put("_id", rev2.getDocID());
        props.put("foo", 2);
        props.put("bazz", false);
        props.put("_attachments", getAttachmentsDict(attach2, "attach", "text/html", false));
        RevisionInternal rev3 = database.putRevision(new RevisionInternal(props), rev2.getRevID(), false, status);
        Assert.assertEquals(Status.CREATED, status.getCode());

        // Check the 2nd revision's attachment:
        att = database.getAttachment(rev2, "attach");
        Assert.assertNotNull(att);
        Assert.assertEquals("text/plain", att.getContentType());
        Assert.assertEquals(AttachmentInternal.AttachmentEncoding.AttachmentEncodingNone, att.getEncoding());
        Assert.assertTrue(Arrays.equals(attach1, att.getContent()));

        expandedRev = rev2.copy();
        Assert.assertTrue(database.expandAttachments(expandedRev, 2, false, true, status));
        attachmentDict = new HashMap<String, Object>();
        itemDict = new HashMap<String, Object>();
        itemDict.put("stub", true);
        itemDict.put("revpos", 1);
        attachmentDict.put("attach", itemDict);
        Assert.assertEquals(attachmentDict, expandedRev.getAttachments());


        // Check the 3rd revision's attachment:
        att = database.getAttachment(rev3, "attach");
        Assert.assertNotNull(att);
        Assert.assertEquals("text/html", att.getContentType());
        Assert.assertEquals(AttachmentInternal.AttachmentEncoding.AttachmentEncodingNone, att.getEncoding());
        Assert.assertTrue(Arrays.equals(attach2, att.getContent()));

        expandedRev = rev3.copy();
        Assert.assertTrue(database.expandAttachments(expandedRev, 2, false, true, status));
        attachmentDict = new HashMap<String, Object>();
        itemDict = new HashMap<String, Object>();
        itemDict.put("content_type", "text/html");
        itemDict.put("data", "PGh0bWw+QW5kIHRoaXMgaXMgYXR0YWNoMjwvaHRtbD4=");
        itemDict.put("digest", "sha1-s14XRTXlwvzYfjo1t1u0rjB+ZUA=");
        itemDict.put("length", 32);
        itemDict.put("revpos", 3);
        attachmentDict.put("attach", itemDict);
        Map<String, Object> data = expandedRev.getAttachments();
        Assert.assertEquals(attachmentDict, expandedRev.getAttachments());

        // Examine the attachment store:
        Assert.assertEquals(2, attachments.count());
        Set<BlobKey> expected = new HashSet<BlobKey>();
        expected.add(BlobStore.keyForBlob(attach1));
        expected.add(BlobStore.keyForBlob(attach2));

        Assert.assertEquals(expected, attachments.allKeys());

        database.compact();  // This clears the body of the first revision
        Assert.assertEquals(1, attachments.count());

        Set<BlobKey> expected2 = new HashSet<BlobKey>();
        expected2.add(BlobStore.keyForBlob(attach2));
        Assert.assertEquals(expected2, attachments.allKeys());
    }

    /**
     * - (void) test13_GarbageCollectAttachments
     */
    public void test13_GarbageCollectAttachments() throws CouchbaseLiteException{
        List<RevisionInternal> revs = new ArrayList<RevisionInternal>();
        for(int i = 0; i < 100; i++)
            revs.add(this.putDocWithAttachment(String.format("doc-%d", i), String.format("Attachment #%d", i), false));
        for (int i = 0; i < 40; i++) {
            revs.set(i,
                    database.updateAttachment(
                            "attach",
                            null,
                            null,
                            AttachmentInternal.AttachmentEncoding.AttachmentEncodingNone,
                            revs.get(i).getDocID(),
                            revs.get(i).getRevID(),
                            null));
        }
        database.compact();
        assertEquals(60, database.getAttachmentStore().count());
    }

    /**
     * NOTE: Most of methods that are used in this test are no longer available with 1.2.0 or later
     * (With new table schema (iOS 1.1). If necessary, re-implment this test with new methods and
     * expected values.
     *
     @SuppressWarnings("unchecked")
     public void testPutLargeAttachment() throws Exception {

     String testAttachmentName = "test_attachment";

     BlobStore attachments = database.getAttachments();
     attachments.deleteBlobs();
     Assert.assertEquals(0, attachments.count());

     Status status = new Status();
     Map<String, Object> rev1Properties = new HashMap<String, Object>();
     rev1Properties.put("foo", 1);
     rev1Properties.put("bar", false);
     RevisionInternal rev1 = database.putRevision(new RevisionInternal(rev1Properties), null, false, status);

     Assert.assertEquals(Status.CREATED, status.getCode());

     StringBuffer largeAttachment = new StringBuffer();
     for (int i = 0; i < Database.kBigAttachmentLength; i++) {
     largeAttachment.append("big attachment!");
     }
     byte[] attach1 = largeAttachment.toString().getBytes();
     database.insertAttachmentForSequenceWithNameAndType(new ByteArrayInputStream(attach1),
     rev1.getSequence(), testAttachmentName, "text/plain", rev1.getGeneration());

     Attachment attachment = database.getAttachmentForSequence(rev1.getSequence(), testAttachmentName);
     Assert.assertEquals("text/plain", attachment.getContentType());
     InputStream is = attachment.getContent();
     byte[] data = IOUtils.toByteArray(is);
     is.close();
     Assert.assertTrue(Arrays.equals(attach1, data));

     EnumSet<Database.TDContentOptions> contentOptions = EnumSet.of(
     Database.TDContentOptions.TDIncludeAttachments,
     Database.TDContentOptions.TDBigAttachmentsFollow
     );

     Map<String, Object> attachmentDictForSequence = database.getAttachmentsDictForSequenceWithContent(
     rev1.getSequence(),
     contentOptions
     );

     Map<String, Object> innerDict = (Map<String, Object>) attachmentDictForSequence.get(testAttachmentName);

     if (innerDict.containsKey("stub")) {
     if (((Boolean) innerDict.get("stub")).booleanValue() == true) {
     throw new RuntimeException("Did not expected attachment dict 'stub' key to be true");
     } else {
     throw new RuntimeException("Did not expected attachment dict to have 'stub' key");
     }
     }

     if (!innerDict.containsKey("follows")) {
     throw new RuntimeException("Expected attachment dict to have 'follows' key");
     }

     RevisionInternal rev1WithAttachments = database.getDocumentWithIDAndRev(rev1.getDocId(), rev1.getRevId(), contentOptions);

     Map<String, Object> rev1WithAttachmentsProperties = rev1WithAttachments.getProperties();

     Map<String, Object> rev2Properties = new HashMap<String, Object>();
     rev2Properties.put("_id", rev1WithAttachmentsProperties.get("_id"));
     rev2Properties.put("foo", 2);

     RevisionInternal newRev = new RevisionInternal(rev2Properties);
     RevisionInternal rev2 = database.putRevision(newRev, rev1WithAttachments.getRevId(), false, status);
     Assert.assertEquals(Status.CREATED, status.getCode());

     database.copyAttachmentNamedFromSequenceToSequence(
     testAttachmentName,
     rev1WithAttachments.getSequence(),
     rev2.getSequence());

     // Check the 2nd revision's attachment:
     Attachment rev2FetchedAttachment = database.getAttachmentForSequence(rev2.getSequence(), testAttachmentName);
     Assert.assertEquals(attachment.getLength(), rev2FetchedAttachment.getLength());
     Assert.assertEquals(attachment.getMetadata(), rev2FetchedAttachment.getMetadata());
     Assert.assertEquals(attachment.getContentType(), rev2FetchedAttachment.getContentType());
     // Because of how getAttachmentForSequence works rev2FetchedAttachment has an open stream as a body, we have to close it.
     rev2FetchedAttachment.getContent().close();

     // Add a third revision of the same document:
     Map<String, Object> rev3Properties = new HashMap<String, Object>();
     rev3Properties.put("_id", rev2.getProperties().get("_id"));
     rev3Properties.put("foo", 3);
     rev3Properties.put("baz", false);

     RevisionInternal rev3 = new RevisionInternal(rev3Properties);
     rev3 = database.putRevision(rev3, rev2.getRevId(), false, status);
     Assert.assertEquals(Status.CREATED, status.getCode());

     byte[] attach3 = "<html><blink>attach3</blink></html>".getBytes();
     database.insertAttachmentForSequenceWithNameAndType(new ByteArrayInputStream(attach3),
     rev3.getSequence(), testAttachmentName, "text/html", rev3.getGeneration());

     // Check the 3rd revision's attachment:
     Attachment rev3FetchedAttachment = database.getAttachmentForSequence(rev3.getSequence(), testAttachmentName);

     InputStream isRev3 = rev3FetchedAttachment.getContent();
     data = IOUtils.toByteArray(isRev3);
     isRev3.close();
     Assert.assertTrue(Arrays.equals(attach3, data));
     Assert.assertEquals("text/html", rev3FetchedAttachment.getContentType());

     // TODO: why doesn't this work?
     // Assert.assertEquals(attach3.length, rev3FetchedAttachment.getLength());

     Set<BlobKey> blobKeys = database.getAttachments().allKeys();
     Assert.assertEquals(2, blobKeys.size());
     database.compact();
     blobKeys = database.getAttachments().allKeys();
     Assert.assertEquals(1, blobKeys.size());
     }
     */

    /**
     * in DatabaseAttachment_Tests.m
     * - (void) test11_PutAttachment
     */
    @SuppressWarnings("unchecked")
    public void testPutAttachment() throws Exception {

        // Put a revision that includes an _attachments dict:
        RevisionInternal rev1 = putDocWithAttachment(null, "This is the body of attach1", false);
        Map<String, Object> itemDict = new HashMap<String, Object>();
        itemDict.put("content_type", "text/plain");
        itemDict.put("digest", "sha1-gOHUOBmIMoDCrMuGyaLWzf1hQTE=");
        itemDict.put("length", 27);
        itemDict.put("stub", true);
        itemDict.put("revpos", 1);
        Map<String, Object> attachmentDict = new HashMap<String, Object>();
        attachmentDict.put("attach", itemDict);
        Assert.assertEquals(attachmentDict, rev1.getAttachments());

        // Examine the attachment store:
        Assert.assertEquals(database.getAttachmentStore().count(), 1);

        // Get the revision:
        RevisionInternal gotRev1 = database.getDocument(rev1.getDocID(), rev1.getRevID(), true);
        Assert.assertEquals(attachmentDict, gotRev1.getAttachments());

        // Update the attachment directly:
        boolean gotExpectedErrorCode = false;
        byte[] attachv2 = "Replaced body of attach".getBytes();
        try {
            database.updateAttachment("attach",
                    blobForData(database, attachv2),
                    "application/foo",
                    AttachmentInternal.AttachmentEncoding.AttachmentEncodingNone,
                    rev1.getDocID(),
                    null,
                    null);
        } catch (CouchbaseLiteException e) {
            gotExpectedErrorCode = (e.getCBLStatus().getCode() == Status.CONFLICT);
        }
        Assert.assertTrue(gotExpectedErrorCode);

        gotExpectedErrorCode = false;
        try {
            database.updateAttachment("attach",
                    blobForData(database, attachv2),
                    "application/foo",
                    AttachmentInternal.AttachmentEncoding.AttachmentEncodingNone,
                    rev1.getDocID(),
                    "1-deadbeef",
                    null);
        } catch (CouchbaseLiteException e) {
            gotExpectedErrorCode = (e.getCBLStatus().getCode() == Status.CONFLICT);
        }
        Assert.assertTrue(gotExpectedErrorCode);

        RevisionInternal rev2 = database.updateAttachment("attach",
                blobForData(database, attachv2),
                "application/foo",
                AttachmentInternal.AttachmentEncoding.AttachmentEncodingNone,
                rev1.getDocID(),
                rev1.getRevID(),
                null);
        Assert.assertNotNull(rev2);
        Assert.assertEquals(rev1.getDocID(), rev2.getDocID());
        Assert.assertEquals(2, rev2.getGeneration());

        // Get the updated revision:
        RevisionInternal gotRev2 = database.getDocument(rev2.getDocID(), rev2.getRevID(), true);

        itemDict = new HashMap<String, Object>();
        itemDict.put("content_type", "application/foo");
        itemDict.put("digest", "sha1-mbT3208HI3PZgbG4zYWbDW2HsPk=");
        itemDict.put("length", 23);
        itemDict.put("stub", true);
        itemDict.put("revpos", 2);
        attachmentDict = new HashMap<String, Object>();
        attachmentDict.put("attach", itemDict);

        Assert.assertEquals(attachmentDict, gotRev2.getAttachments());

        // Delete the attachment:
        gotExpectedErrorCode = false;
        try {
            database.updateAttachment("nosuchattach",
                    null,
                    null,
                    AttachmentInternal.AttachmentEncoding.AttachmentEncodingNone,
                    rev2.getDocID(),
                    rev2.getRevID(),
                    null);
        } catch (CouchbaseLiteException e) {
            gotExpectedErrorCode = (e.getCBLStatus().getCode() == Status.NOT_FOUND);
        }
        Assert.assertTrue(gotExpectedErrorCode);

        gotExpectedErrorCode = false;
        try {
            database.updateAttachment("nosuchattach",
                    null,
                    null,
                    AttachmentInternal.AttachmentEncoding.AttachmentEncodingNone,
                    "nosuchdoc",
                    "nosuchrev",
                    null);
        } catch (CouchbaseLiteException e) {
            gotExpectedErrorCode = (e.getCBLStatus().getCode() == Status.NOT_FOUND);
        }
        Assert.assertTrue(gotExpectedErrorCode);

        RevisionInternal rev3 = database.updateAttachment("attach",
                null,
                null,
                AttachmentInternal.AttachmentEncoding.AttachmentEncodingNone,
                rev2.getDocID(),
                rev2.getRevID(),
                null);
        Assert.assertNotNull(rev2);
        Assert.assertEquals(rev2.getDocID(), rev3.getDocID());
        Assert.assertEquals(3, rev3.getGeneration());

        // Get the updated revision:
        RevisionInternal gotRev3 = database.getDocument(rev3.getDocID(), rev3.getRevID(), true);
        Assert.assertNull(gotRev3.getAttachments());
    }

    public void testAddAndGetAttachment() throws CouchbaseLiteException {
        Document document = database.createDocument();
        UnsavedRevision rev = document.createRevision();

        byte[] attach = "This is the body of attach".getBytes();
        ;
        InputStream in = new ByteArrayInputStream(attach);
        rev.setAttachment("attach", "text/plain", in);

        assertNotNull(rev.getAttachment("attach"));
        assertEquals(1, rev.getAttachments().size());
        rev.save();
    }

    public void testStreamAttachmentBlobStoreWriter() throws Exception {
        BlobStore attachments = database.getAttachmentStore();

        BlobStoreWriter blobWriter = new com.couchbase.lite.BlobStoreWriter(attachments);
        String testBlob = "foo";
        blobWriter.appendData(new String(testBlob).getBytes());
        blobWriter.finish();

        String sha1Base64Digest = "sha1-C+7Hteo/D9vJXQ3UfzxbwnXaijM=";
        Assert.assertEquals(blobWriter.sHA1DigestString(), sha1Base64Digest);
        Assert.assertEquals(blobWriter.mD5DigestString(), "md5-rL0Y20zC+Fzt72VPzMSk2A==");

        // install it
        blobWriter.install();

        // look it up in blob store and make sure it's there
        BlobKey blobKey = new BlobKey(sha1Base64Digest);
        byte[] blob = attachments.blobForKey(blobKey);
        Assert.assertTrue(Arrays.equals(testBlob.getBytes(Charset.forName("UTF-8")), blob));
    }

    /**
     * https://github.com/couchbase/couchbase-lite-android/issues/134
     */
    public void testGetAttachmentBodyUsingPrefetch() throws CouchbaseLiteException, IOException {

        // add a doc with an attachment
        Document doc = database.createDocument();
        UnsavedRevision rev = doc.createRevision();

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "bar");
        rev.setUserProperties(properties);

        final byte[] attachBodyBytes = "attach body".getBytes();
        Attachment attachment = new Attachment(
                new ByteArrayInputStream(attachBodyBytes),
                "text/plain"
        );

        final String attachmentName = "test_attachment.txt";
        rev.addAttachment(attachment, attachmentName);
        rev.save();

        // do query that finds that doc with prefetch
        View view = database.getView("aview");
        view.setMapReduce(new Mapper() {

            @Override
            public void map(Map<String, Object> document, Emitter emitter) {
                String id = (String) document.get("_id");
                emitter.emit(id, null);
            }
        }, null, "1");


        // try to get the attachment

        Query query = view.createQuery();
        query.setPrefetch(true);

        QueryEnumerator results = query.run();

        while (results.hasNext()) {

            QueryRow row = results.next();


            // This returns the revision just fine, but the sequence number
            // is set to 0.
            SavedRevision revision = row.getDocument().getCurrentRevision();

            List<String> attachments = revision.getAttachmentNames();

            // This returns an Attachment object which looks ok, except again
            // its sequence number is 0. The metadata property knows about
            // the length and mime type of the attachment. It also says
            // "stub" -> "true".
            Attachment attachmentRetrieved = revision.getAttachment(attachmentName);

            // This throws a CouchbaseLiteException with Status.NOT_FOUND.
            InputStream is = attachmentRetrieved.getContent();
            assertNotNull(is);
            byte[] attachmentDataRetrieved = TextUtils.read(is);
            is.close();
            String attachmentDataRetrievedString = new String(attachmentDataRetrieved);
            String attachBodyString = new String(attachBodyBytes);
            assertEquals(attachBodyString, attachmentDataRetrievedString);
        }
    }

    /**
     * Regression test for https://github.com/couchbase/couchbase-lite-java-core/issues/218
     */

    public void testGetAttachmentAfterItDeleted() throws CouchbaseLiteException, IOException {

        // add a doc with an attachment
        Document doc = database.createDocument();
        UnsavedRevision rev = doc.createRevision();

        final byte[] attachBodyBytes = "attach body".getBytes();
        Attachment attachment = new Attachment(
                new ByteArrayInputStream(attachBodyBytes),
                "text/plain"
        );

        String attachmentName = "test_delete_attachment.txt";
        rev.addAttachment(attachment, attachmentName);
        rev.save();

        UnsavedRevision rev1 = doc.createRevision();
        Attachment currentAttachment = rev1.getAttachment(attachmentName);
        assertNotNull(currentAttachment);

        rev1.removeAttachment(attachmentName);
        currentAttachment = rev1.getAttachment(attachmentName);
        assertNull(currentAttachment); // otherwise NullPointerException when currentAttachment.getMetadata()
        rev1.save();

        currentAttachment = doc.getCurrentRevision().getAttachment(attachmentName);
        assertNull(currentAttachment); // otherwise NullPointerException when currentAttachment.getMetadata()
    }


    /**
     * Regression test for https://github.com/couchbase/couchbase-lite-android-core/issues/70
     */
    public void testAttachmentDisappearsAfterSave() throws CouchbaseLiteException, IOException {

        // create a doc with an attachment
        Document doc = database.createDocument();
        String content = "This is a test attachment!";
        ByteArrayInputStream body = new ByteArrayInputStream(content.getBytes());
        UnsavedRevision rev = doc.createRevision();
        rev.setAttachment("index.html", "text/plain; charset=utf-8", body);
        rev.save();

        // make sure the doc's latest revision has the attachment
        Map<String, Object> attachments = (Map) doc.getCurrentRevision().getProperty("_attachments");
        assertNotNull(attachments);
        assertEquals(1, attachments.size());

        // make sure the rev has the attachment
        attachments = (Map) rev.getProperty("_attachments");
        assertNotNull(attachments);
        assertEquals(1, attachments.size());

        // create new properties to add
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("foo", "bar");

        // make sure the new rev still has the attachment
        UnsavedRevision rev2 = doc.createRevision();
        rev2.getProperties().putAll(properties);
        rev2.save();
        attachments = (Map) rev2.getProperty("_attachments");
        assertNotNull(attachments);
        assertEquals(1, attachments.size());
    }

    /**
     * attempt to reproduce https://github.com/couchbase/couchbase-lite-android/issues/328 &
     * https://github.com/couchbase/couchbase-lite-android/issues/325
     */
    public void testSetAttachmentsSequentially() throws CouchbaseLiteException, IOException {

        try {
            //Create rev1 of document with just properties
            Document doc = database.createDocument();
            String id = doc.getId();
            Map<String, Object> docProperties = new HashMap<String, Object>();
            docProperties.put("Iteration", 0);
            doc.putProperties(docProperties);

            UnsavedRevision rev = null;

            //Create a new revision with attachment1
            InputStream attachmentStream1 = getAsset("attachment.png");
            doc = database.getDocument(id);//not required
            byte[] jsonb = Manager.getObjectMapper().writeValueAsBytes(doc.getProperties().get("_attachments"));
            Log.d(Database.TAG, "Doc _rev = %s", doc.getProperties().get("_rev"));
            Log.d(Database.TAG, "Doc properties = %s", new String(jsonb));
            rev = doc.createRevision();
            rev.setAttachment("attachment1", "image/png", attachmentStream1);
            rev.save();
            attachmentStream1.close();

            //Create a new revision updated properties
            doc = database.getDocument(id);//not required
            jsonb = Manager.getObjectMapper().writeValueAsBytes(doc.getProperties().get("_attachments"));
            Log.d(Database.TAG, "Doc _rev = %s", doc.getProperties().get("_rev"));
            Log.d(Database.TAG, "Doc properties = %s", new String(jsonb));
            Map<String, Object> curProperties;
            curProperties = doc.getProperties();
            docProperties = new HashMap<String, Object>();
            docProperties.putAll(curProperties);
            docProperties.put("Iteration", 1);
            doc.putProperties(docProperties);


            //Create a new revision with attachment2
            InputStream attachmentStream2 = getAsset("attachment.png");
            doc = database.getDocument(id);//not required
            jsonb = Manager.getObjectMapper().writeValueAsBytes(doc.getProperties().get("_attachments"));
            Log.d(Database.TAG, "Doc _rev = %s", doc.getProperties().get("_rev"));
            Log.d(Database.TAG, "Doc properties = %s", new String(jsonb));
            rev = doc.createRevision();
            rev.setAttachment("attachment2", "image/png", attachmentStream2);
            rev.save();
            attachmentStream2.close();

            //Assert final document revision
            doc = database.getDocument(id);
            curProperties = doc.getProperties();
            assertEquals(4, curProperties.size());
            Map<String, Object> attachments = (Map<String, Object>) doc.getCurrentRevision().getProperty("_attachments");
            assertNotNull(attachments);
            assertEquals(2, attachments.size());
        } catch (CouchbaseLiteException e) {
            Log.e(Database.TAG, "Error adding attachment: " + e.getMessage(), e);
            fail();
        }
    }

    /**
     * attempt to reproduce https://github.com/couchbase/couchbase-lite-android/issues/328 &
     * https://github.com/couchbase/couchbase-lite-android/issues/325
     */
    public void failingTestSetAttachmentsSequentiallyInTransaction() throws CouchbaseLiteException, IOException {

        boolean success = database.runInTransaction(new TransactionalTask() {

            public boolean run() {

                try {
                    // add a doc with an attachment
                    Document doc = database.createDocument();
                    String id = doc.getId();

                    InputStream jsonStream = getAsset("300k.json");

                    Map<String, Object> docProperties = null;

                    docProperties = Manager.getObjectMapper().readValue(jsonStream, Map.class);


                    docProperties.put("Iteration", 0);

                    doc.putProperties(docProperties);

                    jsonStream.close();
                    UnsavedRevision rev = null;


                    for (int i = 0; i < 20; i++) {

                        InputStream attachmentStream1 = getAsset("attachment.png");

                        Log.e(Database.TAG, "TEST ITERATION " + i);
                        doc = database.getDocument(id);//not required
                        rev = doc.createRevision();
                        rev.setAttachment("attachment " + i * 5, "image/png", attachmentStream1);
                        rev.save();

                        attachmentStream1.close();

                        InputStream attachmentStream2 = getAsset("attachment.png");
                        doc = database.getDocument(id);//not required
                        rev = doc.createRevision();
                        rev.setAttachment("attachment " + i * 5 + 1, "image/png", attachmentStream2);
                        rev.save();

                        attachmentStream2.close();

                        InputStream attachmentStream3 = getAsset("attachment.png");
                        doc = database.getDocument(id);//not required
                        rev = doc.createRevision();
                        rev.setAttachment("attachment " + i * 5 + 2, "image/png", attachmentStream3);
                        rev.save();

                        attachmentStream3.close();

                        InputStream attachmentStream4 = getAsset("attachment.png");
                        doc = database.getDocument(id);//not required
                        rev = doc.createRevision();
                        rev.setAttachment("attachment " + i * 5 + 3, "image/png", attachmentStream4);
                        rev.save();

                        attachmentStream4.close();

                        InputStream attachmentStream5 = getAsset("attachment.png");
                        doc = database.getDocument(id);//not required
                        rev = doc.createRevision();
                        rev.setAttachment("attachment " + i * 5 + 4, "image/png", attachmentStream5);
                        rev.save();

                        attachmentStream5.close();

                        Map<String, Object> curProperties;

                        doc = database.getDocument(id);//not required
                        curProperties = doc.getProperties();
                        docProperties = new HashMap<String, Object>();
                        docProperties.putAll(curProperties);
                        docProperties.put("Iteration", (i + 1) * 5);
                        doc.putProperties(docProperties);
                    }

                    Map<String, Object> curProperties;
                    doc = database.getDocument(id);//not required
                    curProperties = doc.getProperties();
                    assertEquals(22, curProperties.size());
                    Map<String, Object> attachments = (Map<String, Object>) doc.getCurrentRevision().getProperty("_attachments");
                    assertNotNull(attachments);
                    assertEquals(100, attachments.size());
                } catch (Exception e) {
                    Log.e(Database.TAG, "Error deserializing properties from JSON", e);
                    return false;
                }

                return true;
            }

        });
        assertTrue("transaction with set attachments sequentially failed", success);
    }

    /**
     * Regression test for https://github.com/couchbase/couchbase-lite-android-core/issues/70
     */
    public void testAttachmentInstallBodies() throws Exception {
        Map<String, Object> attachmentsMap = new HashMap<String, Object>();
        Map<String, Object> attachmentMap = new HashMap<String, Object>();
        attachmentMap.put("length", 25);
        String attachmentName = "index.html";
        attachmentsMap.put(attachmentName, attachmentMap);
        Map<String, Object> updatedAttachments = Attachment.installAttachmentBodies(attachmentsMap, database);
        assertTrue(updatedAttachments.size() > 0);
        assertTrue(updatedAttachments.containsKey(attachmentName));
    }


    public void testGetContentURL() throws Exception {
        String attachmentName = "index.html";
        String content = "This is a test attachment!";
        Document doc = createDocWithAttachment(database, attachmentName, content);
        Attachment attachment = doc.getCurrentRevision().getAttachment(attachmentName);
        URL url = attachment.getContentURL();
        assertNotNull(url);
        FileInputStream fis = new FileInputStream(new File(url.toURI()));
        byte[] buffer = new byte[1024];
        int len = fis.read(buffer);
        assertTrue(len != -1);
        String content2 = new String(buffer, 0, len);
        assertEquals(content, content2);
        fis.close();
    }

    public void testAttachmentThrowIoException() {
        InputStream in = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException();
            }
        };

        Document doc = database.createDocument();
        UnsavedRevision rev = doc.createRevision();
        rev.setAttachment("ioe_attach", "text/plain", in);

        try {
            rev.save();
            fail("Saved revision with corrupt attachment");
        } catch (CouchbaseLiteException expected) {
            assertEquals(Status.ATTACHMENT_ERROR, expected.getCBLStatus().getCode());
        }
    }

    public void testGetAttachmentFromUnsavedRevision() throws Exception {
        String attachmentName = "index.html";
        String content = "This is a test attachment!";

        Document doc = createDocWithAttachment(database, attachmentName, content);
        UnsavedRevision rev = doc.createRevision();
        Attachment attachment = rev.getAttachment(attachmentName);
        assertNotNull(attachment);

        InputStream in = attachment.getContent();
        assertNotNull(in);
        assertEquals(IOUtils.toString(in, "UTF-8"), content);
        in.close();
    }

    public void testGetAttachmentFromUnsavedRevisionWithMultipleRevisions() throws Exception {
        String attachmentName = "index.html";
        String content = "This is a test attachment!";

        Document doc = createDocWithAttachment(database, attachmentName, content);

        // added extra two revisions to make sure in case the revision that has an attachment is
        // more than two generation older than current.
        doc.createRevision().save();
        doc.createRevision().save();

        UnsavedRevision rev = doc.createRevision();
        Attachment attachment = rev.getAttachment(attachmentName);
        assertNotNull(attachment);

        InputStream in = attachment.getContent();
        assertNotNull(in);
        assertEquals(IOUtils.toString(in, "UTF-8"), content);
        in.close();
    }

    public void testGzippedAttachments() throws Exception {
        String attachmentName = "index.html";
        byte content[] = "This is a test attachment!".getBytes("UTF-8");

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(byteOut);
        gzipOut.write(content);
        gzipOut.close();
        byte contentGzipped[] = byteOut.toByteArray();

        Document doc = database.createDocument();
        UnsavedRevision rev = doc.createRevision();
        rev.setAttachment(attachmentName, "text/html", new ByteArrayInputStream(contentGzipped));
        rev.save();

        SavedRevision savedRev = doc.getCurrentRevision();
        Attachment attachment = savedRev.getAttachment(attachmentName);

        // As far as revision users are concerned their data is not gzipped
        InputStream in = attachment.getContent();
        assertNotNull(in);
        assertTrue(Arrays.equals(content, IOUtils.toByteArray(in)));
        in.close();

        Document gotDoc = database.getDocument(doc.getId());
        Revision gotRev = gotDoc.getCurrentRevision();
        Attachment gotAtt = gotRev.getAttachment(attachmentName);
        in = gotAtt.getContent();
        assertNotNull(in);
        assertTrue(Arrays.equals(content, IOUtils.toByteArray(in)));
        in.close();
    }

    // Store Gzipped attachment by Base64 encoding
    public void testGzippedAttachmentByBase64() throws Exception {
        String attachmentName = "attachment.png";

        // 1. store attachment with doc

        // 1.a load attachment data from asset
        InputStream attachmentStream = getAsset(attachmentName);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
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
        Document putDoc = database.createDocument();
        putDoc.putProperties(propsMap);
        String docId = putDoc.getId();

        // 2. Load attachment from database and compare it with original

        // 2.a load doc and attachment from database
        Document getDoc = database.getDocument(docId);
        Attachment attachment = getDoc.getCurrentRevision().getAttachment(attachmentName);
        assertEquals(bytes.length, attachment.getLength());
        assertEquals("image/png", attachment.getContentType());
        assertEquals("gzip", attachment.getMetadata().get("encoding"));

        InputStream is = attachment.getContent();
        byte[] receivedBytes = getBytesFromInputStream(is);
        assertEquals(bytes.length, receivedBytes.length);
        is.close();

        assertTrue(Arrays.equals(bytes, receivedBytes));
    }

    private RevisionInternal putDocWithAttachment(String docID, String attachmentText, boolean compress) throws CouchbaseLiteException {
        byte[] attachmentData = attachmentText.getBytes();
        String encoding = null;
        int length = 0;
        if (compress) {
            length = attachmentData.length;
            encoding = "gzip";
            // encode
        }
        String base64 = Base64.encodeBytes(attachmentData);
        Map<String, Object> itemDict = new HashMap<String, Object>();
        itemDict.put("content_type", "text/plain");
        itemDict.put("data", base64);
        itemDict.put("encoding", encoding);
        itemDict.put("length", length == 0 ? null : length);
        Map<String, Object> attachmentDict = new HashMap<String, Object>();
        attachmentDict.put("attach", itemDict);
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("_id", docID);
        props.put("foo", 1);
        props.put("bar", false);
        props.put("_attachments", attachmentDict);

        Status status = new Status(Status.OK);
        RevisionInternal rev = database.putRevision(new RevisionInternal(props), null, false, status);
        Assert.assertEquals(Status.CREATED, status.getCode());
        return rev;
    }

    private static byte[] getBytesFromInputStream(InputStream is) {
        org.apache.commons.io.output.ByteArrayOutputStream os = new org.apache.commons.io.output.ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
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

    private static Map<String, Map<String, Object>> getAttachmentsDict(byte[] data, String name, String type, boolean gzipped) {
        if (gzipped)
            // TODO
            ;
        Map<String, Object> att = new HashMap<String, Object>();
        att.put("content_type", type);
        att.put("data", data);
        if (gzipped)
            att.put("encoding", "gzip");
        Map<String, Map<String, Object>> atts = new HashMap<String, Map<String, Object>>();
        atts.put(name, att);
        return atts;
    }

    private static Map<String, Map<String, Object>> getAttachmentsStub(String name) {
        Map<String, Object> att = new HashMap<String, Object>();
        att.put("stub", true);
        Map<String, Map<String, Object>> atts = new HashMap<String, Map<String, Object>>();
        atts.put(name, att);
        return atts;
    }

    private static BlobStoreWriter blobForData(Database db, byte[] data)
            throws SymmetricKeyException {
        try {
            BlobStoreWriter blob = db.getAttachmentWriter();
            blob.appendData(data);
            blob.finish();
            return blob;
        } catch (IOException e) {
            return null;
        }
    }
}
