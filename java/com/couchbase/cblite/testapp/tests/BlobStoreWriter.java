package com.couchbase.cblite.testapp.tests;

import com.couchbase.cblite.CBLBlobKey;
import com.couchbase.cblite.CBLBlobStore;
import com.couchbase.cblite.CBLBlobStoreWriter;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;

import java.io.InputStream;

public class BlobStoreWriter extends CBLiteTestCase {

    public void testThatItworks() throws Exception {

        CBLBlobStore attachments = database.getAttachments();

        InputStream attachmentStream = getInstrumentation().getContext().getAssets().open("attachment.png");
        byte[] bytes = IOUtils.toByteArray(attachmentStream);

        CBLBlobStoreWriter blobStoreWriter = new CBLBlobStoreWriter(attachments);
        blobStoreWriter.appendData(bytes);
        blobStoreWriter.finish();
        blobStoreWriter.install();

        String sha1DigestKey = blobStoreWriter.sHA1DigestString();
        String md5DigestKey = blobStoreWriter.mD5DigestString();

        CBLBlobKey keyFromSha1 = new CBLBlobKey(sha1DigestKey);
        Assert.assertTrue(attachments.getSizeOfBlob(keyFromSha1) == bytes.length);

        CBLBlobKey keyFromMd5 = new CBLBlobKey(md5DigestKey);
        Assert.assertTrue(attachments.getSizeOfBlob(keyFromMd5) == bytes.length);

    }

}
