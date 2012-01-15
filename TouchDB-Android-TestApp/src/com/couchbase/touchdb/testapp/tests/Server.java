package com.couchbase.touchdb.testapp.tests;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;
import android.test.AndroidTestCase;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDServer;

public class Server extends AndroidTestCase {

    public void testServer() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDServer server = null;
        try {
            server = new TDServer(filesDir);
        } catch (IOException e) {
            fail("Creating server caused IOException");
        }

        //to ensure this test is easily repeatable we will explicitly remove
        //any stale foo.touchdb
        TDDatabase old = server.getExistingDatabaseNamed("foo");
        if(old != null) {
            old.deleteDatabase();
        }

        TDDatabase db = server.getDatabaseNamed("foo");
        Assert.assertNotNull(db);
        Assert.assertEquals("foo", db.getName());
        Assert.assertTrue(db.getPath().startsWith(filesDir));
        Assert.assertFalse(db.exists());

        Assert.assertEquals(db, server.getDatabaseNamed("foo"));

        // because foo doesn't exist yet
        List<String> databaseNames = new ArrayList<String>();
        Assert.assertEquals(databaseNames, server.allDatabaseNames());

        Assert.assertTrue(db.open());
        Assert.assertTrue(db.exists());

        databaseNames.add("foo");
        Assert.assertEquals(databaseNames, server.allDatabaseNames());

    }

}
