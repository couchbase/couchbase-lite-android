/**
 * Created by Pasin Suriyentrakorn on 8/28/15.
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

import com.couchbase.lite.support.security.SymmetricKey;
import com.couchbase.lite.util.ArrayUtils;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.TextUtils;

import junit.framework.Assert;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DatabaseEncryptionTest extends LiteTestCaseWithDB {
    private static final String TEST_DIR = "encryption";
    private static final String NULL_PASSWORD = null;
    private Manager cryptoManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        ManagerOptions options = new ManagerOptions();
        options.setEnableStorageEncryption(true);
        cryptoManager = new Manager(getTestContext(TEST_DIR, true), options);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        if (cryptoManager != null)
            cryptoManager.close();
    }

    public void testSymmetricKey() throws Exception {
        if (!isEncryptionTestEnabled())
            return;

        Database database = cryptoManager.getDatabase("seekrit");

        long start = System.currentTimeMillis();
        SymmetricKey key = database.createSymmetricKey("letmein123456");
        long end = System.currentTimeMillis();
        Log.i(TAG, "Finished getting a symmetric key in " + (end - start) + " msec.");
        byte[] keyData = key.getKey();
        Log.i(TAG, "Key = " + key);

        // Encrypt using the key:
        byte[] clearText = "This is the clear text.".getBytes();
        byte[] ciphertext = key.encryptData(clearText);
        Log.i(TAG, "Encrypted = " + new String(ciphertext));
        Assert.assertNotNull(ciphertext);

        // Decrypt using the key:
        byte[] decrypted = key.decryptData(ciphertext);
        Log.i(TAG, "Decrypted String = " + new String(decrypted));
        Assert.assertTrue(Arrays.equals(clearText, decrypted));

        // Incremental encryption:
        start = System.currentTimeMillis();
        SymmetricKey.Encryptor encryptor = key.createEncryptor();
        byte[] incrementalClearText = new byte[0];
        byte[] incrementalCiphertext = new byte[0];
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 55; i++) {
            byte[] data = new byte[555];
            random.nextBytes(data);
            byte[] cipherData = encryptor.encrypt(data);
            incrementalClearText = ArrayUtils.concat(incrementalClearText, data);
            incrementalCiphertext = ArrayUtils.concat(incrementalCiphertext, cipherData);
        }
        incrementalCiphertext = ArrayUtils.concat(incrementalCiphertext, encryptor.encrypt(null));
        decrypted = key.decryptData(incrementalCiphertext);
        Assert.assertTrue(Arrays.equals(incrementalClearText, decrypted));
        end = System.currentTimeMillis();
        Log.i(TAG, "Finished incremental encryption test in " + (end - start) + " msec.");
    }

    public void testCreateRandomSymmetricKey() throws Exception {
        if (!isEncryptionTestEnabled())
            return;

        long start = System.currentTimeMillis();
        SymmetricKey key = new SymmetricKey();
        long end = System.currentTimeMillis();
        Log.i(TAG, "Finished creating a random symmetric key in " + (end - start) + " msec.");
        byte[] keyData = key.getKey();
        Assert.assertNotNull(keyData);
        Assert.assertEquals(32, keyData.length);
        Log.i(TAG, "Key = " + key);
    }

    public void testEncryptionFailsGracefully() throws Exception {
        if (!isSQLiteDB())
            return;

        // Disable the test for couchbase-lite-java as couchbase-lite-java has SQLCipher by default:
        if (!isAndriod())
            return;

        if (isEncryptionTestEnabled())
            return;

        manager.registerEncryptionKey("123456", "seekrit");
        Database seekrit = null;
        CouchbaseLiteException error = null;
        try {
            seekrit = null;
            seekrit = manager.getDatabase("seekrit");
        } catch (CouchbaseLiteException e) {
            error = e;
        }
        Assert.assertNotNull(error);
        Assert.assertEquals(501, error.getCBLStatus().getCode());
        Assert.assertNull(seekrit);
    }

    public void testUnEncryptedDB() throws Exception {
        if (!isEncryptionTestEnabled())
            return;

        // Create unencrypted DB:
        Database seekrit = cryptoManager.getDatabase("seekrit");
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("answer", "42");
        createDocumentWithProperties(seekrit, properties);
        Assert.assertTrue(seekrit.close());

        // Try to reopen with password (fails):
        cryptoManager.registerEncryptionKey("wrong", "seekrit");
        CouchbaseLiteException error = null;
        try {
            seekrit = null;
            seekrit = cryptoManager.getDatabase("seekrit");
        } catch (CouchbaseLiteException e) {
            error = e;
        }
        Assert.assertNotNull(error);
        Assert.assertEquals(401, error.getCBLStatus().getCode());
        Assert.assertNull(seekrit);

        // Reopen with no password:
        cryptoManager.registerEncryptionKey(NULL_PASSWORD, "seekrit");
        seekrit = cryptoManager.getDatabase("seekrit");
        Assert.assertNotNull(seekrit);
    }

    public void testEncryptedDB() throws Exception {
        if (!isEncryptionTestEnabled())
            return;

        // Create encrypted DB:
        cryptoManager.registerEncryptionKey("123456", "seekrit");
        Database seekrit = cryptoManager.getDatabase("seekrit");
        Assert.assertNotNull(seekrit);
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("answer", "42");
        createDocumentWithProperties(seekrit, properties);
        Assert.assertTrue(seekrit.close());

        // Try to reopen without the password (fails):
        cryptoManager.registerEncryptionKey(NULL_PASSWORD, "seekrit");
        CouchbaseLiteException error = null;
        try {
            seekrit = null;
            seekrit = cryptoManager.getDatabase("seekrit");
        } catch (CouchbaseLiteException e) {
            error = e;
        }
        Assert.assertNotNull(error);
        Assert.assertEquals(401, error.getCBLStatus().getCode());
        Assert.assertNull(seekrit);

        // Reopen with correct password:
        cryptoManager.registerEncryptionKey("123456", "seekrit");
        seekrit = cryptoManager.getDatabase("seekrit");
        Assert.assertNotNull(seekrit);
        Assert.assertEquals(1, seekrit.getDocumentCount());
        Assert.assertTrue(seekrit.close());
    }

    public void testDeleteEcryptedDB() throws Exception {
        if (!isEncryptionTestEnabled())
            return;

        // Create encrypted DB:
        cryptoManager.registerEncryptionKey("letmein", "seekrit");
        Database seekrit = cryptoManager.getDatabase("seekrit");
        Assert.assertNotNull(seekrit);
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("answer", "42");
        createDocumentWithProperties(seekrit, properties);

        // Delete db; this also unregisters its password:
        seekrit.delete();

        // Re-create database:
        seekrit = cryptoManager.getDatabase("seekrit");
        Assert.assertNotNull(seekrit);
        Assert.assertEquals(0, seekrit.getDocumentCount());
        Assert.assertTrue(seekrit.close());

        // Make sure it doesn't need a password now:
        cryptoManager.registerEncryptionKey(NULL_PASSWORD, "seekrit");
        seekrit = cryptoManager.getDatabase("seekrit");
        Assert.assertNotNull(seekrit);
        Assert.assertEquals(0, seekrit.getDocumentCount());
        Assert.assertTrue(seekrit.close());

        // Make sure old password doesn't work:
        cryptoManager.registerEncryptionKey("letmein", "seekrit");
        CouchbaseLiteException error = null;
        try {
            seekrit = null;
            seekrit = cryptoManager.getDatabase("seekrit");
        } catch (CouchbaseLiteException e) {
            error = e;
        }
        Assert.assertNotNull(error);
        Assert.assertEquals(401, error.getCBLStatus().getCode());
        Assert.assertNull(seekrit);
    }

    public void testCompactEncryptedDB() throws Exception {
        if (!isEncryptionTestEnabled())
            return;

        // Create encrypted DB:
        cryptoManager.registerEncryptionKey("letmein", "seekrit");
        Database seekrit = cryptoManager.getDatabase("seekrit");
        Assert.assertNotNull(seekrit);

        // Create a doc and then update it:
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("answer", "42");
        Document doc = seekrit.createDocument();
        doc.putProperties(properties);

        doc.update(new Document.DocumentUpdater() {
            @Override
            public boolean update(UnsavedRevision rev) {
                Map<String, Object> properties = rev.getProperties();
                properties.put("foo", "84");
                rev.setProperties(properties);
                return true;
            }
        });

        // Compact:
        seekrit.compact();

        // Update the document:
        doc.update(new Document.DocumentUpdater() {
            @Override
            public boolean update(UnsavedRevision rev) {
                Map<String, Object> properties = rev.getProperties();
                properties.put("foo", "85");
                rev.setProperties(properties);
                return true;
            }
        });

        // Close and then reopen the database:
        Assert.assertTrue(seekrit.close());
        // Reregister the encryption key:
        cryptoManager.registerEncryptionKey("letmein", "seekrit");
        // Reopen the database:
        seekrit = cryptoManager.getDatabase("seekrit");
        Assert.assertNotNull(seekrit);
        Assert.assertEquals(1, seekrit.getDocumentCount());
    }

    public void testEncryptedAttachments() throws Exception {
        if (!isEncryptionTestEnabled())
            return;

        cryptoManager.registerEncryptionKey("letmein", "seekrit");
        Database seekrit = cryptoManager.getDatabase("seekrit");
        Assert.assertNotNull(seekrit);

        // Save a doc with an attachment:
        Document doc = seekrit.getDocument("att");
        byte[] body = "This is a test attachment!".getBytes();
        ByteArrayInputStream bis = new ByteArrayInputStream(body);
        UnsavedRevision rev = doc.createRevision();
        rev.setAttachment("att.txt", "text/plain; charset=utf-8", bis);
        SavedRevision savedRev = rev.save();
        Assert.assertNotNull(savedRev);

        // Read the raw attachment file and make sure it's not clear text:
        Map<String, Object> atts = (Map<String, Object>)savedRev.getProperties().get("_attachments");
        Map<String, Object> att = (Map<String, Object>)atts.get("att.txt");
        String digest = (String) att.get("digest");
        Assert.assertNotNull(digest);

        BlobKey key = new BlobKey(digest);
        String path = seekrit.getAttachmentStore().getRawPathForKey(key);
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(path);
            byte[] raw = TextUtils.read(fis);
            Assert.assertNotNull(raw);
            Assert.assertTrue(!Arrays.equals(raw, body));
        } finally {
            if (fis != null)
                fis.close();
        }
    }

    public void testRekey() throws Exception {
        if (!isEncryptionTestEnabled())
            return;

        // First run the encrypted-attachments test to populate the db:
        testEncryptedAttachments();
        
        Database seekrit = cryptoManager.getDatabase("seekrit");
        seekrit.changeEncryptionKey("letmeout");

        // Close & reopen seekrit:
        String dbName = seekrit.getName();
        Assert.assertTrue(seekrit.close());
        seekrit = null;

        cryptoManager.registerEncryptionKey("letmeout", "seekrit");
        Database seekrit2 = cryptoManager.getDatabase("seekrit");
        Assert.assertNotNull(seekrit2);
        seekrit = seekrit2;

        // Check the document and its attachment:
        SavedRevision savedRev = seekrit.getDocument("att").getCurrentRevision();
        Assert.assertNotNull(savedRev);

        Attachment att = savedRev.getAttachment("att.txt");
        Assert.assertNotNull(att);
        byte[] body = "This is a test attachment!".getBytes();
        byte[] rawAtt = TextUtils.read(att.getContent());
        Assert.assertTrue(Arrays.equals(rawAtt, body));
    }
}
