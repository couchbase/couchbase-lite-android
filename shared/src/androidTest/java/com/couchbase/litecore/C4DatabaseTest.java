//
// C4DatabaseTest.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.litecore;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static com.couchbase.litecore.C4Constants.C4DocumentVersioning.kC4RevisionTrees;
import static com.couchbase.litecore.C4Constants.C4EncryptionAlgorithm.kC4EncryptionNone;
import static com.couchbase.litecore.C4Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.C4Constants.C4RevisionFlags.kRevDeleted;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorBusy;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorNotFound;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Ported from c4DatabaseTest.cc
 */
public class C4DatabaseTest extends C4BaseTest {

    static C4Document nextDocument(C4DocEnumerator e) throws LiteCoreException {
        return e.next() ? e.getDocument() : null;
    }

    // - "Database ErrorMessages"
    @Test
    public void testDatabaseErrorMessages() {
        try {
            new C4Database("", 0, null, 0, 0, null);
            fail();
        } catch (LiteCoreException e) {
            assertEquals(LiteCoreDomain, e.domain);
            assertEquals(LiteCoreError.kC4ErrorWrongFormat, e.code);
            assertEquals("file/data is not in the requested format", e.getMessage());
        }

        try {
            db.get("a", true);
            fail();
        } catch (LiteCoreException e) {
            assertEquals(LiteCoreDomain, e.domain);
            assertEquals(kC4ErrorNotFound, e.code);
            assertEquals("not found", e.getMessage());
        }

        try {
            db.get(null, true);
            fail();
        } catch (LiteCoreException e) {
            assertEquals(LiteCoreDomain, e.domain);
            assertEquals(kC4ErrorNotFound, e.code);
            assertEquals("not found", e.getMessage());
        }

        // NOTE: c4error_getMessage() is not supported by Java
    }

    // - "Database Info"
    @Test
    public void testDatabaseInfo() throws LiteCoreException {
        assertEquals(0, db.getDocumentCount());
        assertEquals(0, db.getLastSequence());

        byte[] publicUUID = db.getPublicUUID();
        assertNotNull(publicUUID);
        assertTrue(publicUUID.length > 0);
        // Weird requirements of UUIDs according to the spec:
        assertTrue((publicUUID[6] & 0xF0) == 0x40);
        assertTrue((publicUUID[8] & 0xC0) == 0x80);
        byte[] privateUUID = db.getPrivateUUID();
        assertNotNull(privateUUID);
        assertTrue(privateUUID.length > 0);
        assertTrue((privateUUID[6] & 0xF0) == 0x40);
        assertTrue((privateUUID[8] & 0xC0) == 0x80);
        assertFalse(Arrays.equals(publicUUID, privateUUID));

        reopenDB();

        // Make sure UUIDs are persistent:
        byte[] publicUUID2 = db.getPublicUUID();
        byte[] privateUUID2 = db.getPrivateUUID();
        assertTrue(Arrays.equals(publicUUID, publicUUID2));
        assertTrue(Arrays.equals(privateUUID, privateUUID2));
    }

    // - Database deletion lock
    @Test
    public void testDatabaseDeletionLock() {
        try {
            C4Database.deleteAtPath(db.getPath());
            fail();
        } catch (LiteCoreException e) {
            assertEquals(LiteCoreDomain, e.domain);
            assertEquals(kC4ErrorBusy, e.code);
        }

        try {
            C4Database.deleteAtPath(dir.getAbsolutePath());
            fail();
        } catch (LiteCoreException e) {
            assertEquals(LiteCoreDomain, e.domain);
            assertEquals(kC4ErrorBusy, e.code);
        }
    }

    // - Database Read-Only UUIDs
    @Test
    public void testDatabaseReadOnlyUUIDs() throws LiteCoreException {
        // Make sure UUIDs are available even if the db is opened read-only when they're first accessed.
        reopenDBReadOnly();
        assertNotNull(db.getPublicUUID());
        assertNotNull(db.getPrivateUUID());
    }

