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

import junit.framework.Assert;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class DatabaseEncryptionTest extends LiteTestCaseWithDB {
    private static final String TEST_DIR = "encryption";
    private static final String NULL_PASSWORD = null;
    private Manager currentManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ManagerOptions options = new ManagerOptions();
        options.setEnableStorageEncryption(true);
        currentManager = new Manager(getTestContext(TEST_DIR, true), options);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        if (currentManager != null)
            currentManager.close();
    }

    private Manager getEncryptionTestManager() {
        return currentManager;
    }

    public void testEncryptionFailsGracefully() throws Exception {
        if (!isSQLiteDB() || !isAndriod())
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
        if (!isSQLiteDB() || !isAndriod())
            return;

        Manager cryptoManager = getEncryptionTestManager();

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
        if (!isSQLiteDB() || !isAndriod())
            return;

        Manager cryptoManager = getEncryptionTestManager();

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
        if (!isSQLiteDB() || !isAndriod())
            return;

        Manager cryptoManager = getEncryptionTestManager();

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
        if (!isSQLiteDB() || !isAndriod())
            return;

        Manager cryptoManager = getEncryptionTestManager();

        // Create encrypted DB:
        cryptoManager.registerEncryptionKey("letmein", "seekrit");
        Database seekrit = cryptoManager.getDatabase("seekrit");
        Assert.assertNotNull(seekrit);

        File f0 = new File("/data/data/com.couchbase.lite.test/files/encryption/seekrit.cblite2/attachments/_encryption");
        boolean exist0 = f0.exists();

        // Create a doc and then update it:
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("answer", "42");
        Document doc = createDocumentWithProperties(seekrit, properties);
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

        File f = new File("/data/data/com.couchbase.lite.test/files/encryption/seekrit.cblite2/attachments/_encryption");
        boolean exist = f.exists();

        // Close and reopen:
        Assert.assertTrue(seekrit.close());
        cryptoManager.registerEncryptionKey("letmein", "seekrit");
        File f1 = new File("/data/data/com.couchbase.lite.test/files/encryption/seekrit.cblite2/attachments/_encryption");
        boolean exist1 = f1.exists();

        seekrit = cryptoManager.getDatabase("seekrit");
        Assert.assertNotNull(seekrit);
        Assert.assertEquals(1, seekrit.getDocumentCount());
    }
}
