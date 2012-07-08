package com.couchbase.touchdb.testapp.tests;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;
import android.util.Log;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDStatus;

public class LocalDocs extends TouchDBTestCase {

    public static final String TAG = "LocalDocs";

    public void testLocalDocs() {

        //create a document
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("_id", "_local/doc1");
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);

        TDBody body = new TDBody(documentProperties);
        TDRevision rev1 = new TDRevision(body);

        TDStatus status = new TDStatus();
        rev1 = database.putLocalRevision(rev1, null, status);

        Assert.assertEquals(TDStatus.CREATED, status.getCode());
        Log.v(TAG, "Created " + rev1);
        Assert.assertEquals("_local/doc1", rev1.getDocId());
        Assert.assertTrue(rev1.getRevId().startsWith("1-"));

        //read it back
        TDRevision readRev = database.getLocalDocument(rev1.getDocId(), null);
        Assert.assertNotNull(readRev);
        Map<String,Object> readRevProps = readRev.getProperties();
        Assert.assertEquals(rev1.getDocId(), readRev.getProperties().get("_id"));
        Assert.assertEquals(rev1.getRevId(), readRev.getProperties().get("_rev"));
        Assert.assertEquals(userProperties(readRevProps), userProperties(body.getProperties()));

        //now update it
        documentProperties = readRev.getProperties();
        documentProperties.put("status", "updated!");
        body = new TDBody(documentProperties);
        TDRevision rev2 = new TDRevision(body);
        TDRevision rev2input = rev2;
        rev2 = database.putLocalRevision(rev2, rev1.getRevId(), status);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());
        Log.v(TAG, "Updated " + rev1);
        Assert.assertEquals(rev1.getDocId(), rev2.getDocId());
        Assert.assertTrue(rev2.getRevId().startsWith("2-"));

        //read it back
        readRev = database.getLocalDocument(rev2.getDocId(), null);
        Assert.assertNotNull(readRev);
        Assert.assertEquals(userProperties(readRev.getProperties()), userProperties(body.getProperties()));

        // Try to update the first rev, which should fail:
        database.putLocalRevision(rev2input, rev1.getRevId(), status);
        Assert.assertEquals(TDStatus.CONFLICT, status.getCode());

        // Delete it:
        TDRevision revD = new TDRevision(rev2.getDocId(), null, true);
        TDRevision revResult = database.putLocalRevision(revD, null, status);
        Assert.assertNull(revResult);
        Assert.assertEquals(TDStatus.CONFLICT, status.getCode());
        revD = database.putLocalRevision(revD, rev2.getRevId(), status);
        Assert.assertEquals(TDStatus.OK, status.getCode());

        // Delete nonexistent doc:
        TDRevision revFake = new TDRevision("_local/fake", null, true);
        database.putLocalRevision(revFake, null, status);
        Assert.assertEquals(TDStatus.NOT_FOUND, status.getCode());

        // Read it back (should fail):
        readRev = database.getLocalDocument(revD.getDocId(), null);
        Assert.assertNull(readRev);
    }

}