    // - "Database OpenBundle"
    @Test
    public void testDatabaseOpenBundle() throws LiteCoreException {
        int flags = getFlags();
        File bundlePath = new File(context.getFilesDir(), "cbl_core_test_bundle");

        if (bundlePath.exists())
            C4Database.deleteAtPath(bundlePath.getPath());
        C4Database bundle = new C4Database(bundlePath.getPath(), flags, null, getVersioning(),
                encryptionAlgorithm(), encryptionKey());
        assertNotNull(bundle);
        bundle.close();
        bundle.free();

        // Reopen without 'create' flag:
        flags &= ~C4DatabaseFlags.kC4DB_Create;
        bundle = new C4Database(bundlePath.getPath(), flags, null, getVersioning(),
                encryptionAlgorithm(), encryptionKey());
        assertNotNull(bundle);
        bundle.close();
        bundle.free();

        // Reopen with wrong storage type:
        // NOTE: Not supported

        // Open nonexistent bundle:
        try {
            File notExist = new File(context.getFilesDir(), "no_such_bundle");
            new C4Database(notExist.getPath(), flags, null, getVersioning(), encryptionAlgorithm(), encryptionKey());
            fail();
        } catch (LiteCoreException e) {
            assertEquals(LiteCoreDomain, e.domain);
            assertEquals(kC4ErrorNotFound, e.code);
        }
    }

    // - "Database Transaction"
    @Test
    public void testDatabaseTransaction() throws LiteCoreException {
        assertEquals(0, db.getDocumentCount());
        assertFalse(db.isInTransaction());
        db.beginTransaction();
        assertTrue(db.isInTransaction());
        db.beginTransaction();
        assertTrue(db.isInTransaction());
        db.endTransaction(true);
        assertTrue(db.isInTransaction());
        db.endTransaction(true);
        assertFalse(db.isInTransaction());
    }

    // - "Database CreateRawDoc"
    @Test
    public void testDatabaseCreateRawDoc() throws LiteCoreException {
        final String store = "test";
        final String key = "key";
        final String meta = "meta";
        boolean commit = false;
        db.beginTransaction();
        try {
            db.rawPut(store, key, meta, kFleeceBody);
            commit = true;
        } finally {
            db.endTransaction(commit);
        }

        C4RawDocument doc = db.rawGet(store, key);
        assertNotNull(doc);
        assertEquals(doc.key(), key);
        assertEquals(doc.meta(), meta);
        assertTrue(Arrays.equals(doc.body(), kFleeceBody));
        doc.free();

        // Nonexistent:
        try {
            db.rawGet(store, "bogus");
            fail("Should not come here.");
        } catch (LiteCoreException ex) {
            assertEquals(LiteCoreDomain, ex.domain);
            assertEquals(kC4ErrorNotFound, ex.code);
        }
    }

    // - Database Rekey
    @Test
    public void testDatabaseRekey() throws LiteCoreException {
        // NOTE: C4Database.rekey() does not work with Java because no JNI binding to setup
        //       BlobStore individually.
    }

    // - "Database AllDocs"
    @Test
    public void testDatabaseAllDocs() throws LiteCoreException {
        setupAllDocs();

        assertEquals(99, db.getDocumentCount());

        C4Document doc;
        int i;

        // No start or end ID:
        int iteratorFlags = C4EnumeratorFlags.kC4Default;
        iteratorFlags &= ~C4EnumeratorFlags.kC4IncludeBodies;
        C4DocEnumerator e = db.enumerateAllDocs(iteratorFlags);
        assertNotNull(e);
        try {
            i = 1;
            while (e.next()) {
                assertNotNull(doc = e.getDocument());
                try {
                    String docID = String.format(Locale.ENGLISH, "doc-%03d", i);
                    assertEquals(docID, doc.getDocID());
                    assertEquals(kRevID, doc.getRevID());
                    assertEquals(kRevID, doc.getSelectedRevID());
                    assertEquals(i, doc.getSelectedSequence());
                    assertNull(doc.getSelectedBody());
                    // Doc was loaded without its body, but it should load on demand:
                    doc.loadRevisionBody();
                    assertTrue(Arrays.equals(kFleeceBody, doc.getSelectedBody()));
                    i++;
                } finally {
                    doc.free();
                }
            }
            assertEquals(100, i);
        } finally {
            e.free();
        }
    }

