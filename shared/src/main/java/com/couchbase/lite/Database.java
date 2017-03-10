package com.couchbase.lite;

import com.couchbase.lite.internal.bridge.LiteCoreBridge;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.litecore.LiteCoreException;

import java.io.File;
import java.util.Locale;

import static android.R.attr.path;

public final class Database {
    //---------------------------------------------
    // static variables
    //---------------------------------------------
    private static final String TAG = Log.DATABASE;
    private static final String DB_EXTENSION = "cblite2";


    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private String name;
    private DatabaseOptions options;
    // TODO: class name is conflicting between API level and LiteCore
    private com.couchbase.litecore.Database db;

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    public Database(String name) throws CouchbaseLiteException {
        this(name, DatabaseOptions.getDefaultOptions());
    }

    public Database(String name, DatabaseOptions options) throws CouchbaseLiteException {
        this.name = name;
        this.options = options != null ? options : DatabaseOptions.getDefaultOptions();
        open();
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return db != null ? db.getPath() : null;
    }

    public void close() throws CouchbaseLiteException {
        if(db == null) return;

        Log.i(TAG, "Closing %s at path %s", this, path);

        // TODO:

        try {
            db.close();
            db = null;
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }

        // success
    }

    public void changeEncryptionKey(Object key) throws CouchbaseLiteException {

    }

    public void delete() throws CouchbaseLiteException {
        // TODO: need to review Database.delete() and free()
        try {
            db.delete();
        } catch (LiteCoreException e) {
            e.printStackTrace();
        }
        db.free();
        db = null;
    }

    // TODO: dir -> String or File
    public static void delete(String name, File dir) throws CouchbaseLiteException {
        File path = getDatabasePath(dir, name);
        try {
            com.couchbase.litecore.Database.deleteAtPath(path.getPath());
        }catch (LiteCoreException e){
            throw LiteCoreBridge.convertException(e);
        }

    }

    // TODO: dir -> String or File
    public static boolean documentExists(String name, File dir) throws CouchbaseLiteException {
        return false;
    }

    public Document getDocument() {
        return null;
    }

    public Document getDocument(String docID) {
        return null;
    }

    // TODO: Model will be implemented later
    // func getDocument<T:DocumentModel>(type: T.Type) -> T
    // func getDocument<T:DocumentModel>(id: String?, type: T.Type) -> T

    public boolean documentExists(String docID) {
        return false;
    }

    public void inBatch(Runnable action) throws CouchbaseLiteException {

    }

    // TODO:
    // var conflictResolver: ConflictResolver? { get set }

    // TODO: Notification will be implemented in DB4
    // func addChangeListener(docListener: DocumentChangeListener)
    // func removeChangeListener(docListener: DocumentChangeListener)


    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    private void open() throws CouchbaseLiteException {
        if (db != null) return;

        File dir = options.getDirectory() != null ? options.getDirectory() : getDefaultDirectory();
        setupDirectory(dir);

        File dbFile = getDatabasePath(dir, name);

        // databaseFlags
        int databaseFlags;
        if (options.isReadOnly())
            databaseFlags = com.couchbase.litecore.Database.ReadOnly;
        else
            databaseFlags = com.couchbase.litecore.Database.Create;

        // TODO: encryptionAlgorithm, encryptionKey
        int encryptionAlgorithm = com.couchbase.litecore.Database.NoEncryption;
        byte[] encryptionKey = null;

        Log.i(TAG, "Opening %s at path %s", this, dbFile.getPath());

        try {
            // TODO: com.couchbase.litecore.Database is same class name with this classname.
            //       Need to change the name.
            db = new com.couchbase.litecore.Database(
                    dbFile.getPath(),
                    databaseFlags,
                    encryptionAlgorithm,
                    encryptionKey);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }

        // TODO: Other settings

        // success
    }

    private File getDefaultDirectory() {
        // TODO:
        return null;
    }

    private void setupDirectory(File dir) throws CouchbaseLiteException {
        if (!dir.exists()) {
            dir.mkdirs();
        }
        if (!dir.isDirectory()) {
            throw new CouchbaseLiteException(String.format(Locale.ENGLISH, "Unable to create directory for: %s", dir));
        }
    }

    private static File getDatabasePath(File dir, String name){
        name = name.replaceAll("/", ":"); // TODO: This does not work with Windows platform.
        name = String.format(Locale.ENGLISH, "%s.%s", name, DB_EXTENSION);
        return new File(dir, name);
    }
}







