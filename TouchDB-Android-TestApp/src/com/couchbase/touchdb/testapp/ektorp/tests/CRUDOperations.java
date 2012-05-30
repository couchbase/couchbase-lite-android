package com.couchbase.touchdb.testapp.ektorp.tests;

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

import android.test.AndroidTestCase;
import android.util.Log;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.ektorp.TouchDBHttpClient;
import com.couchbase.touchdb.router.TDURLStreamHandlerFactory;

public class CRUDOperations extends AndroidTestCase {

    //static inializer to ensure that touchdb:// URLs are handled properly
    {
        TDURLStreamHandlerFactory.registerSelfIgnoreError();
    }

    public static final String TAG = "Ektorp-CRUDOperations";

    public void testCRUDOperations() throws IOException {

        String filesDir = getContext().getFilesDir().getAbsolutePath();
        TDServer tdserver = new TDServer(filesDir);

        //ensure the test is repeatable
        TDDatabase old = tdserver.getExistingDatabaseNamed("ektorp_crud_test");
        if(old != null) {
            old.deleteDatabase();
        }

        HttpClient httpClient = new TouchDBHttpClient(tdserver);
        CouchDbInstance server = new StdCouchDbInstance(httpClient);

        CouchDbConnector db = server.createConnector("ektorp_crud_test", true);

        TestObject test = new TestObject(1, false, "ektorp");

        db.create(test);
        Assert.assertTrue(test.getId().length() >= 10);
        Assert.assertTrue(test.getRevision().startsWith("1-"));

        //read it back
        TestObject read = db.get(TestObject.class, test.getId());
        Assert.assertNotNull(read);
        Assert.assertEquals(read.getFoo(), test.getFoo());
        Assert.assertEquals(read.getBar(), test.getBar());
        Assert.assertEquals(read.getBaz(), test.getBaz());

        //now update it
        read.setStatus("updated!");
        db.update(read);
        Assert.assertEquals(test.getId(), read.getId());
        Assert.assertTrue(read.getRevision().startsWith("2-"));

        //read it back
        read = db.get(TestObject.class, read.getId());
        Assert.assertNotNull(read);
        Assert.assertEquals("updated!", read.getStatus());

        // try to update the first rev, which should fail:
        try {
            db.update(test);
            fail("expected update conflict exception");
        } catch (UpdateConflictException e) {
            //expected
        }

        // check the changes feed
        ChangesCommand changesCommand = new ChangesCommand.Builder().continuous(false).since(0l).build();
        List<DocumentChange> changes = db.changes(changesCommand);
        Log.v(TAG, "changes: " + changes);
        Assert.assertEquals(1, changes.size());

        // delete it
        try {
            db.delete(test);
            fail("expected update conflict exception");
        } catch (UpdateConflictException e) {
            //expected
        }
        String deletedRev = db.delete(read);
        Assert.assertTrue(deletedRev.startsWith("3-"));

        // delete nonexistent doc
        try {
            db.delete("fakeid", "6-");
            fail("expected document not found exception");
        } catch (DocumentNotFoundException e) {
            //expected
        }

        //read back the deleted document
        try {
            read = db.get(TestObject.class, test.getId());
            fail("expected document not found exception");
        } catch (DocumentNotFoundException e) {
            //expected
        }
    }

}
