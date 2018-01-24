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

import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.FileUtils;
import com.couchbase.lite.internal.utils.JsonUtils;
import com.couchbase.litecore.C4;
import com.couchbase.litecore.C4BlobStore;
import com.couchbase.litecore.C4Constants;
import com.couchbase.litecore.C4Constants.C4DatabaseFlags;
import com.couchbase.litecore.C4Constants.C4DocumentVersioning;
import com.couchbase.litecore.C4Constants.C4EncryptionAlgorithm;
import com.couchbase.litecore.C4Constants.C4ErrorDomain;
import com.couchbase.litecore.C4Constants.C4RevisionFlags;
import com.couchbase.litecore.C4Constants.LiteCoreError;
import com.couchbase.litecore.C4Database;
import com.couchbase.litecore.C4DatabaseChange;
import com.couchbase.litecore.C4DatabaseObserver;
import com.couchbase.litecore.C4DatabaseObserverListener;
import com.couchbase.litecore.C4Document;
import com.couchbase.litecore.C4DocumentObserver;
import com.couchbase.litecore.C4DocumentObserverListener;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.SharedKeys;
import com.couchbase.litecore.fleece.FLSliceResult;

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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
            = C4DatabaseFlags.kC4DB_Create
            | C4DatabaseFlags.kC4DB_AutoCompact
            | C4DatabaseFlags.kC4DB_SharedKeys;

    //---------------------------------------------
    // enums
    //---------------------------------------------

    /**
     * Log domain. The log domains here are tentative and subject to change.
     */
    public enum LogDomain {
        ALL, DATABASE, QUERY, REPLICATOR, NETWORK
    }

    /**
     * Log level. The default log level for all domains is warning.
     * The log levels here are tentative and subject to change.
     */
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
    private final DatabaseConfiguration config;
    private C4Database c4db;
    private ExecutorService executorService;

    private Set<DatabaseChangeListenerToken> dbListenerTokens;
    private C4DatabaseObserver c4DBObserver;
    private Map<String, Set<DocumentChangeListenerToken>> docListenerTokens;
    private Map<String, C4DocumentObserver> c4DocObservers;

    private final SharedKeys sharedKeys;

    private Set<Replicator> activeReplications;
    private Set<LiveQuery> activeLiveQueries;

    private final Object lock = new Object(); // lock for thread-safety

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Construct a  Database with a given name and database config.
     * If the database does not yet exist, it will be created, unless the `readOnly` option is used.
     *
     * @param name   The name of the database. May NOT contain capital letters!
     * @param config The database config, Note: null config parameter is not allowed with Android platform
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the open operation.
     */
    public Database(String name, DatabaseConfiguration config) throws CouchbaseLiteException {
        if (name == null || name.length() == 0)
            throw new IllegalArgumentException("name should not be empty.");
        if (config == null)
            throw new IllegalArgumentException("DatabaseConfiguration should not be null.");

        Log.init();

        this.name = name;
        this.config = config;
        String tempdir = config.getTempDir();
        if (tempdir != null)
            C4.setenv("TMPDIR", tempdir, 1);
        open();
        this.sharedKeys = new SharedKeys(c4db);
        this.executorService = Executors.newSingleThreadExecutor();
        this.activeReplications = Collections.synchronizedSet(new HashSet<Replicator>());
        this.activeLiveQueries = Collections.synchronizedSet(new HashSet<LiveQuery>());
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
        return this.name;
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
        return config;
    }

    // GET EXISTING DOCUMENT

    /**
     * Gets an existing Document object with the given ID. If the document with the given ID doesn't
     * exist in the database, the value returned will be null.
     *
     * @param id the document ID
     * @return the Document object
     */
    public Document getDocument(String id) {
        if (id == null)
            throw new IllegalArgumentException("a documentID parameter is null");

        synchronized (lock) {
            mustBeOpen();
            try {
                return new Document(this, id, false);
            } catch (CouchbaseLiteException ex) {
                // only 404 - Not Found error throws CouchbaseLiteException
                return null;
            }
        }
    }

    /**
     * Saves the given document to the database. If the document in the database has been updated
     * since it was read by this Document, a conflict occurs, which will be resolved by invoking
     * the conflict handler. This can happen if multiple application threads are writing to the
     * database, or a pull replication is copying changes from a server.
     *
     * @param document
     */
    public Document save(MutableDocument document) throws CouchbaseLiteException {
        if (document == null)
            throw new IllegalArgumentException("a document parameter is null");

        // not allowed to save with old MutableDocument
        if (document.isInvalidated())
            throw new IllegalArgumentException("Do not allow to save or delete the MutableDocument "
                    + "that has already been used to save or delete.");

        // NOTE: synchronized in save(Document, boolean) method
        return save(document, false);
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


        if (document.isNewDocument())
            throw new IllegalArgumentException("Do not allow to delete a newly created document "
                    + "that has not been saved into the database.");

        // not allowed to delete with old MutableDocument
        if (document.isInvalidated())
            throw new IllegalArgumentException("Do not allow to save or delete the MutableDocument "
                    + "that has already been used to save or delete.");

        // NOTE: synchronized in save(Document, boolean) method
        save(document, true);
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

        if (document.isNewDocument())
            throw new IllegalArgumentException("Do not allow to purge a newly created document "
                    + "that has not been saved into the database.");

        synchronized (lock) {
            prepareDocument(document);

            boolean commit = false;
            beginTransaction();
            try {
                // revID: null, all revisions are purged.
                if (document.getC4doc().purgeRevision(null) >= 0) {
                    document.getC4doc().save(0);
                    commit = true;
                }
            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertException(e);
            } finally {
                endTransaction(commit);
            }
        }
    }

    // Batch operations:

    /**
     * Runs a group of database operations in a batch. Use this when performing bulk write operations
     * like multiple inserts/updates; it saves the overhead of multiple database commits, greatly
     * improving performance.
     *
     * @param runnable the action which is implementation of Runnable interface
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void inBatch(Runnable runnable) throws CouchbaseLiteException {
        if (runnable == null)
            throw new IllegalArgumentException("The runnable parameter should not be null.");

        synchronized (lock) {
            mustBeOpen();
            try {
                boolean commit = false;
                getC4Database().beginTransaction();
                try {
                    try {
                        runnable.run();
                        commit = true;
                    } catch (RuntimeException e) {
                        throw new CouchbaseLiteException(e);
                    }
                } finally {
                    getC4Database().endTransaction(commit);
                }
            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertException(e);
            }
        }

        postDatabaseChanged();
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
    public ListenerToken addChangeListener(DatabaseChangeListener listener) {
        return addChangeListener(null, listener);
    }

    public ListenerToken addChangeListener(Executor executor, DatabaseChangeListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("a listener parameter is null");

        synchronized (lock) {
            mustBeOpen();
            return addDatabaseChangeListener(executor, listener);
        }
    }

    /**
     * Remove the given DatabaseChangeListener from the this database.
     *
     * @param token
     */
    public void removeChangeListener(ListenerToken token) {
        if (token == null)
            throw new IllegalArgumentException("a token parameter is null");

        synchronized (lock) {
            if (token instanceof DocumentChangeListenerToken)
                removeDocumentChangeListener((DocumentChangeListenerToken) token);
            else
                removeDatabaseChangeListener((DatabaseChangeListenerToken) token);
        }
    }

    // Document changes:

    /**
     * Add the given DocumentChangeListener to the specified document.
     */
    public ListenerToken addDocumentChangeListener(String id, DocumentChangeListener listener) {
        return addDocumentChangeListener(id, null, listener);
    }

    public ListenerToken addDocumentChangeListener(String id, Executor executor,
                                                   DocumentChangeListener listener) {
        if (id == null || listener == null)
            throw new IllegalArgumentException("a listener parameter and/or a listener parameter are null");

        synchronized (lock) {
            mustBeOpen();
            return addDocumentChangeListener(executor, listener, id);
        }
    }

    // Others:

    /**
     * Closes a database.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void close() throws CouchbaseLiteException {
        synchronized (lock) {
            if (c4db == null)
                return;

            Log.i(TAG, "Closing %s at path %s", this, getC4Database().getPath());

            // stop all active replicators
            stopAllActiveReplicatoin();

            // stop all active live queries
            stopAllActiveLiveQueries();

            // close db
            closeC4DB();

            // release instances
            freeC4Observers();
            freeC4DB();

            // shutdown executor service
            shutdownExecutorService();
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

            Log.i(TAG, "Deleting %s at path %s", this, getC4Database().getPath());

            // stop all active replicators
            stopAllActiveReplicatoin();

            // stop all active live queries
            stopAllActiveLiveQueries();

            // delete db
            deleteC4DB();

            // release instances
            freeC4Observers();
            freeC4DB();

            // shutdown executor service
            shutdownExecutorService();
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
            int keyType = encryptionKey == null || encryptionKey.getKey() == null ?
                    C4EncryptionAlgorithm.kC4EncryptionNone :
                    C4EncryptionAlgorithm.kC4EncryptionAES256;
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
            C4Database.deleteAtPath(path.getPath());
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

    public static void copy(File path, String name, DatabaseConfiguration config)
            throws CouchbaseLiteException {
        String fromPath = path.getPath();
        if (fromPath.charAt(fromPath.length() - 1) != File.separatorChar)
            fromPath += File.separator;
        String toPath = getDatabasePath(new File(config.getDirectory()), name).getPath();
        if (toPath.charAt(toPath.length() - 1) != File.separatorChar)
            toPath += File.separator;
        int databaseFlags = DEFAULT_DATABASE_FLAGS;
        int encryptionAlgorithm = C4EncryptionAlgorithm.kC4EncryptionNone;
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

    /**
     * Set log level for the given log domain.
     *
     * @param domain The log domain
     * @param level  The log level
     */
    public static void setLogLevel(LogDomain domain, LogLevel level) {
        Log.setLogLevel(domain, level);
    }

    //---------------------------------------------
    // Override public method
    //---------------------------------------------

    @Override
    public String toString() {
        return "Database@" + Integer.toHexString(hashCode()) + "{" +
                "name='" + name + '\'' +
                //"dir='" + (c4db != null ? c4db.getPath() : "[closed]") + '\'' +
                '}';
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
    Object getLock() {
        return lock;
    }

    boolean equalsWithPath(Database other) {
        if (other == null) return false;
        File path = getPath();
        File otherPath = other.getPath();
        if (path == null && otherPath == null)
            return true;
        else if ((path == null && otherPath != null) || (path != null && otherPath == null))
            return false;
        else
            return path.equals(otherPath);
    }

    C4BlobStore getBlobStore() throws CouchbaseLiteRuntimeException {
        synchronized (lock) {
            mustBeOpen();
            try {
                return c4db.getBlobStore();
            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertRuntimeException(e);
            }
        }
    }

    ConflictResolver getConflictResolver() {
        return config.getConflictResolver();
    }

    // Instead of clone()
    Database copy() throws CouchbaseLiteException {
        return new Database(this.name, this.config);
    }

    //////// DATABASES:

    void mustBeOpen() {
        if (c4db == null)
            throw new CouchbaseLiteRuntimeException("A database is not open",
                    C4ErrorDomain.LiteCoreDomain, LiteCoreError.kC4ErrorNotOpen);
    }

    boolean isOpen() {
        return c4db != null;
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

    Set<Replicator> getActiveReplications() {
        return activeReplications;
    }


    Set<LiveQuery> getActiveLiveQueries() {
        return activeLiveQueries;
    }

    //////// RESOLVING REPLICATED CONFLICTS:

    boolean resolveConflictInDocument(String docID, ConflictResolver resolver)
            throws CouchbaseLiteException {
        Document doc;
        Document otherDoc;
        Document baseDoc;

        while (true) {
            synchronized (lock) {
                // Open a transaction as a workaround to make sure that
                // the replicator commits the current changes before we
                // try to read the documents.
                // https://github.com/couchbase/couchbase-lite-core/issues/322
                boolean commit = false;
                beginTransaction();
                try {
                    doc = new Document(this, docID, true);

                    // Read the conflicting remote revision:
                    otherDoc = new Document(this, docID, true);
                    if (!otherDoc.selectConflictingRevision())
                        return false;

                    // Read the common ancestor revision (if it's available):
                    baseDoc = new Document(this, docID, true);
                    if (!baseDoc.selectCommonAncestor(doc, otherDoc) || baseDoc.toMap() == null)
                        baseDoc = null;

                    commit = true;
                } finally {
                    endTransaction(commit);
                }

                // Call the conflict resolver:
                if (resolver == null)
                    resolver = getConflictResolver();
                Conflict conflict = new Conflict(doc, otherDoc, baseDoc);
                Log.i(TAG, "Resolving doc '%s' with %s (mine=%s, theirs=%s, base=%s)",
                        docID,
                        resolver != null ? resolver.getClass().getSimpleName() : "null",
                        doc != null ? doc.getRevID() : "null",
                        otherDoc != null ? otherDoc.getRevID() : "null",
                        baseDoc != null ? baseDoc.getRevID() : "null");
                Document resolved = resolver.resolve(conflict);
                if (resolved == null)
                    throw new CouchbaseLiteException(C4ErrorDomain.LiteCoreDomain,
                            LiteCoreError.kC4ErrorConflict);

                try {
                    return saveResolvedDocument(resolved, conflict);
                } catch (LiteCoreException e) {
                    if (e.domain == C4ErrorDomain.LiteCoreDomain && e.code == LiteCoreError.kC4ErrorConflict)
                        continue;
                    else
                        throw LiteCoreBridge.convertException(e);
                }
            }
        }
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    //////// DATABASES:

    private void open() throws CouchbaseLiteException {
        if (c4db != null)
            return;

        File dir = config.getDirectory() != null ? new File(config.getDirectory()) : getDefaultDirectory();
        setupDirectory(dir);

        File dbFile = getDatabasePath(dir, this.name);

        int databaseFlags = getDatabaseFlags();

        // encryption key
        int encryptionAlgorithm = config.getEncryptionKey() == null ?
                C4EncryptionAlgorithm.kC4EncryptionNone : C4EncryptionAlgorithm.kC4EncryptionAES256;
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
        dbListenerTokens = new HashSet<>();
        docListenerTokens = new HashMap<>();
        c4DocObservers = new HashMap<>();
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
    private DatabaseChangeListenerToken addDatabaseChangeListener(Executor executor,
                                                                  DatabaseChangeListener listener) {
        // NOTE: caller method is synchronized.
        if (dbListenerTokens.isEmpty())
            registerC4DBObserver();
        DatabaseChangeListenerToken token = new DatabaseChangeListenerToken(executor, listener);
        dbListenerTokens.add(token);
        return token;
    }

    private void removeDatabaseChangeListener(DatabaseChangeListenerToken token) {
        // NOTE: caller method is synchronized.
        dbListenerTokens.remove(token);
        if (dbListenerTokens.isEmpty())
            freeC4DBObserver();
    }

    // Document changes:
    private DocumentChangeListenerToken addDocumentChangeListener(Executor executor,
                                                                  DocumentChangeListener listener,
                                                                  String docID) {
        // NOTE: caller method is synchronized.
        if (!docListenerTokens.containsKey(docID)) {
            Set<DocumentChangeListenerToken> listeners = new HashSet<>();
            docListenerTokens.put(docID, listeners);
            registerC4DocObserver(docID);
        }
        DocumentChangeListenerToken token = new DocumentChangeListenerToken(executor, listener, docID);
        docListenerTokens.get(docID).add(token);
        return token;
    }

    private void removeDocumentChangeListener(DocumentChangeListenerToken token) {
        // NOTE: caller method is synchronized.
        String docID = token.getDocID();
        if (docListenerTokens.containsKey(docID)) {
            Set<DocumentChangeListenerToken> tokens = docListenerTokens.get(docID);
            if (tokens.remove(token)) {
                if (tokens.isEmpty()) {
                    docListenerTokens.remove(docID);
                    removeC4DocObserver(docID);
                }
            }
        }
    }

    private void registerC4DBObserver() {
        c4DBObserver = c4db.createDatabaseObserver(new C4DatabaseObserverListener() {
            @Override
            public void callback(C4DatabaseObserver observer, Object context) {
                executorService.submit(new Runnable() {
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

        C4DocumentObserver docObserver = c4db.createDocumentObserver(docID,
                new C4DocumentObserverListener() {
                    @Override
                    public void callback(C4DocumentObserver observer, final String docID,
                                         final long sequence, Object context) {
                        executorService.submit(new Runnable() {
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
        if (docListenerTokens != null) {
            docListenerTokens.clear();
            docListenerTokens = null;
        }
    }

    private void freeC4Observers() {
        freeC4DBObserver();
        freeC4DocObservers();
    }

    private void postDatabaseChanged() {
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
                        DatabaseChange change = new DatabaseChange(this, docIDs);
                        for (DatabaseChangeListenerToken token : dbListenerTokens)
                            token.notify(change);
                        docIDs = new ArrayList<>();
                    }
                }
                external = newExternal;
                for (int i = 0; i < nChanges; i++)
                    docIDs.add(c4DBChanges[i].getDocID());
            } while (nChanges > 0);
        }
    }

    private void postDocumentChanged(final String documentID) {
        synchronized (lock) {
            if (!c4DocObservers.containsKey(documentID) || c4db == null)
                return;

            Set<DocumentChangeListenerToken> tokens = docListenerTokens.get(documentID);
            if (tokens != null) {
                DocumentChange change = new DocumentChange(this, documentID);
                for (DocumentChangeListenerToken token : tokens)
                    token.notify(change);
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

    // The main save method.
    private Document save(Document document, boolean deletion) throws CouchbaseLiteException {
        if (deletion && !document.exists())
            throw new CouchbaseLiteException(Status.CBLErrorDomain, Status.NotFound);

        // Attempt to save. (On conflict, this will succeed but newDoc will be null.)
        String docID = document.getId();
        Document doc = document;
        Document baseDoc = null;
        Document otherDoc = null;
        C4Document newDoc = null;
        C4Document baseRev = null;
        while (true) {
            synchronized (lock) {
                prepareDocument(doc);

                // Begin a database transaction:
                boolean commit = false;
                beginTransaction();
                try {
                    if (deletion) {
                        // Check existing, NO-OPS if the document doesn't exist:
                        C4Document curDoc = null;
                        try {
                            curDoc = getC4Database().get(docID, true);
                        } catch (LiteCoreException e) {
                            if (e.domain == C4ErrorDomain.LiteCoreDomain && e.code == LiteCoreError.kC4ErrorNotFound) {
                                if (document instanceof MutableDocument)
                                    ((MutableDocument) document).markAsInvalidated();
                                return null;
                            } else
                                throw LiteCoreBridge.convertException(e);
                        } finally {
                            if (curDoc != null)
                                curDoc.free();
                        }
                    }

                    try {
                        newDoc = save(doc, baseRev, deletion);
                        commit = true;
                    } catch (LiteCoreException e) {
                        // conflict is not an error, here
                        if (!(e.domain == C4ErrorDomain.LiteCoreDomain && e.code == LiteCoreError.kC4ErrorConflict)) {
                            throw LiteCoreBridge.convertException(e);
                        }
                    }
                } finally {
                    // true: commit the transaction, false: abort the transaction
                    try {
                        endTransaction(commit);
                    } catch (CouchbaseLiteException e) {
                        // NOTE: newDoc could be null if initial save() throws Exception.
                        if (newDoc != null)
                            newDoc.free();
                        throw e;
                    }
                }

                // save succeeded
                if (newDoc != null) {
                    if (document instanceof MutableDocument)
                        ((MutableDocument) document).markAsInvalidated();
                    C4Document newC4Doc = C4Document.document(newDoc);
                    return new Document(this, document.getId(), newC4Doc);
                }

                // save not succeeded as conflicting:
                if (deletion && !doc.isDeleted()) {
                    // Copy and mark as deleted:
                    MutableDocument deletedDoc = doc.toMutable();
                    deletedDoc.markAsDeleted();
                    doc = deletedDoc;
                }

                if (doc.getC4doc() != null)
                    baseDoc = new Document(this, docID, doc.getC4doc());

                otherDoc = new Document(this, docID, true);
            }

            // Resolve conflict:
            ConflictResolver resolver = getConflictResolver();
            Conflict conflict = new Conflict(doc, otherDoc, baseDoc);

            Document resolved = resolver.resolve(conflict);
            if (resolved == null)
                throw new CouchbaseLiteException(C4ErrorDomain.LiteCoreDomain, LiteCoreError.kC4ErrorConflict);

            synchronized (lock) {
                Document current = new Document(this, docID, true);
                if (resolved.getRevID() != null && resolved.getRevID().equals(current.getRevID())) {
                    if (document instanceof MutableDocument)
                        ((MutableDocument) document).markAsInvalidated();
                    return resolved; // same as current
                }

                // for saving:
                doc = resolved;
                baseRev = current.getC4doc();
                deletion = resolved.isDeleted();
            }
        }
    }

    // Lower-level save method. On conflict, returns YES but sets *outDoc to NULL.
    private C4Document save(Document document, C4Document base, boolean deletion)
            throws LiteCoreException {
        FLSliceResult body = null;
        try {
            int revFlags = 0;
            if (deletion)
                revFlags = C4RevisionFlags.kRevDeleted;
            if (!deletion && !document.isEmpty()) {
                // Encode properties to Fleece data:
                body = document.encode2();
                if (body == null)
                    return null;
                if (SharedKeys.dictContainsBlobs(body, getSharedKeys()))
                    revFlags |= C4Constants.C4RevisionFlags.kRevHasAttachments;
            }

            // Save to database:
            C4Document c4Doc = base != null ? base : document.getC4doc();
            if (c4Doc != null)
                return c4Doc.update(body, revFlags);
            else
                return getC4Database().create(document.getId(), body, revFlags);
        } finally {
            if (body != null)
                body.free();
        }
    }

    private boolean saveResolvedDocument(Document resolved, Conflict conflict)
            throws CouchbaseLiteException, LiteCoreException {
        synchronized (lock) {
            boolean commit = false;
            beginTransaction();
            try {
                Document doc = conflict.getMine();
                Document otherDoc = conflict.getTheirs();

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
                C4Document rawDoc = doc.getC4doc();
                rawDoc.resolveConflict(winningRevID, losingRevID, mergedBody);
                rawDoc.save(0);
                Log.i(TAG, "Conflict resolved as doc '%s' rev %s",
                        rawDoc.getDocID(), rawDoc.getRevID());

                commit = true;
            } finally {
                endTransaction(commit);
            }
            return commit;
        }
    }

    private void shutdownExecutorService() {
        if (!executorService.isShutdown() && !executorService.isTerminated()) {
            shutdownAndAwaitTermination(executorService, 60);
        }
    }

    private void shutdownAndAwaitTermination(ExecutorService pool, int waitSec) {
        pool.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!pool.awaitTermination(waitSec, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!pool.awaitTermination(waitSec, TimeUnit.SECONDS))
                    Log.w(TAG, "Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            pool.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    private void stopAllActiveReplicatoin() {
        // stop replicator
        synchronized (activeReplications) {
            for (Replicator repl : activeReplications)
                repl.stop();
        }

        // TODO: https://github.com/couchbase/couchbase-lite-android/issues/1543
        // NOTE: Need to wait for STOPPED state?
    }

    private void stopAllActiveLiveQueries() {
        // stop live query
        synchronized (activeLiveQueries) {
            for (LiveQuery liveQuery : activeLiveQueries)
                liveQuery.stop(false);
            activeLiveQueries.clear();
        }
    }
}
