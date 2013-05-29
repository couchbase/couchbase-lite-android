package com.couchbase.cblite.testapp.ektorp.tests;

import java.io.IOException;
import java.util.List;

import junit.framework.Assert;

import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.DocumentNotFoundException;
import org.ektorp.UpdateConflictException;
import org.ektorp.changes.ChangesCommand;
import org.ektorp.changes.DocumentChange;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import android.util.Log;

import com.couchbase.cblite.ektorp.CBLiteHttpClient;
import com.couchbase.cblite.testapp.tests.CBLiteTestCase;

public class CRUDOperations extends CBLiteTestCase {

    public static final String TAG = "Ektorp-CRUDOperations";

    public void testCRUDOperations() throws IOException {

        HttpClient httpClient = new CBLiteHttpClient(server);
        CouchDbInstance dbInstance = new StdCouchDbInstance(httpClient);

        CouchDbConnector dbConnector = dbInstance.createConnector(DEFAULT_TEST_DB, true);

        TestObject test = new TestObject(1, false, "ektorp");

        dbConnector.create(test);
        Assert.assertTrue(test.getId().length() >= 10);
        Assert.assertTrue(test.getRevision().startsWith("1-"));

        //read it back
        TestObject read = dbConnector.get(TestObject.class, test.getId());
        Assert.assertNotNull(read);
        Assert.assertEquals(read.getFoo(), test.getFoo());
        Assert.assertEquals(read.getBar(), test.getBar());
        Assert.assertEquals(read.getBaz(), test.getBaz());

        //now update it
        read.setStatus("updated!");
        dbConnector.update(read);
        Assert.assertEquals(test.getId(), read.getId());
        Assert.assertTrue(read.getRevision().startsWith("2-"));

        //read it back
        read = dbConnector.get(TestObject.class, read.getId());
        Assert.assertNotNull(read);
        Assert.assertEquals("updated!", read.getStatus());

        // try to update the first rev, which should fail:
        try {
            dbConnector.update(test);
            fail("expected update conflict exception");
        } catch (UpdateConflictException e) {
            //expected
        }

        // check the changes feed
        ChangesCommand changesCommand = new ChangesCommand.Builder().continuous(false).since(0l).build();
        List<DocumentChange> changes = dbConnector.changes(changesCommand);
        Log.v(TAG, "changes: " + changes);
        Assert.assertEquals(1, changes.size());

        // delete it
        try {
            dbConnector.delete(test);
            fail("expected update conflict exception");
        } catch (UpdateConflictException e) {
            //expected
        }
        String deletedRev = dbConnector.delete(read);
        Assert.assertTrue(deletedRev.startsWith("3-"));

        // delete nonexistent doc
        try {
            dbConnector.delete("fakeid", "6-");
            fail("expected document not found exception");
        } catch (DocumentNotFoundException e) {
            //expected
        }

        //read back the deleted document
        try {
            read = dbConnector.get(TestObject.class, test.getId());
            fail("expected document not found exception");
        } catch (DocumentNotFoundException e) {
            //expected
        }
    }

}
