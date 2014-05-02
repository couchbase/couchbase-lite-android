package com.couchbase.lite;

import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;

import java.io.InputStream;

public class BlobStoreWriterTest extends LiteTestCase {

    public void testBasicOperation() throws Exception {

        BlobStore attachments = database.getAttachments();

        InputStream attachmentStream = getAsset("attachment.png");
        byte[] bytes = IOUtils.toByteArray(attachmentStream);

        BlobStoreWriter blobStoreWriter = new BlobStoreWriter(attachments);
        blobStoreWriter.appendData(bytes);
        blobStoreWriter.finish();
        blobStoreWriter.install();

        String sha1DigestKey = blobStoreWriter.sHA1DigestString();

        assertTrue(sha1DigestKey.contains("LmsoqJJ6LOn4YS60pYnvrKbBd64="));

        BlobKey keyFromSha1 = new BlobKey(sha1DigestKey);
        Assert.assertTrue(attachments.getSizeOfBlob(keyFromSha1) == bytes.length);

    }

    public void testBlobStoreWriterForBody() throws Exception {

        BlobStore attachments = database.getAttachments();

        InputStream attachmentStream = getAsset("attachment.png");

        BlobStoreWriter blobStoreWriter = Attachment.blobStoreWriterForBody(attachmentStream, database);

        String sha1DigestKey = blobStoreWriter.sHA1DigestString();

        assertTrue(sha1DigestKey.contains("LmsoqJJ6LOn4YS60pYnvrKbBd64="));




    }

}
