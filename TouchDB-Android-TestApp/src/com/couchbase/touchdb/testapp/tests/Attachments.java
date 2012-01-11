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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import junit.framework.Assert;
import android.test.AndroidTestCase;

import com.couchbase.touchdb.TDAttachment;
import com.couchbase.touchdb.TDBlobKey;
import com.couchbase.touchdb.TDBlobStore;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDStatus;

public class Attachments extends AndroidTestCase {

    public static final String TAG = "Attachments";

    public void testAttachments() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");
        TDBlobStore attachments = db.getAttachments();

        Assert.assertEquals(0, attachments.count());
        Assert.assertEquals(new HashSet<Object>(), attachments.allKeys());

        TDStatus status = new TDStatus();
        Map<String,Object> rev1Properties = new HashMap<String,Object>();
        rev1Properties.put("foo", 1);
        rev1Properties.put("bar", false);
        TDRevision rev1 = db.putRevision(new TDRevision(rev1Properties), null, status);

        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        byte[] attach1 = "This is the body of attach1".getBytes();
        boolean result = db.insertAttachmentForSequenceWithNameAndType(attach1, rev1.getSequence(), "attach", "text/plain");
        Assert.assertTrue(result);

        TDAttachment attachment = db.getAttachmentForSequence(rev1.getSequence(), "attach", status);
        Assert.assertEquals(TDStatus.OK, status.getCode());
        Assert.assertEquals("text/plain", attachment.getContentType());
        Assert.assertTrue(Arrays.equals(attach1, attachment.getData()));

        Map<String,Object> innerDict = new HashMap<String,Object>();
        innerDict.put("content_type", "text/plain");
        innerDict.put("digest", "sha1-gOHUOBmIMoDCrMuGyaLWzf1hQTE=");
        innerDict.put("length", 27);
        innerDict.put("stub", true);
        Map<String,Object> attachmentDict = new HashMap<String,Object>();
        attachmentDict.put("attach", innerDict);

        Map<String,Object> attachmentDictForSequence = db.getAttachmentsDictForSequenceWithContent(rev1.getSequence(), false);
        Assert.assertEquals(attachmentDict, attachmentDictForSequence);

        TDRevision gotRev1 = db.getDocumentWithIDAndRev(rev1.getDocId(), rev1.getRevId());
        @SuppressWarnings("unchecked")
        Map<String,Object> gotAttachmentDict = (Map<String,Object>)gotRev1.getProperties().get("_attachments");
        Assert.assertEquals(attachmentDict, gotAttachmentDict);


        // Add a second revision that doesn't update the attachment:
        Map<String,Object> rev2Properties = new HashMap<String,Object>();
        rev2Properties.put("_id", rev1.getDocId());
        rev2Properties.put("foo", 2);
        rev2Properties.put("bazz", false);
        TDRevision rev2 = db.putRevision(new TDRevision(rev2Properties), rev1.getRevId(), status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        status = db.copyAttachmentNamedFromSequenceToSequence("attach", rev1.getSequence(), rev2.getSequence());
        Assert.assertEquals(TDStatus.OK, status.getCode());

        // Add a third revision of the same document:
        Map<String,Object> rev3Properties = new HashMap<String,Object>();
        rev3Properties.put("_id", rev2.getDocId());
        rev3Properties.put("foo", 2);
        rev3Properties.put("bazz", false);
        TDRevision rev3 = db.putRevision(new TDRevision(rev3Properties), rev2.getRevId(), status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        byte[] attach2 = "<html>And this is attach2</html>".getBytes();
        result = db.insertAttachmentForSequenceWithNameAndType(attach2, rev3.getSequence(), "attach", "text/html");
        Assert.assertTrue(result);

        // Check the 2nd revision's attachment:
        TDAttachment attachment2 = db.getAttachmentForSequence(rev2.getSequence(), "attach", status);
        Assert.assertEquals(TDStatus.OK, status.getCode());
        Assert.assertEquals("text/plain", attachment2.getContentType());
        Assert.assertTrue(Arrays.equals(attach1, attachment2.getData()));

        // Check the 3rd revision's attachment:
        TDAttachment attachment3 = db.getAttachmentForSequence(rev3.getSequence(), "attach", status);
        Assert.assertEquals(TDStatus.OK, status.getCode());
        Assert.assertEquals("text/html", attachment3.getContentType());
        Assert.assertTrue(Arrays.equals(attach2, attachment3.getData()));

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
    }

}
