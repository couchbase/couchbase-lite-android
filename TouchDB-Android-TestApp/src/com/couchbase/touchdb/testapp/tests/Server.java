package com.couchbase.touchdb.testapp.tests;

import java.util.List;

import junit.framework.Assert;

import com.couchbase.touchdb.TDDatabase;

public class Server extends TouchDBTestCase {

    public void testServer() {

        //to ensure this test is easily repeatable we will explicitly remove
        //any stale foo.touchdb
        TDDatabase old = server.getExistingDatabaseNamed("foo");
        if(old != null) {
            old.deleteDatabase();
        }

        TDDatabase db = server.getDatabaseNamed("foo");
        Assert.assertNotNull(db);
        Assert.assertEquals("foo", db.getName());
        Assert.assertTrue(db.getPath().startsWith(getServerPath()));
        Assert.assertFalse(db.exists());

        Assert.assertEquals(db, server.getDatabaseNamed("foo"));

        // because foo doesn't exist yet
        List<String> databaseNames = server.allDatabaseNames();
        Assert.assertTrue(!databaseNames.contains("foo"));

        Assert.assertTrue(db.open());
        Assert.assertTrue(db.exists());

        databaseNames = server.allDatabaseNames();
        Assert.assertTrue(databaseNames.contains("foo"));

        db.close();
        server.deleteDatabaseNamed("foo");
    }

}
