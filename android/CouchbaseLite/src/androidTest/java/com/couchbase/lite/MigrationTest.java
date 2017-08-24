package com.couchbase.lite;

import android.support.test.InstrumentationRegistry;

import com.couchbase.lite.utils.ZipUtils;
import com.couchbase.litecore.C4BlobKey;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorBusy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MigrationTest extends BaseTest {
    private static final String TAG = MigrationTest.class.getName();

    //---------------------------------------------
    //  setUp/tearDown
    //---------------------------------------------

    @Before
    public void setUp() throws Exception {
        Log.i("MigrationTest", "setUp");
        context = InstrumentationRegistry.getTargetContext();
    }

    @After
    public void tearDown() throws Exception {
        Log.i("MigrationTest", "tearDown");
    }

    /**
     * Tool to generate test db
     */
    //NOTE: @Test
    public void testPrepareDB() throws CouchbaseLiteException {
        Database db = new Database("android-sqlite", new DatabaseConfiguration(context));
        try {
            for (int i = 1; i <= 2; i++) {
                Document doc = new Document("doc" + i);
                doc.setObject("key", String.valueOf(i));
                byte[] attach = String.format(Locale.ENGLISH, "attach%d", i).getBytes();
                Blob blob = new Blob("text/plain", attach);
                doc.setObject("attach" + i, blob);
                db.save(doc);
            }
        } finally {
            db.close();
        }
    }

    // TODO: 1.x DB's attachment is not automatically ditected as blob
    @Test
    public void testOpenExsitingDBv1x() throws Exception {

        // https://github.com/couchbase/couchbase-lite-android/issues/1237

        // if db exist, delete it
        deleteDB("android-sqlite", context.getFilesDir());

        ZipUtils.unzip(getAsset("replacedb/android140-sqlite.cblite2.zip"), context.getFilesDir());

        Database db = new Database("android-sqlite", new DatabaseConfiguration(context));
        try {
            assertEquals(2, db.getCount());
            for (int i = 1; i <= 2; i++) {
                Document doc = db.getDocument("doc" + i);
                assertNotNull(doc);
                assertEquals(String.valueOf(i), doc.getString("key"));

                Dictionary attachments = doc.getDictionary("_attachments");
                assertNotNull(attachments);
                String key = "attach" + i;

                // NOTE: following is temporary solution as Upgrader does not set @type = blob.
                Dictionary content = attachments.getDictionary(key);
                assertNotNull(content);
                String digest = content.getString("digest");
                C4BlobKey blobKey = new C4BlobKey(digest);
                db.getBlobStore().getFilePath(blobKey);
                Blob blob = new Blob(db, content.toMap());

                // TODO: OR
                //Blob blob = attachments.getBlob(key);

                assertNotNull(blob);
                byte[] attach = String.format(Locale.ENGLISH, "attach%d", i).getBytes();
                Arrays.equals(attach, blob.getContent());
            }
        } finally {
            // close db
            db.close();
            // if db exist, delete it
            deleteDB("android-sqlite", context.getFilesDir());
        }
    }

    @Test
    public void testOpenExsitingDBv1xNoAttachment() throws Exception {
        // https://github.com/couchbase/couchbase-lite-android/issues/1237

        // if db exist, delete it
        deleteDB("android-sqlite", context.getFilesDir());

        ZipUtils.unzip(getAsset("replacedb/android140-sqlite-noattachment.cblite2.zip"), context.getFilesDir());

        Database db = new Database("android-sqlite", new DatabaseConfiguration(context));
        try {
            assertEquals(2, db.getCount());
            for (int i = 1; i <= 2; i++) {
                Document doc = db.getDocument("doc" + i);
                assertNotNull(doc);
                assertEquals(String.valueOf(i), doc.getString("key"));
            }
        } finally {
            // close db
            db.close();
            // if db exist, delete it
            deleteDB("android-sqlite", context.getFilesDir());
        }
    }

    @Test
    public void testOpenExsitingDB() throws Exception {
        // if db exist, delete it
        deleteDB("android-sqlite", context.getFilesDir());

        ZipUtils.unzip(getAsset("replacedb/android200-sqlite.cblite2.zip"), context.getFilesDir());

        Database db = new Database("android-sqlite", new DatabaseConfiguration(context));
        try {
            assertEquals(2, db.getCount());
            for (int i = 1; i <= 2; i++) {
                Document doc = db.getDocument("doc" + i);
                assertNotNull(doc);
                assertEquals(String.valueOf(i), doc.getString("key"));
                Blob blob = doc.getBlob("attach" + i);
                assertNotNull(blob);
                byte[] attach = String.format(Locale.ENGLISH, "attach%d", i).getBytes();
                Arrays.equals(attach, blob.getContent());
            }
        } finally {
            // close db
            db.close();
            // if db exist, delete it
            deleteDB("android-sqlite", context.getFilesDir());
        }
    }

    //  // if db exist, delete it
    private void deleteDB(String name, File dir) throws CouchbaseLiteException {
        // database exist, delete it
        if (Database.exists("android-sqlite", context.getFilesDir())) {
            // sometimes, db is still in used, wait for a while. Maximum 3 sec
            for (int i = 0; i < 10; i++) {
                try {
                    Database.delete("android-sqlite", dir);
                    break;
                } catch (CouchbaseLiteException ex) {
                    if (ex.getCode() == kC4ErrorBusy) {
                        try {
                            Thread.sleep(300);
                        } catch (Exception e) {
                        }
                    } else {
                        throw ex;
                    }
                }
            }
        }
    }
}
