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

import com.couchbase.lite.internal.bridge.LiteCoreBridge;
import com.couchbase.lite.internal.support.FileUtils;
import com.couchbase.lite.internal.support.JsonUtils;
import com.couchbase.litecore.C4;
import com.couchbase.litecore.C4BlobStore;
import com.couchbase.litecore.C4Constants;
import com.couchbase.litecore.C4Database;
import com.couchbase.litecore.C4DatabaseChange;
import com.couchbase.litecore.C4DatabaseObserver;
import com.couchbase.litecore.C4DatabaseObserverListener;
import com.couchbase.litecore.C4Document;
import com.couchbase.litecore.C4DocumentObserver;
import com.couchbase.litecore.C4DocumentObserverListener;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.NativeLibraryLoader;
import com.couchbase.litecore.SharedKeys;

import org.json.JSONException;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.couchbase.litecore.C4Constants.C4EncryptionAlgorithm.kC4EncryptionAES256;
import static com.couchbase.litecore.C4Constants.C4EncryptionAlgorithm.kC4EncryptionNone;
import static com.couchbase.litecore.C4Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorConflict;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorNotFound;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorNotOpen;
import static java.util.Collections.synchronizedSet;

/**
 * A Couchbase Lite database.
 */
public final class Database implements C4Constants {

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
            = C4DatabaseFlags.kC4DB_Create
            | C4DatabaseFlags.kC4DB_AutoCompact
            | C4DatabaseFlags.kC4DB_Bundled
            | C4DatabaseFlags.kC4DB_SharedKeys;

    //---------------------------------------------
    // enums
    //---------------------------------------------

    public enum LogDomain {
        ALL, DATABASE, QUERY, REPLICATOR, NETWORK
    }

    public enum LogLevel {
        DEBUG(Log.DEBUG),
        VERBOSE(Log.VERBOSE),
        INFO(Log.INFO),
        WARNING(Log.WARN),
        ERROR(Log.ERROR),
        NONE(Log.NONE);

        private final int value;

        LogLevel(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private String name;
    private DatabaseConfiguration config;
    private C4Database c4db;

    private Set<DatabaseChangeListener> dbChangeListeners;
    private C4DatabaseObserver c4DBObserver;
    private Map<String, Set<DocumentChangeListener>> docChangeListeners;
    private Map<String, C4DocumentObserver> c4DocObservers;

    private final SharedKeys sharedKeys;

    private Map<URI, Replicator> replications;
    private Set<Replicator> activeReplications;

    private final Object lock = new Object(); // lock for thread-safety

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
        String tempdir = config.getTempDir();
        if (tempdir != null)
            C4.setenv("TMPDIR", tempdir, 1);
        open();
        this.sharedKeys = new SharedKeys(c4db);

        this.replications = new HashMap<>();
        this.activeReplications = new HashSet<>();
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
        synchronized (lock) {
            return c4db != null ? new File(getC4Database().getPath()) : null;
        }
    }


    /**
     * The number of documents in the database.
     *
     * @return the number of documents in the database, 0 if database is closed.
     */
    public int getCount() {
        synchronized (lock) {
            return c4db != null ? (int) getC4Database().getDocumentCount() : 0;
        }
    }

