package com.couchbase.cblite.testapp.tests;

import java.util.List;

import junit.framework.Assert;

import com.couchbase.cblite.CBLDatabase;

public class Server extends CBLiteTestCase {

    public void testServer() {

        //to ensure this test is easily repeatable we will explicitly remove
        //any stale foo.cblite
        CBLDatabase old = server.getExistingDatabaseNamed("foo");
        if(old != null) {
            old.deleteDatabase();
        }

        CBLDatabase db = server.getDatabaseNamed("foo");
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
