package com.couchbase.cblite.testapp.tests;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;
import android.util.Log;

import com.couchbase.cblite.CBLBody;
import com.couchbase.cblite.CBLRevision;
import com.couchbase.cblite.CBLStatus;

public class LocalDocs extends CBLiteTestCase {

    public static final String TAG = "LocalDocs";

    public void testLocalDocs() {

        //create a document
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("_id", "_local/doc1");
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);

        CBLBody body = new CBLBody(documentProperties);
        CBLRevision rev1 = new CBLRevision(body, database);

        CBLStatus status = new CBLStatus();
        rev1 = database.putLocalRevision(rev1, null, status);

        Assert.assertEquals(CBLStatus.CREATED, status.getCode());
        Log.v(TAG, "Created " + rev1);
        Assert.assertEquals("_local/doc1", rev1.getDocId());
        Assert.assertTrue(rev1.getRevId().startsWith("1-"));

        //read it back
        CBLRevision readRev = database.getLocalDocument(rev1.getDocId(), null);
        Assert.assertNotNull(readRev);
        Map<String,Object> readRevProps = readRev.getProperties();
        Assert.assertEquals(rev1.getDocId(), readRev.getProperties().get("_id"));
        Assert.assertEquals(rev1.getRevId(), readRev.getProperties().get("_rev"));
        Assert.assertEquals(userProperties(readRevProps), userProperties(body.getProperties()));

        //now update it
        documentProperties = readRev.getProperties();
        documentProperties.put("status", "updated!");
        body = new CBLBody(documentProperties);
        CBLRevision rev2 = new CBLRevision(body, database);
        CBLRevision rev2input = rev2;
        rev2 = database.putLocalRevision(rev2, rev1.getRevId(), status);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());
        Log.v(TAG, "Updated " + rev1);
        Assert.assertEquals(rev1.getDocId(), rev2.getDocId());
        Assert.assertTrue(rev2.getRevId().startsWith("2-"));

        //read it back
        readRev = database.getLocalDocument(rev2.getDocId(), null);
        Assert.assertNotNull(readRev);
        Assert.assertEquals(userProperties(readRev.getProperties()), userProperties(body.getProperties()));

        // Try to update the first rev, which should fail:
        database.putLocalRevision(rev2input, rev1.getRevId(), status);
        Assert.assertEquals(CBLStatus.CONFLICT, status.getCode());

        // Delete it:
        CBLRevision revD = new CBLRevision(rev2.getDocId(), null, true, database);
        CBLRevision revResult = database.putLocalRevision(revD, null, status);
        Assert.assertNull(revResult);
        Assert.assertEquals(CBLStatus.CONFLICT, status.getCode());
        revD = database.putLocalRevision(revD, rev2.getRevId(), status);
        Assert.assertEquals(CBLStatus.OK, status.getCode());

        // Delete nonexistent doc:
        CBLRevision revFake = new CBLRevision("_local/fake", null, true, database);
        database.putLocalRevision(revFake, null, status);
        Assert.assertEquals(CBLStatus.NOT_FOUND, status.getCode());

        // Read it back (should fail):
        readRev = database.getLocalDocument(revD.getDocId(), null);
        Assert.assertNull(readRev);
    }

}