    // - "Database AllDocsInfo"
    @Test
    public void testAllDocsInfo() throws LiteCoreException {
        setupAllDocs();

        // No start or end ID:
        int iteratorFlags = C4EnumeratorFlags.kC4Default;
        C4DocEnumerator e = db.enumerateAllDocs(iteratorFlags);
        assertNotNull(e);
        try {
            C4Document doc;
            int i = 1;
            while ((doc = nextDocument(e)) != null) {
                try {
                    String docID = String.format(Locale.ENGLISH, "doc-%03d", i);
                    assertEquals(docID, doc.getDocID());
                    assertEquals(kRevID, doc.getRevID());
                    assertEquals(kRevID, doc.getSelectedRevID());
                    assertEquals(i, doc.getSequence());
                    assertEquals(i, doc.getSelectedSequence());
                    assertEquals(C4DocumentFlags.kDocExists, doc.getFlags());
                    i++;
                } finally {
                    doc.free();
                }
            }
            assertEquals(100, i);
        } finally {
            e.free();
        }

    }

    // - "Database Changes"
    @Test
    public void testDatabaseChanges() throws LiteCoreException {
        for (int i = 1; i < 100; i++) {
            String docID = String.format(Locale.ENGLISH, "doc-%03d", i);
            createRev(docID, kRevID, kFleeceBody);
        }

        C4Document doc;
        long seq;

        // Since start:
        int iteratorFlags = C4EnumeratorFlags.kC4Default;
        iteratorFlags &= ~C4EnumeratorFlags.kC4IncludeBodies;
        C4DocEnumerator e = db.enumerateChanges(0, iteratorFlags);
        assertNotNull(e);
        try {
            seq = 1;
            while ((doc = nextDocument(e)) != null) {
                try {
                    String docID = String.format(Locale.ENGLISH, "doc-%03d", seq);
                    assertEquals(docID, doc.getDocID());
                    assertEquals(seq, doc.getSelectedSequence());
                    seq++;
                } finally {
                    doc.free();
                }
            }
            assertEquals(100L, seq);
        } finally {
            e.free();
        }

        // Since 6:
        e = db.enumerateChanges(6, iteratorFlags);
        assertNotNull(e);
        try {
            seq = 7;
            while ((doc = nextDocument(e)) != null) {
                try {
                    String docID = String.format(Locale.ENGLISH, "doc-%03d", seq);
                    assertEquals(docID, doc.getDocID());
                    assertEquals(seq, doc.getSelectedSequence());
                    seq++;
                } finally {
                    doc.free();
                }
            }
            assertEquals(100L, seq);
        } finally {
            e.free();
        }

    }

    // - "Database Expired"
    @Test
    public void testDatabaseExpired() throws LiteCoreException {
        String docID = "expire_me";
        createRev(docID, kRevID, kFleeceBody);

        // unix time
        long expire = System.currentTimeMillis() / 1000 + 1;
        db.setExpiration(docID, expire);

        expire = System.currentTimeMillis() / 1000 + 2;
        db.setExpiration(docID, expire);
        db.setExpiration(docID, expire);

        String docID2 = "expire_me_too";
        createRev(docID2, kRevID, kFleeceBody);
        db.setExpiration(docID2, expire);

        String docID3 = "dont_expire_me";
        createRev(docID3, kRevID, kFleeceBody);
        try {
            Thread.sleep(2 * 1000); // sleep 2 sec
        } catch (InterruptedException e) {
        }

        assertEquals(expire, db.getExpiration(docID));
        assertEquals(expire, db.getExpiration(docID2));
        assertEquals(expire, db.nextDocExpiration());

        // TODO: DB00x - Java does not hava the implementation of c4db_enumerateExpired and c4exp_next yet.
    }

