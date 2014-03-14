package com.couchbase.lite;

import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

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

        //InputStream dbStream = getAssets().open("noattachments.cblite");

        File tempDB = new File(getCacheDirectory(), "temp.cblite");

        try
        {
            FileOutputStream tempOutput = new FileOutputStream(tempDB);

            copyFile(dbStream, tempOutput);
        }
        catch(FileNotFoundException fnfex)
        {
            Log.e(TAG,"Failed to open temp DB file",fnfex);
            fail();
        }
        catch(IOException ioex)
        {
            Log.e(TAG,"Failed to copy asset to temp DB file",ioex);
            fail();
        }

        manager.replaceDatabase("replaced", tempDB, null);

        //Now validate the number of files in the DB
        assertEquals(10,manager.getDatabase("replaced").getDocumentCount());

    }

    public void testReplaceDatabaseNamedWithAttachments() throws CouchbaseLiteException {

        InputStream dbStream = getAsset("withattachments.cblite");

        //InputStream dbStream = getAssets().open("withattachments.cblite");

        //Copy DB from assets to local file system, we need to
        //do this as replaceDatabase takes java.io.File arguments
        //and we can't generate these for bundled assets

        File tempDB = new File(getCacheDirectory(), "temp.cblite");

        try
        {
            FileOutputStream tempOutput = new FileOutputStream(tempDB);

            copyFile(dbStream, tempOutput);
        }
        catch(FileNotFoundException fnfex)
        {
            Log.e(TAG,"Failed to open temp DB file",fnfex);
            fail();
        }
        catch(IOException ioex)
        {
            Log.e(TAG,"Failed to copy asset to temp DB file",ioex);
            fail();
        }

        //Copy attachments from assets to local file system

        //Copy attachment blob from assets to local storage
        InputStream blobStream = getAsset("attachments/2e6b28a8927a2ce9f8612eb4a589efaca6c177ae.blob");

        File tempAttachmentsDir = new File(getCacheDirectory(), "attachments");

        tempAttachmentsDir.mkdir();

        File tempBlobPath = new File(tempAttachmentsDir, "2e6b28a8927a2ce9f8612eb4a589efaca6c177ae.blob");

        try
        {
            FileOutputStream tempOutput = new FileOutputStream(tempBlobPath);

            copyFile(blobStream, tempOutput);
        }
        catch(FileNotFoundException fnfex)
        {
            Log.e(TAG,"Failed to open temp blob file",fnfex);
            fail();
        }
        catch(IOException ioex)
        {
            Log.e(TAG,"Failed to copy asset to temp blob file",ioex);
            fail();
        }

        manager.replaceDatabase("replaced2", tempDB, tempAttachmentsDir);

        //Validate the number of files in the DB
        assertEquals(1,manager.getDatabase("replaced2").getDocumentCount());

        //get the attachment from the document
        Document doc = manager.getDatabase("replaced2").getExistingDocument("doc0-1394815387949");

        assertNotNull(doc);

        Attachment attachment = doc.getCurrentRevision().getAttachment("attachment.png");

        Assert.assertEquals(519173L, attachment.getLength());
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    protected File getCacheDirectory() {
        String rootDirectoryPath = System.getProperty("user.dir");
        File rootDirectory = new File(rootDirectoryPath);
        rootDirectory = new File(rootDirectory, "data/data/com.couchbase.cblite.test/cache");

        return rootDirectory;
    }
}
