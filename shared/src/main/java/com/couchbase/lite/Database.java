package com.couchbase.lite;

import com.couchbase.lite.internal.bridge.LiteCoreBridge;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.litecore.LiteCoreException;

import java.io.File;

public final class Database {
    //---------------------------------------------
    // static variables
    //---------------------------------------------
    private static final String DB_EXTENSION = "cblite2";

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private String name;
    private String path;
    private DatabaseOptions options;
    // TODO: class name is conflicting between API level and LiteCore
    private com.couchbase.litecore.Database db;

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    public Database(String name) throws CouchbaseLiteException {
        this.name = name;
    }

    public Database(String name, DatabaseOptions options) throws CouchbaseLiteException {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public void close() throws CouchbaseLiteException {

    }

    public void changeEncryptionKey(Object key) throws CouchbaseLiteException {

    }

    public void delete() throws CouchbaseLiteException {

    }

    // TODO: dir -> String or File
    public static boolean delete(String name, File directory) throws CouchbaseLiteException {
        return false;
    }

    // TODO: dir -> String or File
    public static boolean documentExists(String name, File directory) throws CouchbaseLiteException {
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

    private boolean open() {
        if (db != null) return true;

        File dir = options.getDirectory() != null ? options.getDirectory() : getDefaultDirectory();
        if (setupDirectory(dir))
            return false;

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

        Log.i(Log.DATABASE, "Opening %s at path %s", this, dbFile.getPath());

        try {
            // TODO: com.couchbase.litecore.Database is same class name with this classname.
            //       Need to change the name.
            com.couchbase.litecore.Database db = new com.couchbase.litecore.Database(
                    dbFile.getPath(),
                    databaseFlags,
                    encryptionAlgorithm,
                    encryptionKey);
        } catch (LiteCoreException ex) {
            throw LiteCoreBridge.convertException(ex);
        }

        // TODO: Other settings

        return true;
    }

    private File getDefaultDirectory() {
        return null;
    }

    private boolean setupDirectory(File dir) {
        return true;
    }

    private static File getDatabasePath(File dir, String name){
        name = name.replaceAll("/",":"); // TODO: This does not work with Windows platform.
        return new File(dir, name);
    }
}