    @Test
    public void testPurgeExpiredDocs() throws LiteCoreException {
        String docID = "expire_me";
        createRev(docID, kRevID, kFleeceBody);

        // unix time
        long expire = System.currentTimeMillis() / 1000 + 1;
        db.setExpiration(docID, expire);

        expire = System.currentTimeMillis() / 1000 + 2;
        db.setExpiration(docID, expire);

        String docID2 = "expire_me_too";
        createRev(docID2, kRevID, kFleeceBody);
        db.setExpiration(docID2, expire);

        try {
            Thread.sleep(3 * 1000); // sleep 3 sec
        } catch (InterruptedException e) {
        }

        int cnt = db.purgeExpiredDocs();

        assertEquals(cnt, 2);
    }

    @Test
    public void testPurgeDoc() throws LiteCoreException {
        String docID = "purge_me";
        createRev(docID, kRevID, kFleeceBody);
        try {
            db.purgeDoc(docID);
        } catch(Exception e) {}
        try {
            db.get(docID, true);
        } catch (LiteCoreException e) {
            assertEquals(LiteCoreDomain, e.domain);
            assertEquals(kC4ErrorNotFound, e.code);
            assertEquals("not found", e.getMessage());
        }
    }

    // - "Database CancelExpire"
    @Test
    public void testDatabaseCancelExpire() throws LiteCoreException {
        String docID = "expire_me";
        createRev(docID, kRevID, kFleeceBody);

        // unix time
        long expire = System.currentTimeMillis() / 1000 + 2;
        db.setExpiration(docID, expire);
        db.setExpiration(docID, 0);

        try {
            Thread.sleep(2 * 1000); // sleep 2 sec
        } catch (InterruptedException e) {
        }

        assertNotNull(db.get(docID, true));
    }

    // - "Database BlobStore"
    @Test
    public void testDatabaseBlobStore() throws LiteCoreException {
        C4BlobStore blobs = db.getBlobStore();
        assertNotNull(blobs);
        // NOTE: BlobStore is from the database. Not necessary to call free()?
    }

    // - "Database Compact"
    @Test
    public void testDatabaseCompact() throws LiteCoreException {
        String doc1ID = "doc001";
        String doc2ID = "doc002";
        String doc3ID = "doc003";
        String doc4ID = "doc004";
        String content1 = "This is the first attachment";
        String content2 = "This is the second attachment";
        String content3 = "This is the third attachment";

        C4BlobKey key1, key2, key3;
        List<String> atts = new ArrayList<>();
        db.beginTransaction();
        try {
            atts.add(content1);
            key1 = addDocWithAttachments(doc1ID, atts, "text/plain").get(0);

            atts.clear();
            atts.add(content2);
            key2 = addDocWithAttachments(doc2ID, atts, "text/plain").get(0);

            addDocWithAttachments(doc4ID, atts, "text/plain");

            atts.clear();
            atts.add(content3);
            key3 = addDocWithAttachments(doc3ID, atts, "text/plain").get(0); // legacy: TODO need to implement legacy support
        } finally {
            db.endTransaction(true);
        }

        C4BlobStore store = db.getBlobStore();
        assertNotNull(store);
        db.compact();
        assertTrue(store.getSize(key1) > 0);
        assertTrue(store.getSize(key2) > 0);
        assertTrue(store.getSize(key3) > 0);

        // Only reference to first blob is gone
        createRev(doc1ID, kRev2ID, null, kRevDeleted);
        db.compact();
        assertTrue(store.getSize(key1) == -1);
        assertTrue(store.getSize(key2) > 0);
        assertTrue(store.getSize(key3) > 0);

        // Two references exist to the second blob, so it should still
        // exist after deleting doc002
        createRev(doc2ID, kRev2ID, null, kRevDeleted);
        db.compact();
        assertTrue(store.getSize(key1) == -1);
        assertTrue(store.getSize(key2) > 0);
        assertTrue(store.getSize(key3) > 0);

        // After deleting doc4 both blobs should be gone
        createRev(doc4ID, kRev2ID, null, kRevDeleted);
        db.compact();
        assertTrue(store.getSize(key1) == -1);
        assertTrue(store.getSize(key2) == -1);
        assertTrue(store.getSize(key3) > 0);

        // Delete doc with legacy attachment, and it too will be gone
        createRev(doc3ID, kRev2ID, null, kRevDeleted);
        db.compact();
        assertTrue(store.getSize(key1) == -1);
        assertTrue(store.getSize(key2) == -1);
        assertTrue(store.getSize(key3) == -1);
    }