    /**
     * Returned the copied config object
     *
     * @return the copied config object
     */
    public DatabaseConfiguration getConfig() {
        return config.copy();
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
        if (documentID == null)
            throw new IllegalArgumentException("a documentID parameter is null");

        synchronized (lock) {
            return getDocument(documentID, true);
        }
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
    public void save(Document document) throws CouchbaseLiteException {
        if (document == null)
            throw new IllegalArgumentException("a document parameter is null");

        synchronized (lock) {
            prepareDocument(document);
            document.save();
        }
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
    public void delete(Document document) throws CouchbaseLiteException {
        if (document == null)
            throw new IllegalArgumentException("a document parameter is null");

        synchronized (lock) {
            prepareDocument(document);
            document.delete();
        }
    }

    /**
     * Purges the given document from the database. This is more drastic than delete(Document),
     * it removes all traces of the document. The purge will NOT be replicated to other databases.
     *
     * @param document
     */
    public void purge(Document document) throws CouchbaseLiteException {
        if (document == null)
            throw new IllegalArgumentException("a document parameter is null");

        synchronized (lock) {
            prepareDocument(document);
            document.purge();
        }
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
        synchronized (lock) {
            mustBeOpen();

            try {
                boolean commit = false;
                Log.v(TAG, "inBatch() beginTransaction()");
                getC4Database().beginTransaction();
                try {
                    try {
                        action.run();
                        commit = true;
                    } catch (RuntimeException e) {
                        throw new CouchbaseLiteException(e);
                    }
                } finally {
                    getC4Database().endTransaction(commit);
                    Log.v(TAG, "inBatch() endTransaction()");
                }

                new Handler(Looper.getMainLooper())
                        .post(new Runnable() {
                            @Override
                            public void run() {
                                postDatabaseChanged();
                            }
                        });
            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertException(e);
            }
        }
    }

    // Compaction:

    /**
     * Compacts the database file by deleting unused attachment files and vacuuming the SQLite database
     */
    public void compact() throws CouchbaseLiteException {
        synchronized (lock) {
            mustBeOpen();
            try {
                getC4Database().compact();
            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertException(e);
            }
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
            throw new IllegalArgumentException("a listener parameter is null");

        synchronized (lock) {
            mustBeOpen();
            addDatabaseChangeListener(listener);
        }
    }

    /**
     * Remove the given DatabaseChangeListener from the this database.
     *
     * @param listener
     */
    public void removeChangeListener(DatabaseChangeListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("a listener parameter is null");

        synchronized (lock) {
            mustBeOpen();
            removeDatabaseChangeListener(listener);
        }
    }

    // Document changes:

    /**
     * Add the given DocumentChangeListener to the specified document.
     *
     * @param listener
     */
    public void addChangeListener(String docID, DocumentChangeListener listener) {
        if (docID == null || listener == null)
            throw new IllegalArgumentException("a listener parameter and/or a listener parameter are null");

        synchronized (lock) {
            mustBeOpen();
            addDocumentChangeListener(docID, listener);
        }
    }

    /**
     * Remove the given DocumentChangeListener from the specified document.
     *
     * @param listener
     */
    public void removeChangeListener(String docID, DocumentChangeListener listener) {
        if (docID == null || listener == null)
            throw new IllegalArgumentException("a listener parameter and/or a listener parameter are null");

        synchronized (lock) {
            mustBeOpen();
            removeDocumentChangeListener(docID, listener);
        }
    }

    // Others:

    /**
     * Closes a database.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void close() throws CouchbaseLiteException {
        if (c4db == null)
            return;

        synchronized (lock) {
            Log.i(TAG, "Closing %s at path %s", this, getC4Database().getPath());

            // close db
            closeC4DB();

            // release instances
            freeC4Observers();
            freeC4DB();
        }
    }

    /**
     * Deletes a database.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void delete() throws CouchbaseLiteException {
        synchronized (lock) {
            mustBeOpen();

            // delete db
            deleteC4DB();

            // release instances
            freeC4Observers();
            freeC4DB();
        }
    }

    /**
     * Changes the database's encryption key, or removes encryption if the new key is null.
     *
     * @param encryptionKey The encryption key
     * @throws CouchbaseLiteException
     */
    public void setEncryptionKey(EncryptionKey encryptionKey) throws CouchbaseLiteException {
        synchronized (lock) {
            mustBeOpen();
            int keyType = encryptionKey == null || encryptionKey.getKey() == null ? kC4EncryptionNone : kC4EncryptionAES256;
            try {
                c4db.rekey(keyType, encryptionKey.getKey());
            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertException(e);
            }
        }
    }

    // Maintenance operations:

    public List<String> getIndexes() throws CouchbaseLiteException {
        synchronized (lock) {
            mustBeOpen();
            try {
                return (List<String>) SharedKeys.valueToObject(c4db.getIndexes(), sharedKeys);
            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertException(e);
            }
        }
    }

    public void createIndex(String name, Index index) throws CouchbaseLiteException {
        synchronized (lock) {
            mustBeOpen();
            try {
                String json = JsonUtils.toJson(index.items()).toString();
                getC4Database().createIndex(name, json,
                        index.type().getValue(), index.locale(), index.ignoreDiacritics());
            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertException(e);
            } catch (JSONException e) {
                throw new CouchbaseLiteException(e);
            }
        }
    }

    public void deleteIndex(String name) throws CouchbaseLiteException {
        synchronized (lock) {
            mustBeOpen();
            try {
                c4db.deleteIndex(name);
            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertException(e);
            }
        }
    }

    //---------------------------------------------
    // API - public static methods
    //---------------------------------------------

    /**
     * Deletes a database of the given name in the given directory.
     *
     * @param name      the database's name
     * @param directory the path where the database is located.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public static void delete(String name, File directory) throws CouchbaseLiteException {
        if (name == null || directory == null)
            throw new IllegalArgumentException("a name parameter and/or a dir parameter are null.");

        if (!exists(name, directory))
            throw new CouchbaseLiteException(Status.CBLErrorDomain, Status.NotFound);

        File path = getDatabasePath(directory, name);
        try {
            Log.i(TAG, "delete(): path=%s", path.toString());
            C4Database.deleteAtPath(path.getPath(), DEFAULT_DATABASE_FLAGS, null, C4DocumentVersioning.kC4RevisionTrees);
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
            throw new IllegalArgumentException("a name parameter and/or a dir parameter are null.");
        return getDatabasePath(directory, name).exists();
    }

    public static void copy(File path, String name, DatabaseConfiguration config) throws CouchbaseLiteException {

        String fromPath = path.getPath();
        if (fromPath.charAt(fromPath.length() - 1) != File.separatorChar)
            fromPath += File.separator;
        String toPath = getDatabasePath(config.getDirectory(), name).getPath();
        if (toPath.charAt(toPath.length() - 1) != File.separatorChar)
            toPath += File.separator;
        // TODO:
        int databaseFlags = DEFAULT_DATABASE_FLAGS;
        int encryptionAlgorithm = kC4EncryptionNone;
        byte[] encryptionKey = null;

        try {
            C4Database.copy(fromPath,
                    toPath,
                    databaseFlags,
                    null,
                    C4DocumentVersioning.kC4RevisionTrees,
                    encryptionAlgorithm,
                    encryptionKey);
        } catch (LiteCoreException e) {
            FileUtils.deleteRecursive(toPath);
            throw LiteCoreBridge.convertException(e);
        }
    }

    public static void setLogLevel(LogDomain domain, LogLevel level) {
        switch (domain) {
            case ALL:
                // Database
                Log.enableLogging(Log.DATABASE, level.getValue(), false);
                Log.enableLogging(C4LogDomain.DB, level.getValue(), true);         // LiteCore
                Log.enableLogging(C4LogDomain.Enum, level.getValue(), true);       // LiteCore
                Log.enableLogging(C4LogDomain.Blob, level.getValue(), true);       // LiteCore

                // Query
                Log.enableLogging(Log.QUERY, level.getValue(), false);
                Log.enableLogging(C4LogDomain.SQL, level.getValue(), true);        // LiteCore

                // REPLICATOR
                Log.enableLogging(Log.SYNC, level.getValue(), false);

                // Network
                Log.enableLogging(Log.WebSocket, level.getValue(), false);
                Log.enableLogging(C4LogDomain.BLIP, level.getValue(), true);         // LiteCore
                Log.enableLogging(C4LogDomain.BLIPMessages, level.getValue(), true); // LiteCore
                Log.enableLogging(C4LogDomain.ACTOR, level.getValue(), true);        // LiteCore
                break;

            case DATABASE:
                Log.enableLogging(Log.DATABASE, level.getValue(), false);
                Log.enableLogging(C4LogDomain.DB, level.getValue(), true);         // LiteCore
                Log.enableLogging(C4LogDomain.Enum, level.getValue(), true);       // LiteCore
                Log.enableLogging(C4LogDomain.Blob, level.getValue(), true);       // LiteCore
                break;

            case QUERY:
                Log.enableLogging(Log.QUERY, level.getValue(), false);
                Log.enableLogging(C4LogDomain.SQL, level.getValue(), true);        // LiteCore
                break;

            case REPLICATOR:
                Log.enableLogging(Log.SYNC, level.getValue(), false);
                break;

            case NETWORK:
                Log.enableLogging(Log.WebSocket, level.getValue(), false);
                Log.enableLogging(C4LogDomain.BLIP, level.getValue(), true);         // LiteCore
                Log.enableLogging(C4LogDomain.BLIPMessages, level.getValue(), true); // LiteCore
                Log.enableLogging(C4LogDomain.ACTOR, level.getValue(), true);        // LiteCore
                break;
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
    C4BlobStore getBlobStore() throws CouchbaseLiteRuntimeException {
        mustBeOpen();
        try {
            return c4db.getBlobStore();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertRuntimeException(e);
        }
    }

    ConflictResolver getConflictResolver() {
        return config != null ? config.getConflictResolver() : null;
    }

    // Instead of clone()
    Database copy() throws CouchbaseLiteException {
        return new Database(this.name, this.config);
    }

    //////// DATABASES:

    void mustBeOpen() {
        if (c4db == null)
            throw new CouchbaseLiteRuntimeException("A database is not open", LiteCoreDomain, kC4ErrorNotOpen);
    }

    /**
     * @return a reference to C4Database instance if db is open.
     */
    C4Database getC4Database() {
        // NOTE: In general, mustBeOpen() method is called by caller. And Thread-safety guarantees
        //       c4db should not be closed during the method. Calling mutBeOpen() here is just try
        //       to avoid NullPointerException.
        mustBeOpen();

        return c4db;
    }

    void beginTransaction() throws CouchbaseLiteException {
        try {
            getC4Database().beginTransaction();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    void endTransaction(boolean commit) throws CouchbaseLiteException {
        try {
            getC4Database().endTransaction(commit);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    SharedKeys getSharedKeys() {
        return sharedKeys;
    }

    //////// DOCUMENTS:
    C4Document read(String docID, boolean mustExist)
            throws CouchbaseLiteException {
        try {
            return getC4Database().get(docID, mustExist);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    Map<URI, Replicator> getReplications() {
        return replications;
    }

    Set<Replicator> getActiveReplications() {
        return activeReplications;
    }

    //////// RESOLVING REPLICATED CONFLICTS:

    boolean resolveConflictInDocument(String docID, ConflictResolver resolver)
            throws CouchbaseLiteException {
        synchronized (lock) {
            boolean commit = false;
            beginTransaction();
            try {
                ReadOnlyDocument doc = new ReadOnlyDocument(this, docID, true);
                if (doc == null)
                    return false;

                // Read the conflicting remote revision:
                ReadOnlyDocument otherDoc = new ReadOnlyDocument(this, docID, true);
                if (otherDoc == null || !otherDoc.selectConflictingRevision())
                    return false;

                // Read the common ancestor revision (if it's available):
                ReadOnlyDocument baseDoc = new ReadOnlyDocument(this, docID, true);
                if (!baseDoc.selectCommonAncestor(doc, otherDoc) || baseDoc.getData() == null)
                    baseDoc = null;

                // Call the conflict resolver:
                ReadOnlyDocument resolved;
                if (otherDoc.isDeleted()) {
                    resolved = doc;
                } else if (doc.isDeleted()) {
                    resolved = otherDoc;
                } else {
                    if (resolver == null)
                        resolver = doc.effectiveConflictResolver();
                    Conflict conflict = new Conflict(doc, otherDoc, baseDoc);
                    Log.i(TAG, "Resolving doc '%s' with %s (mine=%s, theirs=%s, base=%s)",
                            docID,
                            resolver != null ? resolver.getClass().getSimpleName() : "null",
                            doc != null ? doc.getRevID() : "null",
                            otherDoc != null ? otherDoc.getRevID() : "null",
                            baseDoc != null ? baseDoc.getRevID() : "null");
                    resolved = resolver.resolve(conflict);
                    if (resolved == null)
                        throw new CouchbaseLiteException(LiteCoreDomain, kC4ErrorConflict);
                }

                // Figure out what revision to delete and what if anything to add:
                String winningRevID;
                String losingRevID;
                byte[] mergedBody = null;
                if (resolved == otherDoc) {
                    winningRevID = otherDoc.getRevID();
                    losingRevID = doc.getRevID();
                } else {
                    winningRevID = doc.getRevID();
                    losingRevID = otherDoc.getRevID();
                    if (resolved != doc) {
                        resolved.setDatabase(this);
                        mergedBody = resolved.encode();
                        if (mergedBody == null)
                            return false;
                    }
                }

                // Tell LiteCore to do the resolution:
                try {
                    C4Document rawDoc = doc.getC4doc().getRawDoc();
                    rawDoc.resolveConflict(winningRevID, losingRevID, mergedBody);
                    rawDoc.save(0);
                } catch (LiteCoreException e) {
                    throw LiteCoreBridge.convertException(e);
                }

                commit = true;
            } finally {
                endTransaction(commit);
            }
            return commit;
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

        // encryption key
        int encryptionAlgorithm = config.getEncryptionKey() == null ? kC4EncryptionNone : kC4EncryptionAES256;
        byte[] encryptionKey = config.getEncryptionKey() == null ? null : config.getEncryptionKey().getKey();

        Log.i(TAG, "Opening %s at path %s", this, dbFile.getPath());

        try {
            c4db = new C4Database(
                    dbFile.getPath(),
                    databaseFlags,
                    null,
                    C4DocumentVersioning.kC4RevisionTrees,
                    encryptionAlgorithm,
                    encryptionKey);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }

        c4DBObserver = null;
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

    //////// DOCUMENTS:

    private Document getDocument(String documentID, boolean mustExist) {
        mustBeOpen();

        try {
            return new Document(this, documentID, mustExist);
        } catch (CouchbaseLiteRuntimeException e) {
            if (e.getDomain() == LiteCoreDomain && e.getCode() == kC4ErrorNotFound)
                return null;
            else
                throw e;
        }
    }


    // --- C4Database
    private void closeC4DB() throws CouchbaseLiteException {
        try {
            getC4Database().close();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    private void deleteC4DB() throws CouchbaseLiteException {
        try {
            getC4Database().delete();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    private void freeC4DB() {
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
        c4DBObserver = c4db.createDatabaseObserver(new C4DatabaseObserverListener() {
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

        C4DocumentObserver docObserver = c4db.createDocumentObserver(docID, new C4DocumentObserverListener() {
            @Override
            public void callback(C4DocumentObserver observer, final String docID, final long sequence, Object context) {
                new Handler(Looper.getMainLooper())
                        .post(new Runnable() {
                            @Override
                            public void run() {
                                postDocumentChanged(docID);
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
        List<DatabaseChange> allChanges = new ArrayList<>();

        synchronized (lock) {
            if (c4DBObserver == null || c4db == null || getC4Database().isInTransaction())
                return;

            boolean external = false;
            int nChanges;
            List<String> docIDs = new ArrayList<>();
            do {
                // Read changes in batches of kMaxChanges:
                C4DatabaseChange[] c4DBChanges = c4DBObserver.getChanges(MAX_CHANGES);
                nChanges = c4DBChanges.length;
                boolean newExternal = nChanges > 0 ? c4DBChanges[0].isExternal() : false;
                if (c4DBChanges == null || c4DBChanges.length == 0 || external != newExternal || docIDs.size() > 1000) {
                    if (docIDs.size() > 0) {
                        allChanges.add(new DatabaseChange(this, docIDs));
                        docIDs = new ArrayList<>();
                    }
                }
                external = newExternal;
                for (int i = 0; i < nChanges; i++)
                    docIDs.add(c4DBChanges[i].getDocID());
            } while (nChanges > 0);
        }

        // NOTE: dbChangeListeners is synchronized collections. And, DatabaseChange is immutable.
        for (DatabaseChange change : allChanges) {
            // not allow to add/remove middle of iteration
            synchronized (dbChangeListeners) {
                for (DatabaseChangeListener listener : dbChangeListeners)
                    listener.changed(change);
            }
        }
    }

    private void postDocumentChanged(final String documentID) {
        synchronized (lock) {
            if (!c4DocObservers.containsKey(documentID) || c4db == null)
                return;
        }

        // NOTE: docChangeListeners and its Set value are synchronized collections.
        //       And DocumentChange is immutable
        Set<DocumentChangeListener> listeners = docChangeListeners.get(documentID);
        if (listeners != null) {
            synchronized (listeners) {
                DocumentChange change = new DocumentChange(documentID);
                for (DocumentChangeListener listener : listeners) {
                    listener.changed(change);
                }
            }
        }
    }

    private void prepareDocument(Document document) throws CouchbaseLiteException {
        mustBeOpen();

        if (document.getDatabase() == null)
            document.setDatabase(this);
        else if (document.getDatabase() != this)
            throw new CouchbaseLiteException(Status.CBLErrorDomain, Status.Forbidden);
    }
}
