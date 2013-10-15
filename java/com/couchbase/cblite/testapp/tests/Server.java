package com.couchbase.cblite.testapp.tests;

import java.io.File;
import java.io.IOException;
import java.util.List;

import junit.framework.Assert;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.internal.CBLServerInternal;

public class Server extends CBLiteTestCase {

    public void testServer() {

        //to ensure this test is easily repeatable we will explicitly remove
        //any stale foo.cblite
        CBLDatabase old = server.getExistingDatabaseNamed("foo");
        if(old != null) {
            old.delete();
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

    public void testUpgradeOldDatabaseFiles() throws Exception {


        String directoryName = "test-directory-" + System.currentTimeMillis();
        String normalFilesDir = getInstrumentation().getContext().getFilesDir().getAbsolutePath();
        String fakeFilesDir = String.format("%s/%s", normalFilesDir, directoryName);

        File directory = new File(fakeFilesDir);
        if(!directory.exists()) {
            boolean result = directory.mkdir();
            if(!result) {
                throw new IOException("Unable to create directory " + directory);
            }
        }
        File oldTouchDbFile = new File(directory, String.format("old%s", CBLServerInternal.DATABASE_SUFFIX_OLD));
        oldTouchDbFile.createNewFile();
        File newCbLiteFile = new File(directory, String.format("new%s", CBLServerInternal.DATABASE_SUFFIX));
        newCbLiteFile.createNewFile();

        CBLServerInternal serverForThisTest = new CBLServerInternal(fakeFilesDir);

        File migratedOldFile = new File(directory, String.format("old%s", CBLServerInternal.DATABASE_SUFFIX));

        Assert.assertTrue(migratedOldFile.exists());
        Assert.assertTrue(oldTouchDbFile.exists() == false);
        Assert.assertTrue(newCbLiteFile.exists());


    }

}