    // - "Database copy"
    @Test
    public void testDatabaseCopy() throws LiteCoreException {
        String doc1ID = "doc001";
        String doc2ID = "doc002";

        createRev(doc1ID, kRevID, kFleeceBody);
        createRev(doc2ID, kRevID, kFleeceBody);

        String srcPath = db.getPath();

        File nuPath = new File(context.getFilesDir(), "nudb.cblite2");

        try {
            C4Database.deleteAtPath(nuPath.getAbsolutePath());
        } catch (LiteCoreException e) {
            assertEquals(0, e.code);
        }

        C4Database.copy(srcPath,
                nuPath.getAbsolutePath(),
                getFlags(),
                null,
                kC4RevisionTrees,
                kC4EncryptionNone,
                null);

        C4Database nudb = new C4Database(nuPath.getAbsolutePath(), getFlags(), null, kC4RevisionTrees, kC4EncryptionNone, null);
        assertNotNull(nudb);
        assertEquals(2, nudb.getDocumentCount());
        nudb.delete();

        nudb = new C4Database(nuPath.getAbsolutePath(), getFlags(), null, kC4RevisionTrees, kC4EncryptionNone, null);
        assertNotNull(nudb);
        createRev(nudb, doc1ID, kRevID, kFleeceBody);
        assertEquals(1, nudb.getDocumentCount());
        nudb.close();

        String originalDest = nuPath.getAbsolutePath();
        nuPath = new File(context.getFilesDir(), "bogus" + File.separator + "nunudb.cblite2" + File.separator);
        try {
            // call to c4db_copy will internally throw an exception
            C4Database.copy(srcPath, nuPath.getAbsolutePath(),
                    getFlags(), null, kC4RevisionTrees,
                    kC4EncryptionNone, null);
            fail();
        } catch (LiteCoreException ex) {
            assertEquals(kC4ErrorNotFound, ex.code);
        }

        nudb = new C4Database(originalDest, getFlags(), null, kC4RevisionTrees, kC4EncryptionNone, null);
        assertNotNull(nudb);
        assertEquals(1, nudb.getDocumentCount());
        nudb.close();

        String originalSrc = srcPath;
        srcPath += "bogus" + File.separator;
        nuPath = new File(originalDest);
        try {
            // call to c4db_copy will internally throw an exception
            C4Database.copy(srcPath, nuPath.getAbsolutePath(),
                    getFlags(), null, kC4RevisionTrees,
                    kC4EncryptionNone, null);
            fail();
        } catch (LiteCoreException ex) {
            assertEquals(kC4ErrorNotFound, ex.code);
        }

        nudb = new C4Database(originalDest, getFlags(), null, kC4RevisionTrees, kC4EncryptionNone, null);
        assertNotNull(nudb);
        assertEquals(1, nudb.getDocumentCount());
        nudb.close();
    }

    private void setupAllDocs() throws LiteCoreException {
        for (int i = 1; i < 100; i++) {
            String docID = String.format(Locale.ENGLISH, "doc-%03d", i);
            createRev(docID, kRevID, kFleeceBody);
        }

        // Add a deleted doc to make sure it's skipped by default:
        createRev("doc-005DEL", kRevID, null, kRevDeleted);
    }
}
