//
// Database.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ExecutorUtils;
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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * AbstractDatabase is a base class of A Couchbase Lite Database.
 */
abstract class AbstractDatabase {
    //---------------------------------------------
    // Load LiteCore library and its dependencies
    //---------------------------------------------
    static {
        NativeLibraryLoader.load();
    }

    //---------------------------------------------
    // static variables
    //---------------------------------------------
    protected static final String TAG = Log.DATABASE;
    protected static final String DB_EXTENSION = "cblite2";
    protected static final int MAX_CHANGES = 100;

    protected static final int DEFAULT_DATABASE_FLAGS
            = C4DatabaseFlags.kC4DB_Create
            | C4DatabaseFlags.kC4DB_AutoCompact
            | C4DatabaseFlags.kC4DB_SharedKeys;

    //---------------------------------------------
    // enums
    //---------------------------------------------

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    protected String name;
    protected final DatabaseConfiguration config;
    protected C4Database c4db;
    protected ScheduledExecutorService postExecutor;  // to post Database/Document Change notification
    protected ScheduledExecutorService queryExecutor; // executor for LiveQuery. one per db.
    protected Set<DatabaseChangeListenerToken> dbListenerTokens;
    protected C4DatabaseObserver c4DBObserver;
    protected Map<String, Set<DocumentChangeListenerToken>> docListenerTokens;
    protected Map<String, C4DocumentObserver> c4DocObservers;
    protected final SharedKeys sharedKeys;
    protected Set<Replicator> activeReplications;
    protected Set<LiveQuery> activeLiveQueries;
    protected final Object lock = new Object(); // lock for thread-safety

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Construct a  AbstractDatabase with a given name and database config.
     * If the database does not yet exist, it will be created, unless the `readOnly` option is used.
     *
     * @param name   The name of the database. May NOT contain capital letters!
     * @param config The database config, Note: null config parameter is not allowed with Android platform
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the open operation.
     */
    protected AbstractDatabase(String name, DatabaseConfiguration config) throws CouchbaseLiteException {
        if (name == null || name.length() == 0)
            throw new IllegalArgumentException("name should not be empty.");
        if (config == null)
            throw new IllegalArgumentException("DatabaseConfiguration should not be null.");

        Log.init();

        this.name = name;
        this.config = config.readonlyCopy();

        String tempdir = this.config.getTempDir();
        if (tempdir != null)
            C4.setenv("TMPDIR", tempdir, 1);
        open();
        this.sharedKeys = new SharedKeys(c4db);
        this.postExecutor = Executors.newSingleThreadScheduledExecutor();
        this.queryExecutor = Executors.newSingleThreadScheduledExecutor();
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
     * Returns a READONLY config object which will throw a runtime exception
     * when any setter methods are called.
     *
     * @return the READONLY copied config object
     */
    public DatabaseConfiguration getConfig() {
        return config.readonlyCopy();
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
                return new Document((Database) this, id, false);
            } catch (CouchbaseLiteException ex) {
                // only 404 - Not Found error throws CouchbaseLiteException
                return null;
            }
        }
    }

    /**
     * @param document
     * @return
     * @throws CouchbaseLiteException
     */
    public void save(MutableDocument document) throws CouchbaseLiteException {
        save(document, ConcurrencyControl.NONE);
    }

    /**
     * @param document
     * @param concurrencyControl
     * @return
     * @throws CouchbaseLiteException
     */
    public boolean save(MutableDocument document, ConcurrencyControl concurrencyControl) throws CouchbaseLiteException {
        if (document == null)
            throw new IllegalArgumentException("a document parameter is null");

        // NOTE: synchronized in save(Document, boolean) method
        return save(document, false, concurrencyControl);
    }


    /**
     * @param document
     * @throws CouchbaseLiteException
     */
    public void delete(Document document) throws CouchbaseLiteException {
        delete(document, ConcurrencyControl.NONE);
    }

    /**
     * @param document
     * @param concurrencyControl
     * @throws CouchbaseLiteException
     */
    public boolean delete(Document document, ConcurrencyControl concurrencyControl) throws CouchbaseLiteException {
        if (document == null)
            throw new IllegalArgumentException("a document parameter is null");


        if (document.isNewDocument())
            throw new IllegalArgumentException("Do not allow to delete a newly created document "
                    + "that has not been saved into the database.");

        // NOTE: synchronized in save(Document, boolean) method
        return save(document, true, concurrencyControl);
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
                throw CBLStatus.convertException(e);
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
                throw CBLStatus.convertException(e);
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
                throw CBLStatus.convertException(e);
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

            if (activeReplications.size() > 0) {
                throw new CouchbaseLiteException(
                        "Cannot close the database. Please stop all of the replicators before " +
                                "closing the database.",
                        CBLError.Domain.CBLErrorDomain, CBLError.Code.CBLErrorBusy);
            }


            if (activeLiveQueries.size() > 0) {
                throw new CouchbaseLiteException("Cannot close the database. Please remove all of " +
                        "the query listeners before closing the database",
                        CBLError.Domain.CBLErrorDomain, CBLError.Code.CBLErrorBusy);
            }

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

            if (activeReplications.size() > 0) {
                throw new CouchbaseLiteException(
                        "Cannot delete the database. Please stop all of the replicators before " +
                                "deleting the database.",
                        CBLError.Domain.CBLErrorDomain, CBLError.Code.CBLErrorBusy);
            }


            if (activeLiveQueries.size() > 0) {
                throw new CouchbaseLiteException("Cannot delete the database. Please remove all of " +
                        "the query listeners before deleting the database",
                        CBLError.Domain.CBLErrorDomain, CBLError.Code.CBLErrorBusy);
            }

            // delete db
            deleteC4DB();

            // release instances
            freeC4Observers();
            freeC4DB();

            // shutdown executor service
            shutdownExecutorService();
        }
    }

    // Maintenance operations:

    public List<String> getIndexes() throws CouchbaseLiteException {
        synchronized (lock) {
            mustBeOpen();
            try {
                return (List<String>) SharedKeys.valueToObject(c4db.getIndexes(), sharedKeys);
            } catch (LiteCoreException e) {
                throw CBLStatus.convertException(e);
            }
        }
    }

    public void createIndex(String name, Index index) throws CouchbaseLiteException {
        if (name == null || index == null)
            throw new IllegalArgumentException();
        synchronized (lock) {
            mustBeOpen();
            try {
                AbstractIndex abstractIndex = (AbstractIndex) index;
                String json = JsonUtils.toJson(abstractIndex.items()).toString();
                getC4Database().createIndex(name, json,
                        abstractIndex.type().getValue(),
                        abstractIndex.language(),
                        abstractIndex.ignoreAccents());
            } catch (LiteCoreException e) {
                throw CBLStatus.convertException(e);
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
                throw CBLStatus.convertException(e);
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
            throw new CouchbaseLiteException(CBLError.Domain.CBLErrorDomain, CBLError.Code.CBLErrorNotFound);

        File path = getDatabasePath(directory, name);
        try {
            Log.i(TAG, "delete(): path=%s", path.toString());
            C4Database.deleteAtPath(path.getPath());
        } catch (LiteCoreException e) {
            throw CBLStatus.convertException(e);
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
        if (path == null || name == null || config == null)
            throw new IllegalArgumentException("a path, a dir and/or config parameters are null.");

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
            throw CBLStatus.convertException(e);
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
                throw CBLStatus.convertRuntimeException(e);
            }
        }
    }

    // Instead of clone()
    Database copy() throws CouchbaseLiteException {
        return new Database(this.name, this.config);
    }

    //////// DATABASES:

    void mustBeOpen() {
        if (c4db == null)
            throw new CouchbaseLiteRuntimeException("A database is not open",
                    CBLError.Domain.CBLErrorDomain, CBLError.Code.CBLErrorNotOpen);
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
            throw CBLStatus.convertException(e);
        }
    }

    void endTransaction(boolean commit) throws CouchbaseLiteException {
        try {
            getC4Database().endTransaction(commit);
        } catch (LiteCoreException e) {
            throw CBLStatus.convertException(e);
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
            throw CBLStatus.convertException(e);
        }
    }

    Set<Replicator> getActiveReplications() {
        return activeReplications;
    }


    Set<LiveQuery> getActiveLiveQueries() {
        return activeLiveQueries;
    }

    //////// RESOLVING REPLICATED CONFLICTS:

    void resolveConflictInDocument(String docID) throws CouchbaseLiteException {
        synchronized (lock) {
            boolean commit = false;
            beginTransaction();
            try {
                // Read local document:
                Document localDoc = new Document((Database) this, docID, true);

                // Read the conflicting remote revision:
                Document remoteDoc = new Document((Database) this, docID, true);
                try {
                    remoteDoc.selectConflictingRevision();
                } catch (LiteCoreException e) {
                    throw CBLStatus.convertException(e);
                }

                // Resolve conflict:
                Log.v(TAG, "Resolving doc '%s' (local=%s and remote=%s)", docID, localDoc.getRevID(), remoteDoc.getRevID());
                Document resolvedDoc = resolveConflict(localDoc, remoteDoc);

                // Save resolved document:
                try {
                    saveResolvedDocument(resolvedDoc, localDoc, remoteDoc);
                } catch (LiteCoreException e) {
                    throw CBLStatus.convertException(e);
                }

                commit = true;
            } finally {
                endTransaction(commit);
            }
        }
    }

    ScheduledExecutorService getQueryExecutor() {
        return queryExecutor;
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    abstract int getEncryptionAlgorithm();

    abstract byte[] getEncryptionKey();

    //////// DATABASES:

    private void open() throws CouchbaseLiteException {
        if (c4db != null)
            return;

        File dir = config.getDirectory() != null ? new File(config.getDirectory()) : getDefaultDirectory();
        setupDirectory(dir);

        File dbFile = getDatabasePath(dir, this.name);
        int databaseFlags = getDatabaseFlags();

        Log.i(TAG, "Opening %s at path %s", this, dbFile.getPath());

        try {
            c4db = new C4Database(
                    dbFile.getPath(),
                    databaseFlags,
                    null,
                    C4DocumentVersioning.kC4RevisionTrees,
                    getEncryptionAlgorithm(),
                    getEncryptionKey());
        } catch (LiteCoreException e) {
            throw CBLStatus.convertException(e);
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
            throw CBLStatus.convertException(e);
        }
    }

    private void deleteC4DB() throws CouchbaseLiteException {
        try {
            getC4Database().delete();
        } catch (LiteCoreException e) {
            throw CBLStatus.convertException(e);
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
                if (!postExecutor.isShutdown() && !postExecutor.isTerminated()) {
                    postExecutor.submit(new Runnable() {
                        @Override
                        public void run() {
                            postDatabaseChanged();
                        }
                    });
                }
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
                        if (!postExecutor.isShutdown() && !postExecutor.isTerminated()) {
                            postExecutor.submit(new Runnable() {
                                @Override
                                public void run() {
                                    postDocumentChanged(docID);
                                }
                            });
                        }
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
                        DatabaseChange change = new DatabaseChange((Database) this, docIDs);
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
                DocumentChange change = new DocumentChange((Database) this, documentID);
                for (DocumentChangeListenerToken token : tokens)
                    token.notify(change);
            }
        }
    }

    private void prepareDocument(Document document) throws CouchbaseLiteException {
        mustBeOpen();

        if (document.getDatabase() == null)
            document.setDatabase((Database) this);
        else if (document.getDatabase() != this)
            throw new CouchbaseLiteException(CBLError.Domain.CBLErrorDomain, CBLError.Code.CBLErrorInvalidParameter);
    }

    // The main save method.
    private boolean save(Document document, boolean deletion, ConcurrencyControl concurrencyControl) throws CouchbaseLiteException {
        if (deletion && !document.exists())
            throw new CouchbaseLiteException(CBLError.Domain.CBLErrorDomain, CBLError.Code.CBLErrorNotFound);

        C4Document curDoc = null;
        C4Document newDoc = null;

        synchronized (lock) {
            prepareDocument(document);
            boolean commit = false;
            beginTransaction();
            try {

                if (deletion) {
                    // Check existing, NO-OPS if the document doesn't exist:
                    try {
                        curDoc = getC4Database().get(document.getId(), true);
                    } catch (LiteCoreException e) {
                        if (e.domain == C4ErrorDomain.LiteCoreDomain && e.code == LiteCoreError.kC4ErrorNotFound)
                            return true;
                        else
                            throw CBLStatus.convertException(e);
                    }
                }

                try {
                    newDoc = save(document, null, deletion);
                    commit = true;
                } catch (LiteCoreException e) {
                    // non-conflict error
                    if (!(e.domain == C4ErrorDomain.LiteCoreDomain && e.code == LiteCoreError.kC4ErrorConflict))
                        throw CBLStatus.convertException(e);
                }

                // Handle conflict:
                if (newDoc == null) {
                    if (concurrencyControl.equals(ConcurrencyControl.OPTIMISTIC)) {
                        return false;
                    }

                    // If deletion and the current doc has already been deleted:
                    if (deletion && curDoc.deleted()) {
                        document.replaceC4Document(curDoc);
                        curDoc = null; // prevent to call curDoc.free() in finally block
                        return true;
                    }

                    if (curDoc == null) {
                        try {
                            curDoc = getC4Database().get(document.getId(), true);
                        } catch (LiteCoreException e) {
                            // unexpected error
                            throw CBLStatus.convertRuntimeException(e);
                        }
                    }

                    // Save changes on the current branch:
                    try {
                        newDoc = save(document, curDoc, deletion);
                    } catch (LiteCoreException e) {
                        throw CBLStatus.convertException(e);
                    }
                }

                document.replaceC4Document(newDoc);
                commit = true;
            } finally {
                if (curDoc != null)
                    curDoc.free();

                try {
                    endTransaction(commit);// true: commit the transaction, false: abort the transaction
                } catch (CouchbaseLiteException e) {
                    if (newDoc != null)
                        newDoc.free();
                    throw e;
                }
            }
            return true;
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

    private Document resolveConflict(Document localDoc, Document remoteDoc) {
        if (remoteDoc.isDeleted())
            return remoteDoc;
        else if (localDoc.isDeleted())
            return localDoc;
        else if (localDoc.generation() > remoteDoc.generation())
            return localDoc;
        else if (localDoc.generation() < remoteDoc.generation())
            return remoteDoc;
        else if (localDoc.getRevID() != null && localDoc.getRevID().compareTo(remoteDoc.getRevID()) > 0)
            return localDoc;
        else
            return remoteDoc;
    }

    private boolean saveResolvedDocument(Document resolvedDoc, Document localDoc, Document remoteDoc)
            throws CouchbaseLiteException, LiteCoreException {

        synchronized (lock) {
            boolean commit = false;
            beginTransaction();
            try {
                if (remoteDoc != localDoc)
                    resolvedDoc.setDatabase((Database) this);

                // The remote branch has to win, so that the doc revision history matches the server's.
                String winningRevID = remoteDoc.getRevID();
                String losingRevID = localDoc.getRevID();

                byte[] mergedBody = null;
                if (resolvedDoc != remoteDoc) {
                    // Unless the remote revision is being used as-is, we need a new revision:
                    mergedBody = resolvedDoc.encode();
                    if (mergedBody == null)
                        return false;
                }

                // Tell LiteCore to do the resolution:
                C4Document rawDoc = localDoc.getC4doc();
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
        if (!postExecutor.isShutdown() && !postExecutor.isTerminated()) {
            ExecutorUtils.shutdownAndAwaitTermination(postExecutor, 60);
        }
        if (!queryExecutor.isShutdown() && !queryExecutor.isTerminated()) {
            ExecutorUtils.shutdownAndAwaitTermination(queryExecutor, 60);
        }
    }
}
