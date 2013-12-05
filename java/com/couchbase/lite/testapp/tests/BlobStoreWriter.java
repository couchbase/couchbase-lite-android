package com.couchbase.lite.testapp.tests;

import com.couchbase.lite.BlobKey;
import com.couchbase.lite.BlobStore;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;

import java.io.InputStream;

public class BlobStoreWriter extends CBLiteTestCase {

    public void testBasicOperation() throws Exception {

        BlobStore attachments = database.getAttachments();

        InputStream attachmentStream = getInstrumentation().getContext().getAssets().open("attachment.png");
        byte[] bytes = IOUtils.toByteArray(attachmentStream);

        com.couchbase.lite.BlobStoreWriter blobStoreWriter = new com.couchbase.lite.BlobStoreWriter(attachments);
        blobStoreWriter.appendData(bytes);
        blobStoreWriter.finish();
        blobStoreWriter.install();

        String sha1DigestKey = blobStoreWriter.sHA1DigestString();

        BlobKey keyFromSha1 = new BlobKey(sha1DigestKey);
        Assert.assertTrue(attachments.getSizeOfBlob(keyFromSha1) == bytes.length);

    }

}
