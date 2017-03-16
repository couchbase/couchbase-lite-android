package com.couchbase.lite;

import com.couchbase.lite.internal.Misc;
import com.couchbase.lite.internal.bridge.LiteCoreBridge;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.support.WeakValueHashMap;
import com.couchbase.litecore.LiteCoreException;

import java.io.File;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import static android.R.attr.path;
import static com.couchbase.litecore.Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.Constants.LiteCoreError.kC4ErrorNotFound;

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
    private com.couchbase.litecore.Database c4db;
    // Unmodified Document Cache from DB
    private WeakValueHashMap<String, Document> documents;
    // Modified (Unsaved) Document Cache before save.
    private Set<Document> unsavedDocuments;

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    public Database(String name, DatabaseOptions options) throws CouchbaseLiteException {
        this.name = name;
        this.options = options != null ? options : DatabaseOptions.getDefaultOptions();
        open();
    }

    public Database(String name) throws CouchbaseLiteException {
        this(name, DatabaseOptions.getDefaultOptions());
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return c4db != null ? c4db.getPath() : null;
    }

    public void close() throws CouchbaseLiteException {
        if(c4db == null) return;

        Log.i(TAG, "Closing %s at path %s", this, path);

        if(unsavedDocuments.size() > 0)
            Log.w(TAG, "Closing database with %d unsaved docs", unsavedDocuments.size());

        documents.clear();
        documents = null;
        unsavedDocuments.clear();
        unsavedDocuments = null;

        try {
            c4db.close();
            c4db = null;
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }

        // TODO:

        // Success:
    }

    public void changeEncryptionKey(Object key) throws CouchbaseLiteException {

    }

    public void delete() throws CouchbaseLiteException {
        // TODO: need to review Database.delete() and free()
        try {
            c4db.delete();
        } catch (LiteCoreException e) {
            e.printStackTrace();
        }
        c4db.free();
        c4db = null;
    }

    // TODO: dir -> String or File
    public static void delete(String name, File dir) throws CouchbaseLiteException {
        File path = getDatabasePath(dir, name);
        try {
            Log.e(TAG, "delete(): path=%s",path.toString());
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
        return getDocument(generateDocID());
    }

    public Document getDocument(String docID) {
        return getDocument(docID, false);
    }

    // TODO: Model will be implemented later
    // func getDocument<T:DocumentModel>(type: T.Type) -> T
    // func getDocument<T:DocumentModel>(id: String?, type: T.Type) -> T

    public boolean documentExists(String docID) {
        try {
            getDocument(docID, true);
            return true;
        } catch (CouchbaseLiteException e) {
            if (e.getDomain() == LiteCoreDomain && e.getCode() == kC4ErrorNotFound)
                return false;

            // unexpected error...
            Log.w(TAG, "Unexpected Error with calling documentExists(docID => %s) method.", e, docID);
            return false;
        }
    }

    public void inBatch(Runnable action) throws CouchbaseLiteException {
        try {
            boolean commit = false;
            c4db.beginTransaction();
            try {
                try {
                    action.run();
                    commit = true;
                } catch (RuntimeException e) {
                    if (e instanceof CouchbaseLiteException)
                        throw e;
                    throw new CouchbaseLiteException(e);
                }
            } finally {
                c4db.endTransaction(commit);
            }
            // TODO: [self postDatabaseChanged];
        }catch(LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    // TODO:
    // var conflictResolver: ConflictResolver? { get set }

    // TODO: Notification will be implemented in DB4
    // func addChangeListener(docListener: DocumentChangeListener)
    // func removeChangeListener(docListener: DocumentChangeListener)

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s[%s]", super.toString(), name);
    }


    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    //////// DATABASES:
    com.couchbase.litecore.Database internal(){
        return c4db;
    }
    void beginTransaction() throws CouchbaseLiteException {
        try {
            c4db.beginTransaction();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    void endTransaction(boolean commit) throws CouchbaseLiteException {
        try {
            c4db.endTransaction(commit);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    //////// DOCUMENTS:
    com.couchbase.litecore.Document read(String docID, boolean mustExist) throws CouchbaseLiteException {
        try {
            return c4db.getDocument(docID, mustExist);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    void unsavedDocument(Document doc, boolean unsaved) {
        if (unsaved)
            unsavedDocuments.add(doc);
        else
            unsavedDocuments.remove(doc);
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    //////// DATABASES:

    private void open() throws CouchbaseLiteException {
        if (c4db != null) return;

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
            c4db = new com.couchbase.litecore.Database(
                    dbFile.getPath(),
                    databaseFlags,
                    encryptionAlgorithm,
                    encryptionKey);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }

        // TODO: Other settings

        documents = new WeakValueHashMap<>();
        unsavedDocuments = new HashSet<>();

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

    private static String generateDocID() {
        return Misc.CreateUUID();
    }

    //////// DOCUMENTS:

    private Document getDocument(String docID, boolean mustExist) throws CouchbaseLiteException {
        Document doc = documents.get(docID);
        if (doc == null) {
            // TODO: I don't think calling Database method from DocumentImpl consturctor is straightforward.
            doc = new DocumentImpl(this, docID, mustExist);
            documents.put(docID, doc);
        } else {
            if (mustExist && !doc.exists()) {
                // Don't return a pre-instantiated CBLDocument if it doesn't exist
                throw new CouchbaseLiteException(LiteCoreDomain, kC4ErrorNotFound);
            }
        }
        return doc;
    }
}







