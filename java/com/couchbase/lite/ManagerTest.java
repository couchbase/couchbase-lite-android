package com.couchbase.lite;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ManagerTest extends LiteTestCase {

    public void testServer() {

        //to ensure this test is easily repeatable we will explicitly remove
        //any stale foo.cblite
        boolean mustExist = true;
        Database old = manager.getDatabaseWithoutOpening("foo", mustExist);
        if(old != null) {
            old.delete();
        }

        mustExist = false;
        Database db = manager.getDatabaseWithoutOpening("foo", mustExist);
        assertNotNull(db);
        assertEquals("foo", db.getName());
        assertTrue(db.getPath().startsWith(getServerPath()));
        assertFalse(db.exists());


        // because foo doesn't exist yet
        List<String> databaseNames = manager.getAllDatabaseNames();
        assertTrue(!databaseNames.contains("foo"));

        assertTrue(db.open());
        assertTrue(db.exists());

        databaseNames = manager.getAllDatabaseNames();
        assertTrue(databaseNames.contains("foo"));

        db.close();
        db.delete();

    }

    public void testUpgradeOldDatabaseFiles() throws Exception {
        String directoryName = "test-directory-" + System.currentTimeMillis();
        String normalFilesDir = getRootDirectory().getAbsolutePath();
        String fakeFilesDir = String.format("%s/%s", normalFilesDir, directoryName);

        File directory = new File(fakeFilesDir);
        if(!directory.exists()) {
            boolean result = directory.mkdir();
            if(!result) {
                throw new IOException("Unable to create directory " + directory);
            }
        }
        File oldTouchDbFile = new File(directory, String.format("old%s", Manager.DATABASE_SUFFIX_OLD));
        oldTouchDbFile.createNewFile();
        File newCbLiteFile = new File(directory, String.format("new%s", Manager.DATABASE_SUFFIX));
        newCbLiteFile.createNewFile();

        File migratedOldFile = new File(directory, String.format("old%s", Manager.DATABASE_SUFFIX));
        migratedOldFile.createNewFile();
        super.stopCBLite();
        manager = new Manager(new File(getRootDirectory(), directoryName), Manager.DEFAULT_OPTIONS);

        assertTrue(migratedOldFile.exists());
        //cannot rename old.touchdb in old.cblite, old.cblite already exists
        assertTrue(oldTouchDbFile.exists());
        assertTrue(newCbLiteFile.exists());

        File dir=new File(getRootDirectory(), directoryName);
        assertEquals(3, dir.listFiles().length);

        super.stopCBLite();
        migratedOldFile.delete();
        manager = new Manager(new File(getRootDirectory(), directoryName), Manager.DEFAULT_OPTIONS);

        //rename old.touchdb in old.cblite, previous old.cblite already doesn't exist
        assertTrue(migratedOldFile.exists());
        assertTrue(oldTouchDbFile.exists() == false);
        assertTrue(newCbLiteFile.exists());
        dir=new File(getRootDirectory(), directoryName);
        assertEquals(2, dir.listFiles().length);

    }

}
