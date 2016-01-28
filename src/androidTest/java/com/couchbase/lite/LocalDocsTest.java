package com.couchbase.lite;

import com.couchbase.lite.internal.Body;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.util.Log;

import java.util.HashMap;
import java.util.Map;


public class LocalDocsTest extends LiteTestCaseWithDB {

    public static final String TAG = "LocalDocs";

    public void testLocalDocs() throws CouchbaseLiteException {

        //create a document
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("_id", "_local/doc1");
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);

        Body body = new Body(documentProperties);
        RevisionInternal rev1 = new RevisionInternal(body);

        Status status = new Status();
        rev1 = database.getStore().putLocalRevision(rev1, null, true);

        Log.v(TAG, "Created " + rev1);
        assertEquals("_local/doc1", rev1.getDocID());
        assertTrue(rev1.getRevID().startsWith("1-"));

        //read it back
        RevisionInternal readRev = database.getLocalDocument(rev1.getDocID(), null);
        assertNotNull(readRev);
        Map<String, Object> readRevProps = readRev.getProperties();
        assertEquals(rev1.getDocID(), readRev.getProperties().get("_id"));
        assertEquals(rev1.getRevID(), readRev.getProperties().get("_rev"));
        assertEquals(userProperties(readRevProps), userProperties(body.getProperties()));

        //now update it
        documentProperties = readRev.getProperties();
        documentProperties.put("status", "updated!");
        body = new Body(documentProperties);
        RevisionInternal rev2 = new RevisionInternal(body);
        RevisionInternal rev2input = rev2;
        rev2 = database.getStore().putLocalRevision(rev2, rev1.getRevID(), true);
        Log.v(TAG, "Updated " + rev1);
        assertEquals(rev1.getDocID(), rev2.getDocID());
        assertTrue(rev2.getRevID().startsWith("2-"));

        //read it back
        readRev = database.getLocalDocument(rev2.getDocID(), null);
        assertNotNull(readRev);
        assertEquals(userProperties(readRev.getProperties()), userProperties(body.getProperties()));

        // Try to update the first rev, which should fail:
        boolean gotException = false;
        try {
            database.getStore().putLocalRevision(rev2input, rev1.getRevID(), true);
        } catch (CouchbaseLiteException e) {
            assertEquals(Status.CONFLICT, e.getCBLStatus().getCode());
            gotException = true;
        }
        assertTrue(gotException);


        // Delete it:
        RevisionInternal revD = new RevisionInternal(rev2.getDocID(), null, true);

        gotException = false;
        try {
            RevisionInternal revResult = database.getStore().putLocalRevision(revD, null, true);
            assertNull(revResult);
        } catch (CouchbaseLiteException e) {
            assertEquals(Status.CONFLICT, e.getCBLStatus().getCode());
            gotException = true;
        }
        assertTrue(gotException);

        revD = database.getStore().putLocalRevision(revD, rev2.getRevID(), true);

        // Delete nonexistent doc:
        gotException = false;
        RevisionInternal revFake = new RevisionInternal("_local/fake", null, true);
        try {
            database.getStore().putLocalRevision(revFake, null, true);
        } catch (CouchbaseLiteException e) {
            assertEquals(Status.NOT_FOUND, e.getCBLStatus().getCode());
            gotException = true;
        }
        assertTrue(gotException);

        // Read it back (should fail):
        readRev = database.getLocalDocument(revD.getDocID(), null);
        assertNull(readRev);
    }
}
