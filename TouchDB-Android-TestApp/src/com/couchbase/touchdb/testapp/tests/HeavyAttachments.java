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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;

import android.content.Intent;
import android.util.Log;

import com.couchbase.touchdb.TDAttachment;
import com.couchbase.touchdb.TDBlobKey;
import com.couchbase.touchdb.TDBlobStore;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDStatus;
import com.couchbase.touchdb.replicator.TDPusher;
import com.couchbase.touchdb.replicator.TDReplicator;
import com.couchbase.touchdb.support.Base64;
import com.couchbase.touchdb.testapp.CopySampleAttachmentsActivity;

public class HeavyAttachments extends TouchDBTestCase {

  public static final String TAG = "Attachments";

  @SuppressWarnings("unchecked")
  public void testRetrieveAttachments() throws Exception {
    boolean heavyAttachments = false;
    boolean pushAttachments = false;
    buildAttachments(heavyAttachments, pushAttachments);
  }

  @SuppressWarnings("unchecked")
  public void testPushAttachments() throws Exception {
    boolean heavyAttachments = false;
    boolean pushAttachments = true;
    buildAttachments(heavyAttachments, pushAttachments);
  }

  @SuppressWarnings("unchecked")
  public void disableTestRetrieveHeavyAttachments() throws Exception {
    boolean heavyAttachments = true;
    boolean pushAttachments = false;
    buildAttachments(heavyAttachments, pushAttachments);
  }

  @SuppressWarnings("unchecked")
  public void disableTestPushHeavyAttachments() throws Exception {
    boolean heavyAttachments = true;
    boolean pushAttachments = true;
    buildAttachments(heavyAttachments, pushAttachments);
  }

  @SuppressWarnings("unchecked")
  public void buildAttachments(boolean heavyAttachments, boolean pushAttachments)
      throws Exception {

    TDBlobStore attachments = database.getAttachments();

    Assert.assertEquals(0, attachments.count());
    Assert.assertEquals(new HashSet<Object>(), attachments.allKeys());

    TDStatus status = new TDStatus();
    Map<String, Object> rev1Properties = new HashMap<String, Object>();
    rev1Properties.put("foo", 1);
    rev1Properties.put("bar", false);
    TDRevision rev1 = database.putRevision(new TDRevision(rev1Properties),
        null, false, status);

    Assert.assertEquals(TDStatus.CREATED, status.getCode());

    byte[] attach1 = "This is the body of attach1".getBytes();
    status = database.insertAttachmentForSequenceWithNameAndType(
        new ByteArrayInputStream(attach1), rev1.getSequence(), "attach",
        "text/plain", rev1.getGeneration());
    Assert.assertEquals(TDStatus.CREATED, status.getCode());

    TDAttachment attachment = database.getAttachmentForSequence(
        rev1.getSequence(), "attach", status);
    Assert.assertEquals(TDStatus.OK, status.getCode());
    Assert.assertEquals("text/plain", attachment.getContentType());
    byte[] data = IOUtils.toByteArray(attachment.getContentStream());
    Assert.assertTrue(Arrays.equals(attach1, data));

    Map<String, Object> innerDict = new HashMap<String, Object>();
    innerDict.put("content_type", "text/plain");
    innerDict.put("digest", "sha1-gOHUOBmIMoDCrMuGyaLWzf1hQTE=");
    innerDict.put("length", 27);
    innerDict.put("stub", true);
    innerDict.put("revpos", 1);
    Map<String, Object> attachmentDict = new HashMap<String, Object>();
    attachmentDict.put("attach", innerDict);

    Map<String, Object> attachmentDictForSequence = database
        .getAttachmentsDictForSequenceWithContent(rev1.getSequence(), false);
    Assert.assertEquals(attachmentDict, attachmentDictForSequence);

    TDRevision gotRev1 = database.getDocumentWithIDAndRev(rev1.getDocId(),
        rev1.getRevId(), EnumSet.noneOf(TDDatabase.TDContentOptions.class));
    Map<String, Object> gotAttachmentDict = (Map<String, Object>) gotRev1
        .getProperties().get("_attachments");
    Assert.assertEquals(attachmentDict, gotAttachmentDict);

    // Check the attachment dict, with attachments included:
    innerDict.remove("stub");
    innerDict.put("data", Base64.encodeBytes(attach1));
    attachmentDictForSequence = database
        .getAttachmentsDictForSequenceWithContent(rev1.getSequence(), true);
    Assert.assertEquals(attachmentDict, attachmentDictForSequence);

    gotRev1 = database.getDocumentWithIDAndRev(rev1.getDocId(),
        rev1.getRevId(),
        EnumSet.of(TDDatabase.TDContentOptions.TDIncludeAttachments));
    gotAttachmentDict = (Map<String, Object>) gotRev1.getProperties().get(
        "_attachments");
    Assert.assertEquals(attachmentDict, gotAttachmentDict);

    // Add a second revision that doesn't update the attachment:
    Map<String, Object> rev2Properties = new HashMap<String, Object>();
    rev2Properties.put("_id", rev1.getDocId());
    rev2Properties.put("foo", 2);
    rev2Properties.put("bazz", false);
    TDRevision rev2 = database.putRevision(new TDRevision(rev2Properties),
        rev1.getRevId(), false, status);
    Assert.assertEquals(TDStatus.CREATED, status.getCode());

    status = database.copyAttachmentNamedFromSequenceToSequence("attach",
        rev1.getSequence(), rev2.getSequence());
    Assert.assertEquals(TDStatus.OK, status.getCode());

    // Add a third revision of the same document:
    Map<String, Object> rev3Properties = new HashMap<String, Object>();
    rev3Properties.put("_id", rev2.getDocId());
    rev3Properties.put("foo", 2);
    rev3Properties.put("bazz", false);
    TDRevision rev3 = database.putRevision(new TDRevision(rev3Properties),
        rev2.getRevId(), false, status);
    Assert.assertEquals(TDStatus.CREATED, status.getCode());

    byte[] attach2 = "<html>And this is attach2</html>".getBytes();
    status = database.insertAttachmentForSequenceWithNameAndType(
        new ByteArrayInputStream(attach2), rev3.getSequence(), "attach",
        "text/html", rev2.getGeneration());
    Assert.assertEquals(TDStatus.CREATED, status.getCode());

    // Check the 2nd revision's attachment:
    TDAttachment attachment2 = database.getAttachmentForSequence(
        rev2.getSequence(), "attach", status);
    Assert.assertEquals(TDStatus.OK, status.getCode());
    Assert.assertEquals("text/plain", attachment2.getContentType());
    data = IOUtils.toByteArray(attachment2.getContentStream());
    Assert.assertTrue(Arrays.equals(attach1, data));

    // Check the 3rd revision's attachment:
    TDAttachment attachment3 = database.getAttachmentForSequence(
        rev3.getSequence(), "attach", status);
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

    status = database.compact(); // This clears the body of the first revision
    Assert.assertEquals(TDStatus.OK, status.getCode());
    Assert.assertEquals(1, attachments.count());

    Set<TDBlobKey> expected2 = new HashSet<TDBlobKey>();
    expected2.add(TDBlobStore.keyForBlob(attach2));
    Assert.assertEquals(expected2, attachments.allKeys());
    TDRevision lastRev = rev3;

    if (heavyAttachments) {
      int numberOfImagesOkayOn512Ram = 4;
      int numberOfImagesOkayOn768Ram = 6;
      int numberOfImagesOkayOn1024Ram = 8;
      
      lastRev = attachImages(numberOfImagesOkayOn1024Ram, rev3);
      /* query the db for that doc */
      TDRevision largerRev = database.getDocumentWithIDAndRev(
          lastRev.getDocId(), lastRev.getRevId(),
          EnumSet.noneOf(TDDatabase.TDContentOptions.class));
      attachmentDict = (Map<String, Object>) largerRev.getProperties().get(
          "_attachments");
      Assert.assertNotNull(attachmentDict);
    }
    if (pushAttachments) {
      URL remote = getReplicationURL();
      deleteRemoteDB(remote);
      final TDReplicator repl = database.getReplicator(remote, true, false, server.getWorkExecutor());
      ((TDPusher) repl).setCreateTarget(true);
      try {
        runTestOnUiThread(new Runnable() {

          @Override
          public void run() {
            // Push them to the remote:
            repl.start();
            Assert.assertTrue(repl.isRunning());
          }
        });
      } catch (Throwable e) {
        e.printStackTrace();
      }

      while (repl.isRunning()) {
        Log.i(TAG, "Waiting for replicator to finish");
        Thread.sleep(1000);
      }
      
      /* Ensure that the last version of the doc is the last thing replicated. */
      Assert.assertTrue(lastRev.getRevId().startsWith(repl.getLastSequence()));

    }

  }

