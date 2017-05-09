/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite;

import android.os.Handler;
import android.os.Looper;

import com.couchbase.lite.internal.Misc;
import com.couchbase.lite.internal.bridge.LiteCoreBridge;
import com.couchbase.litecore.C4DatabaseChange;
import com.couchbase.litecore.C4DatabaseObserver;
import com.couchbase.litecore.C4DatabaseObserverListener;
import com.couchbase.litecore.C4DocumentObserver;
import com.couchbase.litecore.C4DocumentObserverListener;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.NativeLibraryLoader;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.synchronizedSet;

/**
 * A Couchbase Lite database.
 */
public final class Database {

    //---------------------------------------------
    // Load LiteCore library and its dependencies
    //---------------------------------------------
    static {
        NativeLibraryLoader.load();
    }

    //---------------------------------------------
    // static variables
    //---------------------------------------------
    private static final String TAG = Log.DATABASE;
    private static final String DB_EXTENSION = "cblite2";
    private static final int MAX_CHANGES = 100;

    private static final int DEFAULT_DATABASE_FLAGS
            = com.couchbase.litecore.Database.Create
            | com.couchbase.litecore.Database.AutoCompact
            | com.couchbase.litecore.Database.Bundle
            | com.couchbase.litecore.Database.SharedKeys;

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private String name;
    private DatabaseConfiguration config;
    // TODO: class name is conflicting between API level and LiteCore
    private com.couchbase.litecore.Database c4db;

    private ConflictResolver conflictResolver;

    private Set<DatabaseChangeListener> dbChangeListeners;
    private C4DatabaseObserver c4DBObserver;
    private Map<String, Set<DocumentChangeListener>> docChangeListeners;
    private Map<String, C4DocumentObserver> c4DocObservers;

    private SharedKeys sharedKeys;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Construct a  Database with a given name and database config.
     * If the database does not yet exist, it will be created.
     *
     * @param name The name of the database. May NOT contain capital letters!
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public Database(String name) throws CouchbaseLiteException {
        throw new UnsupportedOperationException();
    }

