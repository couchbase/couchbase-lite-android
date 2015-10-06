/**
 * Created by Pasin Suriyentrakorn on 9/3/15.
 * <p/>
 * Copyright (c) 2015 Couchbase, Inc All rights reserved.
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
 *
 */

package com.couchbase.lite;

import com.couchbase.lite.support.FileDirUtils;
import com.couchbase.lite.support.security.SymmetricKey;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.TextUtils;

import junit.framework.Assert;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class BlobStoreTest extends LiteTestCaseWithDB {
    private boolean encrypt;
    private File storeFile;
    private BlobStore store;

    @Override
    public void runBare() throws Throwable {
        encrypt = false;
        super.runBare();

        encrypt = true;
        super.runBare();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Create blob store:
        File storeFile = new File(manager.getDirectory(), "BlobStoreTest");
        FileDirUtils.deleteRecursive(storeFile);
        Assert.assertFalse(storeFile.exists());

        if (encrypt)
            Log.i(TAG, "---- Now enabling attachment encryption ----");
        store = new BlobStore(manager.getContext(),
                storeFile.getPath(), (encrypt ? new SymmetricKey() : null), true);

        // If using encryption, the encryption marker must exist:
        File enMarkerFile = new File(storeFile, BlobStore.ENCRYPTION_MARKER_FILENAME);
        boolean markerExists = enMarkerFile.exists();
        Assert.assertEquals(encrypt, markerExists);
    }

    @Override
    protected void tearDown() throws Exception {
        store = null;
        if (storeFile != null)
            FileDirUtils.deleteRecursive(storeFile);
        super.tearDown();
    }

    private void verifyRawBlob(BlobKey key, byte[] clearText) throws IOException {
        InputStream is = null;
        try {
            String path = store.getRawPathForKey(key);
            is = new FileInputStream(path);
            byte[] raw = TextUtils.read(is);
            Assert.assertNotNull(raw);
            if (store.getEncryptionKey() == null) {
                Assert.assertTrue(Arrays.equals(raw, clearText));
            } else {
                Assert.assertTrue(!Arrays.equals(raw, clearText));
            }
        } finally {
            if (is != null)
                is.close();
        }
    }

    public void testBasic() throws Exception {
        if (!isSQLiteDB()) return;

        byte[] item = "this is an item".getBytes("UTF8");
        BlobKey key = new BlobKey();
        BlobKey key2 = new BlobKey();
        store.storeBlob(item, key);
        store.storeBlob(item, key2);
        Assert.assertTrue(Arrays.equals(key.getBytes(), key2.getBytes()));

        byte[] readItem = store.blobForKey(key);
        Assert.assertTrue(Arrays.equals(readItem, item));
        verifyRawBlob(key, item);

        String path = store.getBlobPathForKey(key);
        Assert.assertEquals((path == null), encrypt); // path is returned if not encrypted
    }

    public void testReopen() throws Exception {
        if (!isSQLiteDB()) return;

        byte[] item = "this is an item".getBytes("UTF8");
        BlobKey key = new BlobKey();
        Assert.assertTrue(store.storeBlob(item, key));

        BlobStore store2 = new BlobStore(manager.getContext(),
                store.getPath(), store.getEncryptionKey(), true);
        Assert.assertNotNull("Couldn't re-open store", store2);

        byte[] readItem = store2.blobForKey(key);
        Assert.assertTrue(Arrays.equals(readItem, item));

        readItem = store.blobForKey(key);
        Assert.assertTrue(Arrays.equals(readItem, item));
        verifyRawBlob(key, item);
    }

    public void testBlobStoreWriter() throws Exception {
        if (!isSQLiteDB()) return;

        BlobStoreWriter writer = new BlobStoreWriter(store);
        Assert.assertNotNull(writer);

        writer.appendData("part 1, ".getBytes("UTF8"));
        writer.appendData("part 2, ".getBytes("UTF8"));
        writer.appendData("part 3".getBytes("UTF8"));
        writer.finish();
        Assert.assertTrue(writer.install());

        byte[] expectedData = "part 1, part 2, part 3".getBytes("UTF8");
        byte[] readItem = store.blobForKey(writer.getBlobKey());
        Assert.assertTrue(Arrays.equals(readItem, expectedData));
        verifyRawBlob(writer.getBlobKey(), expectedData);
    }

    public void testBlobStoreWriterBasicOperation() throws Exception {
        if (!isSQLiteDB()) return;
        if (encrypt) return; // Not involve encryption test

        BlobStore attachments = database.getAttachmentStore();

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
        if (!isSQLiteDB()) return;
        if (encrypt) return; // Not involve encryption test

        InputStream attachmentStream = getAsset("attachment.png");
        BlobStoreWriter blobStoreWriter =
                Attachment.blobStoreWriterForBody(attachmentStream, database);
        String sha1DigestKey = blobStoreWriter.sHA1DigestString();
        assertTrue(sha1DigestKey.contains("LmsoqJJ6LOn4YS60pYnvrKbBd64="));
    }
}
