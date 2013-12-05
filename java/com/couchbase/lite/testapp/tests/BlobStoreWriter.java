package com.couchbase.lite.testapp.tests;

import com.couchbase.lite.CBLBlobKey;
import com.couchbase.lite.CBLBlobStore;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;

import java.io.InputStream;

public class BlobStoreWriter extends CBLiteTestCase {

    public void testBasicOperation() throws Exception {

        CBLBlobStore attachments = database.getAttachments();

        InputStream attachmentStream = getInstrumentation().getContext().getAssets().open("attachment.png");
        byte[] bytes = IOUtils.toByteArray(attachmentStream);

        com.couchbase.lite.BlobStoreWriter blobStoreWriter = new com.couchbase.lite.BlobStoreWriter(attachments);
        blobStoreWriter.appendData(bytes);
        blobStoreWriter.finish();
        blobStoreWriter.install();

        String sha1DigestKey = blobStoreWriter.sHA1DigestString();

        CBLBlobKey keyFromSha1 = new CBLBlobKey(sha1DigestKey);
        Assert.assertTrue(attachments.getSizeOfBlob(keyFromSha1) == bytes.length);

    }

}
