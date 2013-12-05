/**
 * Original iOS version by  Jens Alfke
 * Ported to Android by Marty Schoch
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
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

package com.couchbase.lite.testapp.tests;

import com.couchbase.lite.*;
import com.couchbase.lite.BlobStoreWriter;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.support.Base64;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Attachments extends CBLiteTestCase {

    public static final String TAG = "Attachments";

    @SuppressWarnings("unchecked")
    public void testAttachments() throws Exception {

        BlobStore attachments = database.getAttachments();

        Assert.assertEquals(0, attachments.count());
        Assert.assertEquals(new HashSet<Object>(), attachments.allKeys());

        Status status = new Status();
        Map<String,Object> rev1Properties = new HashMap<String,Object>();
        rev1Properties.put("foo", 1);
        rev1Properties.put("bar", false);
        RevisionInternal rev1 = database.putRevision(new RevisionInternal(rev1Properties, database), null, false, status);

        Assert.assertEquals(Status.CREATED, status.getCode());

        byte[] attach1 = "This is the body of attach1".getBytes();
        database.insertAttachmentForSequenceWithNameAndType(new ByteArrayInputStream(attach1), rev1.getSequence(), "attach", "text/plain", rev1.getGeneration());
        Assert.assertEquals(Status.CREATED, status.getCode());

        Attachment attachment = database.getAttachmentForSequence(rev1.getSequence(), "attach");
        Assert.assertEquals("text/plain", attachment.getContentType());
        byte[] data = IOUtils.toByteArray(attachment.getContent());
        Assert.assertTrue(Arrays.equals(attach1, data));

        Map<String,Object> innerDict = new HashMap<String,Object>();
        innerDict.put("content_type", "text/plain");
        innerDict.put("digest", "sha1-gOHUOBmIMoDCrMuGyaLWzf1hQTE=");
        innerDict.put("length", 27);
        innerDict.put("stub", true);
        innerDict.put("revpos", 1);
        Map<String,Object> attachmentDict = new HashMap<String,Object>();
        attachmentDict.put("attach", innerDict);

        Map<String,Object> attachmentDictForSequence = database.getAttachmentsDictForSequenceWithContent(rev1.getSequence(), EnumSet.noneOf(Database.TDContentOptions.class));
        Assert.assertEquals(attachmentDict, attachmentDictForSequence);

        RevisionInternal gotRev1 = database.getDocumentWithIDAndRev(rev1.getDocId(), rev1.getRevId(), EnumSet.noneOf(Database.TDContentOptions.class));
        Map<String,Object> gotAttachmentDict = (Map<String,Object>)gotRev1.getProperties().get("_attachments");
        Assert.assertEquals(attachmentDict, gotAttachmentDict);

        // Check the attachment dict, with attachments included:
        innerDict.remove("stub");
        innerDict.put("data", Base64.encodeBytes(attach1));
        attachmentDictForSequence = database.getAttachmentsDictForSequenceWithContent(rev1.getSequence(), EnumSet.of(Database.TDContentOptions.TDIncludeAttachments));
        Assert.assertEquals(attachmentDict, attachmentDictForSequence);

        gotRev1 = database.getDocumentWithIDAndRev(rev1.getDocId(), rev1.getRevId(), EnumSet.of(Database.TDContentOptions.TDIncludeAttachments));
        gotAttachmentDict = (Map<String,Object>)gotRev1.getProperties().get("_attachments");
        Assert.assertEquals(attachmentDict, gotAttachmentDict);


        // Add a second revision that doesn't update the attachment:
        Map<String,Object> rev2Properties = new HashMap<String,Object>();
        rev2Properties.put("_id", rev1.getDocId());
        rev2Properties.put("foo", 2);
        rev2Properties.put("bazz", false);
        RevisionInternal rev2 = database.putRevision(new RevisionInternal(rev2Properties, database), rev1.getRevId(), false, status);
        Assert.assertEquals(Status.CREATED, status.getCode());

        database.copyAttachmentNamedFromSequenceToSequence("attach", rev1.getSequence(), rev2.getSequence());

        // Add a third revision of the same document:
        Map<String,Object> rev3Properties = new HashMap<String,Object>();
        rev3Properties.put("_id", rev2.getDocId());
        rev3Properties.put("foo", 2);
        rev3Properties.put("bazz", false);
        RevisionInternal rev3 = database.putRevision(new RevisionInternal(rev3Properties, database), rev2.getRevId(), false, status);
        Assert.assertEquals(Status.CREATED, status.getCode());

        byte[] attach2 = "<html>And this is attach2</html>".getBytes();
        database.insertAttachmentForSequenceWithNameAndType(new ByteArrayInputStream(attach2), rev3.getSequence(), "attach", "text/html", rev2.getGeneration());

        // Check the 2nd revision's attachment:
        Attachment attachment2 = database.getAttachmentForSequence(rev2.getSequence(), "attach");

        Assert.assertEquals("text/plain", attachment2.getContentType());
        data = IOUtils.toByteArray(attachment2.getContent());
        Assert.assertTrue(Arrays.equals(attach1, data));

        // Check the 3rd revision's attachment:
        Attachment attachment3 = database.getAttachmentForSequence(rev3.getSequence(), "attach");
        Assert.assertEquals("text/html", attachment3.getContentType());
        data = IOUtils.toByteArray(attachment3.getContent());
        Assert.assertTrue(Arrays.equals(attach2, data));

        // Examine the attachment store:
        Assert.assertEquals(2, attachments.count());
        Set<BlobKey> expected = new HashSet<BlobKey>();
        expected.add(BlobStore.keyForBlob(attach1));
        expected.add(BlobStore.keyForBlob(attach2));

        Assert.assertEquals(expected, attachments.allKeys());

        status = database.compact();  // This clears the body of the first revision
        Assert.assertEquals(Status.OK, status.getCode());
        Assert.assertEquals(1, attachments.count());

        Set<BlobKey> expected2 = new HashSet<BlobKey>();
        expected2.add(BlobStore.keyForBlob(attach2));
        Assert.assertEquals(expected2, attachments.allKeys());
    }

    @SuppressWarnings("unchecked")
    public void testPutLargeAttachment() throws Exception {

        BlobStore attachments = database.getAttachments();
        attachments.deleteBlobs();
        Assert.assertEquals(0, attachments.count());
        
        Status status = new Status();
        Map<String,Object> rev1Properties = new HashMap<String,Object>();
        rev1Properties.put("foo", 1);
        rev1Properties.put("bar", false);
        RevisionInternal rev1 = database.putRevision(new RevisionInternal(rev1Properties, database), null, false, status);

        Assert.assertEquals(Status.CREATED, status.getCode());

        StringBuffer largeAttachment = new StringBuffer();
        for (int i=0; i< Database.kBigAttachmentLength; i++) {
            largeAttachment.append("big attachment!");
        }
        byte[] attach1 = largeAttachment.toString().getBytes();
        database.insertAttachmentForSequenceWithNameAndType(new ByteArrayInputStream(attach1), rev1.getSequence(), "attach", "text/plain", rev1.getGeneration());

        Attachment attachment = database.getAttachmentForSequence(rev1.getSequence(), "attach");
        Assert.assertEquals("text/plain", attachment.getContentType());
        byte[] data = IOUtils.toByteArray(attachment.getContent());
        Assert.assertTrue(Arrays.equals(attach1, data));

        EnumSet<Database.TDContentOptions> contentOptions = EnumSet.of(
                Database.TDContentOptions.TDIncludeAttachments,
                Database.TDContentOptions.TDBigAttachmentsFollow
        );

        Map<String,Object> attachmentDictForSequence = database.getAttachmentsDictForSequenceWithContent(
                rev1.getSequence(),
                contentOptions
        );

        Map<String,Object> innerDict = (Map<String,Object>) attachmentDictForSequence.get("attach");

        if (!innerDict.containsKey("stub")) {
            throw new RuntimeException("Expected attachment dict to have 'stub' key");
        }

        if (((Boolean)innerDict.get("stub")).booleanValue() == false) {
            throw new RuntimeException("Expected attachment dict 'stub' key to be true");
        }

        if (!innerDict.containsKey("follows")) {
            throw new RuntimeException("Expected attachment dict to have 'follows' key");
        }

        RevisionInternal rev1WithAttachments = database.getDocumentWithIDAndRev(rev1.getDocId(), rev1.getRevId(), contentOptions);
        Map<String,Object> rev1PropertiesPrime = rev1WithAttachments.getProperties();
        rev1PropertiesPrime.put("foo", 2);
        RevisionInternal newRev = new RevisionInternal(rev1PropertiesPrime, database);
        RevisionInternal rev2 = database.putRevision(newRev, rev1WithAttachments.getRevId(), false, status);
        Assert.assertEquals(Status.CREATED, status.getCode());

    }

    @SuppressWarnings("unchecked")
    public void testPutAttachment() throws CouchbaseLiteException {

        BlobStore attachments = database.getAttachments();
        attachments.deleteBlobs();
        Assert.assertEquals(0, attachments.count());

        // Put a revision that includes an _attachments dict:
        byte[] attach1 = "This is the body of attach1".getBytes();
        String base64 = Base64.encodeBytes(attach1);

        Map<String,Object> attachment = new HashMap<String,Object>();
        attachment.put("content_type", "text/plain");
        attachment.put("data", base64);
        Map<String,Object> attachmentDict = new HashMap<String,Object>();
        attachmentDict.put("attach", attachment);
        Map<String,Object> properties = new HashMap<String,Object>();
        properties.put("foo", 1);
        properties.put("bar", false);
        properties.put("_attachments", attachmentDict);

        RevisionInternal rev1 = database.putRevision(new RevisionInternal(properties, database), null, false);

        // Examine the attachment store:
        Assert.assertEquals(1, attachments.count());

        // Get the revision:
        RevisionInternal gotRev1 = database.getDocumentWithIDAndRev(rev1.getDocId(), rev1.getRevId(), EnumSet.noneOf(Database.TDContentOptions.class));
        Map<String,Object> gotAttachmentDict = (Map<String,Object>)gotRev1.getProperties().get("_attachments");

        Map<String,Object> innerDict = new HashMap<String,Object>();
        innerDict.put("content_type", "text/plain");
        innerDict.put("digest", "sha1-gOHUOBmIMoDCrMuGyaLWzf1hQTE=");
        innerDict.put("length", 27);
        innerDict.put("stub", true);
        innerDict.put("revpos", 1);

        Map<String,Object> expectAttachmentDict = new HashMap<String,Object>();
        expectAttachmentDict.put("attach", innerDict);

        Assert.assertEquals(expectAttachmentDict, gotAttachmentDict);

        // Update the attachment directly:
        byte[] attachv2 = "Replaced body of attach".getBytes();
        boolean gotExpectedErrorCode = false;
        try {
            database.updateAttachment("attach", new ByteArrayInputStream(attachv2), "application/foo", rev1.getDocId(), null);
        } catch (CouchbaseLiteException e) {
            gotExpectedErrorCode = (e.getCBLStatus().getCode() == Status.CONFLICT);
        }
        Assert.assertTrue(gotExpectedErrorCode);

        gotExpectedErrorCode = false;
        try {
            database.updateAttachment("attach", new ByteArrayInputStream(attachv2), "application/foo", rev1.getDocId(), "1-bogus");
        } catch (CouchbaseLiteException e) {
            gotExpectedErrorCode = (e.getCBLStatus().getCode() == Status.CONFLICT);
        }
        Assert.assertTrue(gotExpectedErrorCode);

        gotExpectedErrorCode = false;
        RevisionInternal rev2 = null;
        try {
            rev2 = database.updateAttachment("attach", new ByteArrayInputStream(attachv2), "application/foo", rev1.getDocId(), rev1.getRevId());
        } catch (CouchbaseLiteException e) {
            gotExpectedErrorCode = true;
        }
        Assert.assertFalse(gotExpectedErrorCode);

        Assert.assertEquals(rev1.getDocId(), rev2.getDocId());
        Assert.assertEquals(2, rev2.getGeneration());

        // Get the updated revision:
        RevisionInternal gotRev2 = database.getDocumentWithIDAndRev(rev2.getDocId(), rev2.getRevId(), EnumSet.noneOf(Database.TDContentOptions.class));
        attachmentDict = (Map<String, Object>) gotRev2.getProperties().get("_attachments");

        innerDict = new HashMap<String,Object>();
        innerDict.put("content_type", "application/foo");
        innerDict.put("digest", "sha1-mbT3208HI3PZgbG4zYWbDW2HsPk=");
        innerDict.put("length", 23);
        innerDict.put("stub", true);
        innerDict.put("revpos", 2);

        expectAttachmentDict.put("attach", innerDict);

        Assert.assertEquals(expectAttachmentDict, attachmentDict);

        // Delete the attachment:
        gotExpectedErrorCode = false;
        try {
            database.updateAttachment("nosuchattach", null, null, rev2.getDocId(), rev2.getRevId());
        } catch (CouchbaseLiteException e) {
            gotExpectedErrorCode = (e.getCBLStatus().getCode() == Status.NOT_FOUND);
        }
        Assert.assertTrue(gotExpectedErrorCode);

        gotExpectedErrorCode = false;
        try {
            database.updateAttachment("nosuchattach", null, null, "nosuchdoc", "nosuchrev");
        } catch (CouchbaseLiteException e) {
            gotExpectedErrorCode = (e.getCBLStatus().getCode() == Status.NOT_FOUND);
        }
        Assert.assertTrue(gotExpectedErrorCode);


        RevisionInternal rev3 = database.updateAttachment("attach", null, null, rev2.getDocId(), rev2.getRevId());
        Assert.assertEquals(rev2.getDocId(), rev3.getDocId());
        Assert.assertEquals(3, rev3.getGeneration());

        // Get the updated revision:
        RevisionInternal gotRev3 = database.getDocumentWithIDAndRev(rev3.getDocId(), rev3.getRevId(), EnumSet.noneOf(Database.TDContentOptions.class));
        attachmentDict = (Map<String, Object>) gotRev3.getProperties().get("_attachments");
        Assert.assertNull(attachmentDict);

        database.close();
    }

    public void testStreamAttachmentBlobStoreWriter() {


        BlobStore attachments = database.getAttachments();

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

}
