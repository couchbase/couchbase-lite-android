package com.couchbase.touchdb.testapp.tests;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import junit.framework.Assert;
import android.test.AndroidTestCase;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.support.DirUtils;

public class Server extends AndroidTestCase {

    protected String getServerPath() {
        String filesDir = getContext().getFilesDir().getAbsolutePath() + "/tests";
        return filesDir;
    }

    @Override
    protected void setUp() throws Exception {
        //delete and recreate the server path
        String serverPath = getServerPath();
        File serverPathFile = new File(serverPath);
        DirUtils.deleteRecursive(serverPathFile);
        serverPathFile.mkdir();
    }

    public void testServer() {



        TDServer server = null;
        try {
            server = new TDServer(getServerPath());
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
        Assert.assertTrue(db.getPath().startsWith(getServerPath()));
        Assert.assertFalse(db.exists());

        Assert.assertEquals(db, server.getDatabaseNamed("foo"));

        // because foo doesn't exist yet
        List<String> databaseNames = new ArrayList<String>();
        Assert.assertEquals(databaseNames, server.allDatabaseNames());

        Assert.assertTrue(db.open());
        Assert.assertTrue(db.exists());

        databaseNames.add("foo");
        Assert.assertEquals(databaseNames, server.allDatabaseNames());

        db.close();
        server.deleteDatabaseNamed("foo");
        server.close();

    }

}
