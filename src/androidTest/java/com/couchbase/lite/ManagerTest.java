package com.couchbase.lite;

import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.storage.ContentValues;
import com.couchbase.lite.storage.SQLException;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManagerTest extends LiteTestCase {

    public static final String TAG = "ManagerTest";

    public void testServer() throws CouchbaseLiteException {

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
        assertTrue(db.getPath().startsWith(new LiteTestContext().getRootDirectory().getAbsolutePath()));
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
        LiteTestContext context = new LiteTestContext(directoryName);

        File directory = context.getFilesDir();
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
        manager = new Manager(context, Manager.DEFAULT_OPTIONS);

        assertTrue(migratedOldFile.exists());
        //cannot rename old.touchdb to old.cblite, because old.cblite already exists
        assertTrue(oldTouchDbFile.exists());
        assertTrue(newCbLiteFile.exists());

        assertEquals(3, directory.listFiles().length);

        super.stopCBLite();
        migratedOldFile.delete();
        manager = new Manager(context, Manager.DEFAULT_OPTIONS);

        //rename old.touchdb in old.cblite, previous old.cblite already doesn't exist
        assertTrue(migratedOldFile.exists());
        assertTrue(oldTouchDbFile.exists() == false);
        assertTrue(newCbLiteFile.exists());
        assertEquals(2, directory.listFiles().length);

    }

    public void testReplaceDatabaseNamedNoAttachments() throws CouchbaseLiteException {

        //Copy database from assets to local storage
        InputStream dbStream = getAsset("noattachments.cblite");

        manager.replaceDatabase("replaced", dbStream, null);

        //Now validate the number of files in the DB
        assertEquals(10,manager.getDatabase("replaced").getDocumentCount());

    }

    public void testReplaceDatabaseNamedWithAttachments() throws CouchbaseLiteException {

        InputStream dbStream = getAsset("withattachments.cblite");

        String[] attachmentlist = null;

        Map<String, InputStream> attachments = new HashMap<String, InputStream>();
        InputStream blobStream = getAsset("attachments/356a192b7913b04c54574d18c28d46e6395428ab.blob");
        attachments.put("356a192b7913b04c54574d18c28d46e6395428ab.blob",blobStream);

        manager.replaceDatabase("replaced2", dbStream, attachments);

        //Validate the number of files in the DB
        assertEquals(1,manager.getDatabase("replaced2").getDocumentCount());

        //get the attachment from the document
        Document doc = manager.getDatabase("replaced2").getExistingDocument("168e0c56-4588-4df4-8700-4d5115fa9c74");

        assertNotNull(doc);

        RevisionInternal gotRev1 = database.getDocumentWithIDAndRev(doc.getId(), doc.getCurrentRevisionId(), EnumSet.noneOf(Database.TDContentOptions.class));

    }

}
