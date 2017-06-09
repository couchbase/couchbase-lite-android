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
import com.couchbase.lite.internal.support.JsonUtils;
import com.couchbase.litecore.C4DatabaseChange;
import com.couchbase.litecore.C4DatabaseObserver;
import com.couchbase.litecore.C4DatabaseObserverListener;
import com.couchbase.litecore.C4DocumentObserver;
import com.couchbase.litecore.C4DocumentObserverListener;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.NativeLibraryLoader;
import com.couchbase.litecore.SharedKeys;

import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.couchbase.lite.Status.CBLErrorDomain;
import static com.couchbase.lite.Status.Forbidden;
import static com.couchbase.litecore.Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.Constants.LiteCoreError.kC4ErrorNotFound;
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
     * If the database does not yet exist, it will be created, unless the `readOnly` option is used.
     *
     * @param name   The name of the database. May NOT contain capital letters!
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
        return c4db != null ? new File(getC4Database().getPath()) : null;
    }


    /**
     * The number of documents in the database.
     *
     * @return the number of documents in the database, -1 if error.
     */
    public int getCount() {
        return c4db != null ? (int) getC4Database().getDocumentCount() : -1;
    }

    /**
     * Returned the copied config object
     *
     * @return the copied config object
     */
    public DatabaseConfiguration getConfig() {
        return config; // TODO: Returned the copied config object
    }

    // GET EXISTING DOCUMENT

    /**
     * Gets an existing Document object with the given ID. If the document with the given ID doesn't
     * exist in the database, the value returned will be null.
     *
     * @param documentID the document ID
     * @return the Document object
     */
    public Document getDocument(String documentID) {
        return getDocument(documentID, true);
    }

    // CHECK DOCUMENT EXISTS
    public boolean contains(String documentID) {
        return getDocument(documentID, true) != null;
    }

    // SUBSCRIPTION

    // SAVE DELETE PURGE

    /**
     * Saves the given document to the database. If the document in the database has been updated
     * since it was read by this Document, a conflict occurs, which will be resolved by invoking
     * the conflict handler. This can happen if multiple application threads are writing to the
     * database, or a pull replication is copying changes from a server.
     *
     * @param document
     */
    public void save(Document document) {
        prepareDocument(document);
        document.save();
    }

    /**
     * Delete the givin document. All properties are removed, and subsequent calls to
     * getDocument(String) will return null. Deletion adds a special "tombstone" revision
     * to the database, as bookkeeping so that the change can be replicated to other databases.
     * Thus, it does not free up all of the disk space occupied by the document.
     * To delete a document entirely (but without the ability to replicate this),
     * use purge(Document).
     *
     * @param document
     */
    public void delete(Document document) {
        prepareDocument(document);
        document.delete();
    }

    /**
     * Purges the given document from the database. This is more drastic than delete(Document),
     * it removes all traces of the document. The purge will NOT be replicated to other databases.
     *
     * @param document
     */
    public void purge(Document document) {
        prepareDocument(document);
        document.purge();
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
    public void inBatch(Runnable action) throws CouchbaseLiteException, IllegalStateException {
        try {
            boolean commit = false;
            Log.e(TAG, "inBatch() beginTransaction()");
            getC4Database().beginTransaction();
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
                getC4Database().endTransaction(commit);
                Log.e(TAG, "inBatch() endTransaction()");
            }

            postDatabaseChanged();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    // Compaction:

    /**
     * Compacts the database file by deleting unused attachment files and vacuuming the SQLite database
     */
    public void compact() throws CouchbaseLiteException, IllegalStateException {
        try {
            getC4Database().compact();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    // Database changes:

    /**
     * Set the given DatabaseChangeListener to the this database.
     *
     * @param listener
     */
    public void addChangeListener(DatabaseChangeListener listener) {
        if (listener == null)
            throw new IllegalArgumentException();
        addDatabaseChangeListener(listener);
    }

    /**
     * Remove the given DatabaseChangeListener from the this database.
     *
     * @param listener
     */
    public void removeChangeListener(DatabaseChangeListener listener) {
        if (listener == null)
            throw new IllegalArgumentException();
        removeDatabaseChangeListener(listener);
    }

    // Document changes:

    /**
     * Add the given DocumentChangeListener to the specified document.
     *
     * @param listener
     */
    public void addChangeListener(String docID, DocumentChangeListener listener) {
        if (docID == null || listener == null)
            throw new IllegalArgumentException();
        addDocumentChangeListener(docID, listener);
    }

    /**
     * Remove the given DocumentChangeListener from the specified document.
     *
     * @param listener
     */
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

        Log.i(TAG, "Closing %s at path %s", this, getC4Database().getPath());

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
        // TODO: Unable to delete the closed database
        if (c4db == null)
            return;

        // delete db
        deleteC4DB();

        // release instances
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
        // TODO:
        throw new UnsupportedOperationException("Work in Progress!");
    }

    /**
     * Deletes a database of the given name in the given directory.
     *
     * @param name      the database's name
     * @param directory the path where the database is located.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public static void delete(String name, File directory) throws CouchbaseLiteException {
        if (name == null || directory == null)
            throw new IllegalArgumentException("name and/or dir arguments are null.");

        if (!exists(name, directory))
            throw new CouchbaseLiteException(Status.CBLErrorDomain, Status.NotFound);

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
     * @param name      the database's name
     * @param directory the path where the database is located.
     * @return true if exists, false otherwise.
     */
    public static boolean exists(String name, File directory) {
        if (name == null || directory == null)
            throw new IllegalArgumentException("name and/or dir arguments are null.");
        return getDatabasePath(directory, name).exists();
    }

    /**
     * Creates a value index (type IndexType.Value) on the given expressions. This will
     * speed up queries that queries that test the expressions, at the expense of making
     * database writes a little bit slower.
     *
     * @param expressions Expressions to index, typically property expressions.
     * @throws CouchbaseLiteException if there is an error occurred.
     */
    public void createIndex(List<Expression> expressions) throws CouchbaseLiteException {
        createIndex(expressions, IndexType.Value, null);
    }

    /**
     * Creates an index based on the given expressions, index type, and index config. This will
     * speed up queries that queries that test the expressions, at the expense of making
     * database writes a little bit slower.
     *
     * @param expressions Expressions to index, typically property expressions.
     * @param type        Type of index to create (Value, FullText or Geo.)
     * @param options     Options affecting the index, or {@code null} for default settings.
     * @throws CouchbaseLiteException if there is an error occurred.
     */
    public void createIndex(List<Expression> expressions,
                            IndexType type,
                            IndexOptions options) throws CouchbaseLiteException, IllegalStateException {
        if (expressions == null)
            throw new IllegalArgumentException("expressions parameter cannot be null");

        List<Object> list = new ArrayList<Object>();
        for (Expression exp : expressions) {
            list.add(exp.asJSON());
        }

        try {
            String json = JsonUtils.toJson(list).toString();
            String language = options != null ? options.getLanguage() : null;
            boolean ignoreDiacritics = options != null ? options.isIgnoreDiacritics() : false;
            getC4Database().createIndex(json, type.getValue(), language, ignoreDiacritics);
        } catch (JSONException e) {
            throw new CouchbaseLiteException(e);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

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

    /* package */ ConflictResolver getConflictResolver() {
        return config != null ? config.getConflictResolver() : null;
    }

    // Instead of clone()
    /* package */ Database copy() {
        return new Database(this.name, this.config);
    }

    //////// DATABASES:

    /**
     * @return a reference to C4Database instance if db is open.
     * @throws IllegalStateException
     */
    com.couchbase.litecore.Database getC4Database() throws IllegalStateException {
        if (c4db == null)
            throw new IllegalStateException("Database is not opened.");
        return c4db;
    }

    /* package */ void beginTransaction() throws CouchbaseLiteException, IllegalStateException {
        try {
            getC4Database().beginTransaction();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    /* package */ void endTransaction(boolean commit) throws CouchbaseLiteException, IllegalStateException {
        try {
            getC4Database().endTransaction(commit);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    /* package */  SharedKeys getSharedKeys() {
        return sharedKeys;
    }

    //////// DOCUMENTS:
    /* package */ com.couchbase.litecore.Document read(String docID, boolean mustExist) throws CouchbaseLiteException, IllegalStateException {
        try {
            return getC4Database().getDocument(docID, mustExist);
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

    private Document getDocument(String documentID, boolean mustExist) throws CouchbaseLiteException {
        try {
            return new Document(this, documentID, mustExist);
        } catch (CouchbaseLiteException e) {
            if (e.getDomain() == LiteCoreDomain && e.getCode() == kC4ErrorNotFound)
                return null;
            else
                throw e;
        }
    }


    // --- C4Database
    private void closeC4DB() throws CouchbaseLiteException, IllegalStateException {
        try {
            getC4Database().close();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    private void deleteC4DB() throws CouchbaseLiteException, IllegalStateException {
        try {
            getC4Database().delete();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    private void freeC4DB() throws IllegalStateException {
        if (c4db != null) {
            getC4Database().free();
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
        if (c4DBObserver == null || c4db == null || getC4Database().isInTransaction())
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
        } while (nChanges > 0);
    }

    private void postDocumentChanged(String documentID, long sequence) {
        if (!c4DocObservers.containsKey(documentID) || c4db == null)
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

    private void prepareDocument(Document document) throws CouchbaseLiteException {
        if (document.getDatabase() == null)
            document.setDatabase(this);
        else if (document.getDatabase() != this)
            throw new CouchbaseLiteException(CBLErrorDomain, Forbidden);
    }
}