  /**
   * Helper function to attach (many) images to a document to test heavy docs
   * with large attachments or multiple attachments
   * 
   * 
   * @param howMany
   *          how many images we want to add (default is two or more)
   * @param startingRev
   *          The TDRevision we want to add to
   */
  public TDRevision attachImages(int howMany, TDRevision startingRev) {
    /*
     * Add two or more image attachments to the documents, these attachments are
     * copied by the CopySampleAttachmentsActivity.
     */
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
      TDStatus status = database.insertAttachmentForSequenceWithNameAndType(
          new ByteArrayInputStream(imageAttachment), startingRev.getSequence(),
          "sample_attachment_image1.jpg", "image/jpeg",
          startingRev.getGeneration());
      Assert.assertEquals(TDStatus.CREATED, status.getCode());

      fileStream2 = new FileInputStream(
          "/data/data/com.couchbase.touchdb.testapp/files/sample_attachment_image2.jpg");
      byte[] secondImageAttachment = IOUtils.toByteArray(fileStream2);
      if (secondImageAttachment.length == 0) {
        sampleFilesExistAndWereCopied = false;
      }
      status = database.insertAttachmentForSequenceWithNameAndType(
          new ByteArrayInputStream(secondImageAttachment),
          startingRev.getSequence(), "sample_attachment_image2.jpg",
          "image/jpeg", startingRev.getGeneration());
      Assert.assertEquals(TDStatus.CREATED, status.getCode());

      int howManyMoreToAdd = howMany - 2;
      if (howManyMoreToAdd < 1) {
        howManyMoreToAdd = 0;
      }
      for (int i = 0; i < howManyMoreToAdd; i++) {
        status = database.insertAttachmentForSequenceWithNameAndType(
            new ByteArrayInputStream(secondImageAttachment),
            startingRev.getSequence(),
            "sample_attachment_image_test_out_of_memory" + i + ".jpg",
            "image/jpeg", startingRev.getGeneration());
        Assert.assertEquals(TDStatus.CREATED, status.getCode());
        int imagesSoFar = i + 2;
        Log.d(TAG, "Attached " + imagesSoFar + "images.");
        if (status.getCode() != TDStatus.CREATED) {
          break;
        }
      }

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
          "The sample image files for testing multipart attachment upload weren't copied to the SDCARD, ");
    }
    Assert.assertTrue(sampleFilesExistAndWereCopied);
    return startingRev;
  }
}
