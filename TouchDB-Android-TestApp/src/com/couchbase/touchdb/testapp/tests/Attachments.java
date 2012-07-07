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

package com.couchbase.touchdb.testapp.tests;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;

import android.test.AndroidTestCase;

import com.couchbase.touchdb.TDAttachment;
import com.couchbase.touchdb.TDBlobKey;
import com.couchbase.touchdb.TDBlobStore;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDStatus;
import com.couchbase.touchdb.support.Base64;

public class Attachments extends AndroidTestCase {

    public static final String TAG = "Attachments";

    @SuppressWarnings("unchecked")
    public void testAttachments() throws Exception {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");
        TDBlobStore attachments = db.getAttachments();

        Assert.assertEquals(0, attachments.count());
        Assert.assertEquals(new HashSet<Object>(), attachments.allKeys());

        TDStatus status = new TDStatus();
        Map<String,Object> rev1Properties = new HashMap<String,Object>();
        rev1Properties.put("foo", 1);
        rev1Properties.put("bar", false);
        TDRevision rev1 = db.putRevision(new TDRevision(rev1Properties), null, false, status);

        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        byte[] attach1 = "This is the body of attach1".getBytes();
        status = db.insertAttachmentForSequenceWithNameAndType(new ByteArrayInputStream(attach1), rev1.getSequence(), "attach", "text/plain", rev1.getGeneration());
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        TDAttachment attachment = db.getAttachmentForSequence(rev1.getSequence(), "attach", status);
        Assert.assertEquals(TDStatus.OK, status.getCode());
        Assert.assertEquals("text/plain", attachment.getContentType());
        byte[] data = IOUtils.toByteArray(attachment.getContentStream());
        Assert.assertTrue(Arrays.equals(attach1, data));

        Map<String,Object> innerDict = new HashMap<String,Object>();
        innerDict.put("content_type", "text/plain");
        innerDict.put("digest", "sha1-gOHUOBmIMoDCrMuGyaLWzf1hQTE=");
        innerDict.put("length", 27);
        innerDict.put("stub", true);
        innerDict.put("revpos", 1);
        Map<String,Object> attachmentDict = new HashMap<String,Object>();
        attachmentDict.put("attach", innerDict);

        Map<String,Object> attachmentDictForSequence = db.getAttachmentsDictForSequenceWithContent(rev1.getSequence(), false);
        Assert.assertEquals(attachmentDict, attachmentDictForSequence);

        TDRevision gotRev1 = db.getDocumentWithIDAndRev(rev1.getDocId(), rev1.getRevId(), EnumSet.noneOf(TDDatabase.TDContentOptions.class));
        Map<String,Object> gotAttachmentDict = (Map<String,Object>)gotRev1.getProperties().get("_attachments");
        Assert.assertEquals(attachmentDict, gotAttachmentDict);

        // Check the attachment dict, with attachments included:
        innerDict.remove("stub");
        innerDict.put("data", Base64.encodeBytes(attach1));
        attachmentDictForSequence = db.getAttachmentsDictForSequenceWithContent(rev1.getSequence(), true);
        Assert.assertEquals(attachmentDict, attachmentDictForSequence);

        gotRev1 = db.getDocumentWithIDAndRev(rev1.getDocId(), rev1.getRevId(), EnumSet.of(TDDatabase.TDContentOptions.TDIncludeAttachments));
        gotAttachmentDict = (Map<String,Object>)gotRev1.getProperties().get("_attachments");
        Assert.assertEquals(attachmentDict, gotAttachmentDict);


        // Add a second revision that doesn't update the attachment:
        Map<String,Object> rev2Properties = new HashMap<String,Object>();
        rev2Properties.put("_id", rev1.getDocId());
        rev2Properties.put("foo", 2);
        rev2Properties.put("bazz", false);
        TDRevision rev2 = db.putRevision(new TDRevision(rev2Properties), rev1.getRevId(), false, status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        status = db.copyAttachmentNamedFromSequenceToSequence("attach", rev1.getSequence(), rev2.getSequence());
        Assert.assertEquals(TDStatus.OK, status.getCode());

        // Add a third revision of the same document:
        Map<String,Object> rev3Properties = new HashMap<String,Object>();
        rev3Properties.put("_id", rev2.getDocId());
        rev3Properties.put("foo", 2);
        rev3Properties.put("bazz", false);
        TDRevision rev3 = db.putRevision(new TDRevision(rev3Properties), rev2.getRevId(), false, status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        byte[] attach2 = "<html>And this is attach2</html>".getBytes();
        status = db.insertAttachmentForSequenceWithNameAndType(new ByteArrayInputStream(attach2), rev3.getSequence(), "attach", "text/html", rev2.getGeneration());
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        // Check the 2nd revision's attachment:
        TDAttachment attachment2 = db.getAttachmentForSequence(rev2.getSequence(), "attach", status);
        Assert.assertEquals(TDStatus.OK, status.getCode());
        Assert.assertEquals("text/plain", attachment2.getContentType());
        data = IOUtils.toByteArray(attachment2.getContentStream());
        Assert.assertTrue(Arrays.equals(attach1, data));

        // Check the 3rd revision's attachment:
        TDAttachment attachment3 = db.getAttachmentForSequence(rev3.getSequence(), "attach", status);
        Assert.assertEquals(TDStatus.OK, status.getCode());
        Assert.assertEquals("text/html", attachment3.getContentType());
        data = IOUtils.toByteArray(attachment3.getContentStream());
        Assert.assertTrue(Arrays.equals(attach2, data));

        // Examine the attachment store:
        Assert.assertEquals(2, attachments.count());
        Set<TDBlobKey> expected = new HashSet<TDBlobKey>();
        expected.add(TDBlobStore.keyForBlob(attach1));
        expected.add(TDBlobStore.keyForBlob(attach2));

        Assert.assertEquals(expected, attachments.allKeys());

        status = db.compact();  // This clears the body of the first revision
        Assert.assertEquals(TDStatus.OK, status.getCode());
        Assert.assertEquals(1, attachments.count());

        Set<TDBlobKey> expected2 = new HashSet<TDBlobKey>();
        expected2.add(TDBlobStore.keyForBlob(attach2));
        Assert.assertEquals(expected2, attachments.allKeys());

        db.close();
    }

    @SuppressWarnings("unchecked")
    public void testPutAttachment() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");
        TDBlobStore attachments = db.getAttachments();

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

        TDStatus status = new TDStatus();
        TDRevision rev1 = db.putRevision(new TDRevision(properties), null, false, status);

        Assert.assertEquals(TDStatus.CREATED, status.getCode());
        // Examine the attachment store:
        Assert.assertEquals(1, attachments.count());

        // Get the revision:
        TDRevision gotRev1 = db.getDocumentWithIDAndRev(rev1.getDocId(), rev1.getRevId(), EnumSet.noneOf(TDDatabase.TDContentOptions.class));
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
        db.updateAttachment("attach", new ByteArrayInputStream(attachv2), "application/foo", rev1.getDocId(), null, status);
        Assert.assertEquals(TDStatus.CONFLICT, status.getCode());
        db.updateAttachment("attach", new ByteArrayInputStream(attachv2), "application/foo", rev1.getDocId(), "1-bogus", status);
        Assert.assertEquals(TDStatus.CONFLICT, status.getCode());
        TDRevision rev2 = db.updateAttachment("attach", new ByteArrayInputStream(attachv2), "application/foo", rev1.getDocId(), rev1.getRevId(), status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());
        Assert.assertEquals(rev1.getDocId(), rev2.getDocId());
        Assert.assertEquals(2, rev2.getGeneration());

        // Get the updated revision:
        TDRevision gotRev2 = db.getDocumentWithIDAndRev(rev2.getDocId(), rev2.getRevId(), EnumSet.noneOf(TDDatabase.TDContentOptions.class));
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
        db.updateAttachment("nosuchattach", null, null, rev2.getDocId(), rev2.getRevId(), status);
        Assert.assertEquals(TDStatus.NOT_FOUND, status.getCode());

        db.updateAttachment("nosuchattach", null, null, "nosuchdoc", "nosuchrev", status);
        Assert.assertEquals(TDStatus.NOT_FOUND, status.getCode());

        TDRevision rev3 = db.updateAttachment("attach", null, null, rev2.getDocId(), rev2.getRevId(), status);
        Assert.assertEquals(TDStatus.OK, status.getCode());
        Assert.assertEquals(rev2.getDocId(), rev3.getDocId());
        Assert.assertEquals(3, rev3.getGeneration());

        // Get the updated revision:
        TDRevision gotRev3 = db.getDocumentWithIDAndRev(rev3.getDocId(), rev3.getRevId(), EnumSet.noneOf(TDDatabase.TDContentOptions.class));
        attachmentDict = (Map<String, Object>) gotRev3.getProperties().get("_attachments");
        Assert.assertNull(attachmentDict);

        db.close();
    }

}