    /**
     * Construct a  Database with a given name and database config.
     * If the database does not yet exist, it will be created, unless the `readOnly` option is used.
     *
     * @param name    The name of the database. May NOT contain capital letters!
     * @param config The database config, or null for the default config.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the open operation.
     */
    public Database(String name, DatabaseConfiguration config) throws CouchbaseLiteException {
        if (name == null || name.length() == 0)
            throw new IllegalArgumentException("name should not be empty.");
        if (config == null)
            throw new IllegalArgumentException("DatabaseConfiguration should not be null.");

        this.name = name;
        this.config = config.copy();
        open();
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    // Attributes:

    /**
     * Return the database name
     *
     * @return the database's name
     */
    public String getName() {
        return name;
    }

    /**
     * Return the database's path. If the database is closed or deleted, null value will be returned.
     *
     * @return the database's path.
     */
    public File getPath() {
        return c4db != null ? new File(c4db.getPath()) : null;
    }


    public DatabaseConfiguration getConfig(){
        return config; // TODO: Returned the copied config object
    }

    // Get document:

    /**
     * Gets or creates a Document object with the given ID.
     * The existence of the Document in the database can be checked by checking its documentExists() method.
     * Documents are cached, so there will never be more than one instance in this Database
     * object at a time with the same documentID.
     *
     * @param documentID the document ID
     * @return the Document object
     */
    public Document getDocument(String documentID) {
        return getDocument(documentID, false);
    }

    // Save document:
    public void save(Document document) {
        // TODO: Implement.
        throw new UnsupportedOperationException("Work in Progress!");
    }

    public void delete(Document document) {
        // TODO: Implement.
        throw new UnsupportedOperationException("Work in Progress!");
    }

    public void purge(Document document) {
        // TODO: Implement.
        throw new UnsupportedOperationException("Work in Progress!");
    }


    // Batch operations:

    /**
     * Runs a group of database operations in a batch. Use this when performing bulk write operations
     * like multiple inserts/updates; it saves the overhead of multiple database commits, greatly
     * improving performance.
     *
     * @param action the action which is implementation of Runnable interface
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void inBatch(Runnable action) throws CouchbaseLiteException {
        try {
            boolean commit = false;
            Log.e(TAG, "inBatch() beginTransaction()");
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
                Log.e(TAG, "inBatch() endTransaction()");
            }

            postDatabaseChanged();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }


    // Compaction:

    public void compact(){
        // TODO: Implement.
        throw new UnsupportedOperationException("Work in Progress!");
    }

    // Database changes:
    public void addChangeListener(DatabaseChangeListener listener) {
        if (listener == null)
            throw new IllegalArgumentException();
        addDatabaseChangeListener(listener);
    }

    public void removeChangeListener(DatabaseChangeListener listener) {
        if (listener == null)
            throw new IllegalArgumentException();
        removeDatabaseChangeListener(listener);
    }

    // Document changes:
    public void addChangeListener(String docID, DocumentChangeListener listener) {
        if (docID == null || listener == null)
            throw new IllegalArgumentException();
        addDocumentChangeListener(docID, listener);
    }

    public void removeChangeListener(String docID, DocumentChangeListener listener) {
        if (docID == null || listener == null)
            throw new IllegalArgumentException();
        removeDocumentChangeListener(docID, listener);
    }

    // Fragment / Subscription:
    // NOTE: Java does not support Fragment

    // Others:

    /**
     * Closes a database.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void close() throws CouchbaseLiteException {
        if (c4db == null)
            return;

        Log.i(TAG, "Closing %s at path %s", this, c4db.getPath());

//        if (unsavedDocuments.size() > 0)
//            Log.w(TAG, "Closing database with %d unsaved docs", unsavedDocuments.size());

//        documents.clear();
//        documents = null;
//        unsavedDocuments.clear();
//        unsavedDocuments = null;

        // close db
        closeC4DB();

        // release instances
        freeC4Observers();
        freeC4DB();
    }

    /**
     * Deletes a database.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void delete() throws CouchbaseLiteException {
        deleteC4DB();
        freeC4Observers();
        freeC4DB();
    }

    // Maintenance operations:

    /**
     * Changes the database's encryption key, or removes encryption if the new key is null.
     *
     * @param key The encryption key in the form of an String (a password) or
     *            an byte[] object exactly 32 bytes in length (a raw AES key.)
     *            If a string is given, it will be internally converted to a raw key
     *            using 64,000 rounds of PBKDF2 hashing. A null value will decrypt the database.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public static void changeEncryptionKey(Object key) throws CouchbaseLiteException {
        // TODO: Implement.
        throw new UnsupportedOperationException("Work in Progress!");
    }

    /**
     * Deletes a database of the given name in the given directory.
     *
     * @param name the database's name
     * @param directory  the path where the database is located.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public static void delete(String name, File directory) throws CouchbaseLiteException {
        File path = getDatabasePath(directory, name);
        try {
            Log.e(TAG, "delete(): path=%s", path.toString());
            com.couchbase.litecore.Database.deleteAtPath(path.getPath(), DEFAULT_DATABASE_FLAGS);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    /**
     * Checks whether a database of the given name exists in the given directory or not.
     *
     * @param name the database's name
     * @param directory  the path where the database is located.
     * @return true if exists, false otherwise.
     */
    public static boolean exists(String name, File directory) {
        if (name == null || directory == null)
            throw new IllegalArgumentException("name and/or dir arguments are null.");
        return getDatabasePath(directory, name).exists();
    }






    /**
     * Creates a new Document object with no properties and a new (random) UUID.
     * The document will be saved to the database when you call save() on it.
     *
     * @return the Document object
     */
    public Document getDocument() {
        return getDocument(generateDocID());
    }


//    /**
//     * Checks whether a document of the given document ID exists in the database or not.
//     *
//     * @param docID the document ID
//     * @return true if exists, false otherwise
//     */
//    public boolean documentExists(String docID) {
//        try {
//            getDocument(docID, true);
//            return true;
//        } catch (CouchbaseLiteException e) {
//            if (e.getDomain() == LiteCoreDomain && e.getCode() == kC4ErrorNotFound)
//                return false;
//
//            // unexpected error...
//            Log.w(TAG, "Unexpected Error with calling documentExists(docID => %s) method.", e, docID);
//            return false;
//        }
//    }


    // TODO: Following methods should go somewhere.

//    /**
//     * Return the conflict resolver for this database
//     *
//     * @return the conflict resolver for this database
//     */
//    public ConflictResolver getConflictResolver() {
//        return conflictResolver;
//    }
//
//    /**
//     * Set the conflict resolver for this database. If null, a default algorithm will be used, where
//     * the revision with more history wins.
//     *
//     * @param conflictResolver the conflict resolver for this database
//     */
//    public void setConflictResolver(ConflictResolver conflictResolver) {
//        this.conflictResolver = conflictResolver;
//    }
//
//
//
//    /**
//     * Creates a value index (type IndexType.Value) on the given expressions. This will
//     * speed up queries that queries that test the expressions, at the expense of making
//     * database writes a little bit slower.
//     *
//     * @param expressions Expressions to index, typically property expressions.
//     * @throws CouchbaseLiteException if there is an error occurred.
//     */
//    public void createIndex(List<Expression> expressions) throws CouchbaseLiteException {
//        createIndex(expressions, IndexType.Value, null);
//    }
//
//    /**
//     * Creates an index based on the given expressions, index type, and index config. This will
//     * speed up queries that queries that test the expressions, at the expense of making
//     * database writes a little bit slower.
//     *
//     * @param expressions Expressions to index, typically property expressions.
//     * @param type        Type of index to create (Value, FullText or Geo.)
//     * @param options     Options affecting the index, or {@code null} for default settings.
//     * @throws CouchbaseLiteException if there is an error occurred.
//     */
//
//    public void createIndex(List<Expression> expressions,
//                            IndexType type,
//                            IndexOptions options) throws CouchbaseLiteException {
//        if (expressions == null)
//            throw new IllegalArgumentException("expressions parameter cannot be null");
//
//        List<Object> list = new ArrayList<Object>();
//        for (Expression exp : expressions) {
//            list.add(exp.asJSON());
//        }
//
//        try {
//            String json = JsonUtils.toJson(list).toString();
//            String language = options != null ? options.getLanguage() : null;
//            boolean ignoreDiacritics = options != null ? options.isIgnoreDiacritics() : false;
//            c4db.createIndex(json, type.getValue(), language, ignoreDiacritics);
//        } catch (JSONException e) {
//            throw new CouchbaseLiteException(e);
//        } catch (LiteCoreException e) {
//            throw LiteCoreBridge.convertException(e);
//        }
//    }


    //---------------------------------------------
    // Override public method
    //---------------------------------------------

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s[%s]", super.toString(), name);
    }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    @Override
    protected void finalize() throws Throwable {
        freeC4Observers();
        freeC4DB();
        super.finalize();
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    long documentCount(){
        return c4db.getDocumentCount();
    }



    // Instead of clone()
    /* package */ Database copy() {
        return new Database(this.name, this.config);
    }

    //////// DATABASES:
    com.couchbase.litecore.Database internal() {
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

    SharedKeys getSharedKeys() {
        return sharedKeys;
    }

    //////// DOCUMENTS:
    com.couchbase.litecore.Document read(String docID, boolean mustExist) throws CouchbaseLiteException {
        try {
            return c4db.getDocument(docID, mustExist);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    //////// DATABASES:

    private void open() throws CouchbaseLiteException {
        if (c4db != null)
            return;

        File dir = config.getDirectory() != null ? config.getDirectory() : getDefaultDirectory();
        setupDirectory(dir);

        File dbFile = getDatabasePath(dir, name);

        // TODO:
        int databaseFlags = getDatabaseFlags();

        // TODO:
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

        sharedKeys = new SharedKeys(c4db);
        c4DBObserver = new C4DatabaseObserver(c4db, new C4DatabaseObserverListener() {
            @Override
            public void callback(C4DatabaseObserver observer, Object context) {
                new Handler(Looper.getMainLooper())
                        .post(new Runnable() {
                            @Override
                            public void run() {
                                postDatabaseChanged();
                            }
                        });
            }
        }, this);
        dbChangeListeners = synchronizedSet(new HashSet<DatabaseChangeListener>());
        docChangeListeners = Collections.synchronizedMap(new HashMap<String, Set<DocumentChangeListener>>());
        c4DocObservers = Collections.synchronizedMap(new HashMap<String, C4DocumentObserver>());
    }

    private int getDatabaseFlags() {
        int databaseFlags = DEFAULT_DATABASE_FLAGS;
//        if (config.isReadOnly())
//            databaseFlags |= com.couchbase.litecore.Database.ReadOnly;
        return databaseFlags;
    }

    private File getDefaultDirectory() {
        throw new UnsupportedOperationException("getDefaultDirectory() is not supported.");
    }

    private void setupDirectory(File dir) throws CouchbaseLiteException {
        if (!dir.exists()) {
            dir.mkdirs();
        }
        if (!dir.isDirectory()) {
            throw new CouchbaseLiteException(String.format(Locale.ENGLISH,
                    "Unable to create directory for: %s", dir));
        }
    }

    private static File getDatabasePath(File dir, String name) {
        // TODO:
        name = name.replaceAll("/", ":");
        name = String.format(Locale.ENGLISH, "%s.%s", name, DB_EXTENSION);
        return new File(dir, name);
    }

    private static String generateDocID() {
        return Misc.CreateUUID();
    }

    //////// DOCUMENTS:

    private Document getDocument(String docID, boolean mustExist) throws CouchbaseLiteException {
//        Document doc = documents.get(docID);
//        if (doc == null) {
//            // TODO: I don't think calling Database method from Document consturctor is straightforward.
//            doc = new Document(this, docID, mustExist);
//            documents.put(docID, doc);
//        } else {
//            if (mustExist && !doc.exists()) {
//                // Don't return a pre-instantiated CBLDocument if it doesn't exist
//                throw new CouchbaseLiteException(LiteCoreDomain, kC4ErrorNotFound);
//            }
//        }
//        return doc;

        //TODO:
        return null;
    }


    // --- C4Database
    private void closeC4DB() throws CouchbaseLiteException {
        try {
            c4db.close();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    private void deleteC4DB() throws CouchbaseLiteException {
        try {
            c4db.delete();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    private void freeC4DB() {
        if (c4db != null) {
            c4db.free();
            c4db = null;
        }
    }

    // --- Notification: - C4DatabaseObserver/C4DocumentObserver

    // Database changes:
    private void addDatabaseChangeListener(DatabaseChangeListener listener) {
        if (dbChangeListeners.isEmpty())
            registerC4DBObserver();
        dbChangeListeners.add(listener);
    }

    private void removeDatabaseChangeListener(DatabaseChangeListener listener) {
        dbChangeListeners.remove(listener);
        if (dbChangeListeners.isEmpty())
            freeC4DBObserver();
    }

    // Document changes:
    private void addDocumentChangeListener(String docID, DocumentChangeListener listener) {
        if (docChangeListeners.containsKey(docID)) {
            docChangeListeners.get(docID).add(listener);
        } else {
            Set<DocumentChangeListener> listeners = Collections.synchronizedSet(new HashSet<DocumentChangeListener>());
            listeners.add(listener);
            docChangeListeners.put(docID, listeners);
            registerC4DocObserver(docID);
        }
    }

    private void removeDocumentChangeListener(String docID, DocumentChangeListener listener) {
        if (docChangeListeners.containsKey(docID)) {
            Set<DocumentChangeListener> listeners = docChangeListeners.get(docID);
            if (listeners.remove(listener)) {
                if (listeners.isEmpty()) {
                    docChangeListeners.remove(docID);
                    removeC4DocObserver(docID);
                }
            }
        }
    }

    private void registerC4DBObserver() {
        c4DBObserver = new C4DatabaseObserver(c4db, new C4DatabaseObserverListener() {
            @Override
            public void callback(C4DatabaseObserver observer, Object context) {
                new Handler(Looper.getMainLooper())
                        .post(new Runnable() {
                            @Override
                            public void run() {
                                postDatabaseChanged();
                            }
                        });
            }
        }, this);
    }

    private void freeC4DBObserver() {
        if (c4DBObserver != null) {
            c4DBObserver.free();
            c4DBObserver = null;
        }
    }

    private void registerC4DocObserver(String docID) {
        // prevent multiple observer for same docID.
        if (c4DocObservers.containsKey(docID))
            return;

        C4DocumentObserver docObserver = new C4DocumentObserver(c4db, docID, new C4DocumentObserverListener() {
            @Override
            public void callback(C4DocumentObserver observer, final String docID, final long sequence, Object context) {
                new Handler(Looper.getMainLooper())
                        .post(new Runnable() {
                            @Override
                            public void run() {
                                postDocumentChanged(docID, sequence);
                            }
                        });
            }
        }, this);

        c4DocObservers.put(docID, docObserver);
    }

    private void removeC4DocObserver(String docID) {
        C4DocumentObserver docObs = c4DocObservers.get(docID);
        if (docObs != null) {
            docObs.free();
        }
        c4DocObservers.remove(docID);
    }

    private void freeC4DocObservers() {
        if (c4DocObservers != null) {
            for (C4DocumentObserver value : c4DocObservers.values()) {
                if (value != null)
                    value.free();
            }
            c4DocObservers.clear();
            c4DocObservers = null;
        }
        if (docChangeListeners != null) {
            docChangeListeners.clear();
            docChangeListeners = null;
        }
    }

    private void freeC4Observers() {
        freeC4DBObserver();
        freeC4DocObservers();
    }

    private void postDatabaseChanged() {
        if (c4DBObserver == null || c4db == null || c4db.isInTransaction())
            return;

        int nChanges = 0;
        List<String> documentIDs = new ArrayList<>();
        //long lastSequence = 0L;
        boolean external = false;
        do {
            // Read changes in batches of kMaxChanges:
            C4DatabaseChange[] c4DBChanges = c4DBObserver.getChanges(MAX_CHANGES);
            nChanges = c4DBChanges.length;
            boolean newExternal = nChanges > 0 ? c4DBChanges[0].isExternal() : false;
            if (c4DBChanges == null || c4DBChanges.length == 0 || external != newExternal || documentIDs.size() > 1000) {
                if (documentIDs.size() > 0) {
                    // Only notify if there are actually changes to send
                    DatabaseChange change = new DatabaseChange(this, documentIDs/*, lastSequence, external*/);
                    // not allow to add/remove middle of iteration
                    synchronized (dbChangeListeners) {
                        for (DatabaseChangeListener listener : dbChangeListeners) {
                            listener.changed(change);
                        }
                    }
                    documentIDs.clear();
                }
            }

            external = newExternal;
            for (int i = 0; i < nChanges; i++) {
                documentIDs.add(c4DBChanges[i].getDocID());
            }
//            if (nChanges > 0)
//                lastSequence = c4DBChanges[nChanges - 1].getSequence();
        } while (nChanges > 0);
    }

    private void postDocumentChanged(String documentID, long sequence) {
        // TODO: Temporary commented out the inTransaction check.
        // https://github.com/couchbase/couchbase-lite-android/issues/1154
        if (!c4DocObservers.containsKey(documentID) || c4db == null /*|| c4db.isInTransaction()*/)
            return;

        Set<DocumentChangeListener> listeners = docChangeListeners.get(documentID);
        if (listeners == null)
            return;

        DocumentChange change = new DocumentChange(documentID/*, sequence*/);
        synchronized (listeners) {
            for (DocumentChangeListener listener : listeners) {
                listener.changed(change);
            }
        }
    }
}







