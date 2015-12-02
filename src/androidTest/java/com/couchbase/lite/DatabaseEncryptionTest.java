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

import com.couchbase.lite.store.EncryptableStore;
import com.couchbase.lite.store.Store;
import com.couchbase.lite.support.security.SymmetricKey;
import com.couchbase.lite.util.ArrayUtils;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.TextUtils;
import com.couchbase.lite.util.Utils;

import junit.framework.Assert;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class DatabaseEncryptionTest extends LiteTestCaseWithDB {
    private static final String TEST_DIR = "encryption";
    private static final String SEEKRIT_DB_NAME = "seekrit";
    private static final String NULL_PASSWORD = null;
    private static final boolean USE_OPENDATABASE_API = true;
    private Manager cryptoManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        cryptoManager = createManager(getTestContext(TEST_DIR, true));
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        if (cryptoManager != null)
            cryptoManager.close();
    }

    private Database openSeekritDatabase(Object key) throws CouchbaseLiteException {
        if (USE_OPENDATABASE_API) {
            DatabaseOptions options = new DatabaseOptions();
            options.setCreate(true);
            options.setEncryptionKey(key);
            return cryptoManager.openDatabase(SEEKRIT_DB_NAME, options);
        } else {
            cryptoManager.registerEncryptionKey(key, SEEKRIT_DB_NAME);
            return cryptoManager.getDatabase(SEEKRIT_DB_NAME);
        }
    }

    public void testSymmetricKey() throws Exception {
        if (!isEncryptionTestEnabled())
            return;

        Database database = openSeekritDatabase(null);

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

    public void testKeyDerivation() throws Exception {
        if (!isEncryptionTestEnabled())
            return;

        Store store = database.getStore();
        if (store instanceof EncryptableStore) {
            EncryptableStore ens = (EncryptableStore)store;
            final byte[] salt =  "Salty McNaCl".getBytes();
            final int rounds = 64000;

            byte[] result = ens.derivePBKDF2SHA256Key("letmein", salt, rounds);
            Assert.assertNotNull(result);
            String hexData = Utils.bytesToHex(result);
            Assert.assertEquals("6a9bd780221f4fe8a594fc728a94ba633b882983fe5613db427bb61242bfef0f",
                    hexData);
        } else
            Assert.fail("No encryptable store");
    }

    public void testEncryptionFailsGracefully() throws Exception {
        if (isEncryptionTestEnabled())
            return;

        Database seekrit = null;
        CouchbaseLiteException error = null;
        try {
            seekrit = openSeekritDatabase("123456");
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
        Database seekrit = openSeekritDatabase(null);
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("answer", "42");
        createDocumentWithProperties(seekrit, properties);

        // Close DB:
        Assert.assertTrue(seekrit.close());

        // Try to reopen with a password which should be failed:
        CouchbaseLiteException error = null;
        try {
            seekrit = null;
            seekrit = openSeekritDatabase("letmein");
        } catch (CouchbaseLiteException e) {
            error = e;
        }
        Assert.assertNotNull(error);
        Assert.assertEquals(401, error.getCBLStatus().getCode());
        Assert.assertNull(seekrit);

        // Reopen with no password:
        seekrit = openSeekritDatabase(NULL_PASSWORD);
        Assert.assertNotNull(seekrit);
    }

    public void testEncryptedDB() throws Exception {
        if (!isEncryptionTestEnabled())
            return;

        // Create encrypted DB:
        Database seekrit = openSeekritDatabase("123456");
        Assert.assertNotNull(seekrit);
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("answer", "42");
        createDocumentWithProperties(seekrit, properties);
        Assert.assertTrue(seekrit.close());

        // Try to reopen without the password (fails):
        CouchbaseLiteException error = null;
        try {
            seekrit = null;
            seekrit = openSeekritDatabase(NULL_PASSWORD);
        } catch (CouchbaseLiteException e) {
            error = e;
        }
        Assert.assertNotNull(error);
        Assert.assertEquals(401, error.getCBLStatus().getCode());
        Assert.assertNull(seekrit);

        // Reopen with correct password:
        seekrit = openSeekritDatabase("123456");
        Assert.assertNotNull(seekrit);
        Assert.assertEquals(1, seekrit.getDocumentCount());
        Assert.assertTrue(seekrit.close());
    }

    public void testDeleteEcryptedDB() throws Exception {
        if (!isEncryptionTestEnabled())
            return;

        // Create encrypted DB:
        Database seekrit = openSeekritDatabase("letmein");
        Assert.assertNotNull(seekrit);
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("answer", "42");
        createDocumentWithProperties(seekrit, properties);

        // Delete db; this also unregisters its password:
        seekrit.delete();

        // Re-create database:
        seekrit = openSeekritDatabase(null);
        Assert.assertNotNull(seekrit);
        Assert.assertEquals(0, seekrit.getDocumentCount());
        Assert.assertTrue(seekrit.close());

        // Make sure it doesn't need a password now:
        seekrit = openSeekritDatabase(NULL_PASSWORD);
        Assert.assertNotNull(seekrit);
        Assert.assertEquals(0, seekrit.getDocumentCount());
        Assert.assertTrue(seekrit.close());

        // Make sure old password doesn't work:
        CouchbaseLiteException error = null;
        try {
            seekrit = null;
            seekrit = openSeekritDatabase("letmein");
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
        Database seekrit = openSeekritDatabase("letmein");
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

        // Reopen the database:
        seekrit = null;
        seekrit = openSeekritDatabase("letmein");
        Assert.assertNotNull(seekrit);
        Assert.assertEquals(1, seekrit.getDocumentCount());
    }

    public void testEncryptedAttachments() throws Exception {
        if (!isEncryptionTestEnabled())
            return;

        Database seekrit = openSeekritDatabase("letmein");
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
        
        Database seekrit = openSeekritDatabase("letmein");
        seekrit.changeEncryptionKey("letmeout");

        // Close & reopen seekrit:
        String dbName = seekrit.getName();
        Assert.assertTrue(seekrit.close());
        seekrit = null;

        cryptoManager.registerEncryptionKey("letmeout", "seekrit");
        Database seekrit2 = openSeekritDatabase("letmeout");
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
