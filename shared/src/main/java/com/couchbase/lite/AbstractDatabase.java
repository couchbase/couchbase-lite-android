//
// AbstractDatabase.java
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

import android.support.annotation.NonNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.json.JSONException;

import com.couchbase.lite.internal.core.C4BlobStore;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Constants.C4DatabaseFlags;
import com.couchbase.lite.internal.core.C4Constants.C4DocumentVersioning;
import com.couchbase.lite.internal.core.C4Constants.C4EncryptionAlgorithm;
import com.couchbase.lite.internal.core.C4Constants.C4ErrorDomain;
import com.couchbase.lite.internal.core.C4Constants.C4RevisionFlags;
import com.couchbase.lite.internal.core.C4Constants.LiteCoreError;
import com.couchbase.lite.internal.core.C4Database;
import com.couchbase.lite.internal.core.C4DatabaseChange;
import com.couchbase.lite.internal.core.C4DatabaseObserver;
import com.couchbase.lite.internal.core.C4DatabaseObserverListener;
import com.couchbase.lite.internal.core.C4Document;
import com.couchbase.lite.internal.core.CBLVersion;
import com.couchbase.lite.internal.core.SharedKeys;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.support.Run;
import com.couchbase.lite.internal.utils.ExecutorUtils;
import com.couchbase.lite.internal.utils.JsonUtils;
import com.couchbase.lite.utils.FileUtils;


/**
 * AbstractDatabase is a base class of A Couchbase Lite Database.
 */
abstract class AbstractDatabase {
    //---------------------------------------------
    // Load LiteCore library and its dependencies
    //---------------------------------------------

    /**
     * Gets the logging controller for the Couchbase Lite library to configure the
     * logging settings and add custom logging.
     * <p>
     * This is part of the Public API.
     */
    @SuppressWarnings("ConstantName")
    @NonNull
    public static final com.couchbase.lite.Log log;

    static {
        NativeLibraryLoader.load();
        log = new com.couchbase.lite.Log(); // Don't move this, the native library is needed
        Log.setLogLevel(LogDomain.ALL, LogLevel.WARNING);
    }

    //---------------------------------------------
    // Static variables
    //---------------------------------------------
    private static final LogDomain DOMAIN = LogDomain.DATABASE;
    private static final String DB_EXTENSION = "cblite2";
    private static final int MAX_CHANGES = 100;
    // How long to wait after a database opens before expiring docs
    private static final long HOUSEKEEPING_DELAY_AFTER_OPENING = 3;

    private static final int DEFAULT_DATABASE_FLAGS
        = C4DatabaseFlags.kC4DB_Create
        | C4DatabaseFlags.kC4DB_AutoCompact
        | C4DatabaseFlags.kC4DB_SharedKeys;

    // ---------------------------------------------
    // API - public static methods
    // ---------------------------------------------

    /**
     * Deletes a database of the given name in the given directory.
     *
     * @param name      the database's name
     * @param directory the path where the database is located.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public static void delete(@NonNull String name, @NonNull File directory) throws CouchbaseLiteException {
        if (name == null) { throw new IllegalArgumentException("name cannot be null."); }
        if (directory == null) { throw new IllegalArgumentException("directory cannot be null."); }
        if (!exists(name, directory)) {
            throw new CouchbaseLiteException(
                CBLError.Domain.CBLErrorDomain,
                CBLError.Code.CBLErrorNotFound);
        }

        final File path = getDatabasePath(directory, name);
        try {
            Log.i(DOMAIN, "delete(): path=%s", path.toString());
            C4Database.deleteAtPath(path.getPath());
        }
        catch (LiteCoreException e) {
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
    public static boolean exists(@NonNull String name, @NonNull File directory) {
        if (name == null) { throw new IllegalArgumentException("name cannot be null."); }
        if (directory == null) { throw new IllegalArgumentException("directory cannot be null."); }
        return getDatabasePath(directory, name).exists();
    }

    public static void copy(
        @NonNull File path,
        @NonNull String name,
        @NonNull DatabaseConfiguration config)
        throws CouchbaseLiteException {
        if (path == null) { throw new IllegalArgumentException("path cannot be null."); }
        if (name == null) { throw new IllegalArgumentException("name cannot be null."); }
        if (config == null) { throw new IllegalArgumentException("config cannot be null."); }

        String fromPath = path.getPath();
        if (fromPath.charAt(fromPath.length() - 1) != File.separatorChar) { fromPath += File.separator; }
        String toPath = getDatabasePath(new File(config.getDirectory()), name).getPath();
        if (toPath.charAt(toPath.length() - 1) != File.separatorChar) { toPath += File.separator; }

        // Set the temp directory based on Database Configuration:
        config.setTempDir();

        try {
            C4Database.copy(
                fromPath,
                toPath,
                DEFAULT_DATABASE_FLAGS,
                null,
                C4DocumentVersioning.kC4RevisionTrees,
                C4EncryptionAlgorithm.kC4EncryptionNone,
                null);
        }
        catch (LiteCoreException e) {
            FileUtils.deleteRecursive(toPath);
            throw CBLStatus.convertException(e);
        }
    }

    /**
     * Set log level for the given log domain.
     *
     * @param domain The log domain
     * @param level  The log level
     * @deprecated As of 2.5 because it is being replaced with the
     * {@link com.couchbase.lite.Log#getConsole() getConsole} method
     * from the {@link #log log} property.  This method has
     * been replaced with a no-op to preserve API compatibility.
     */
    @Deprecated
    public static void setLogLevel(@NonNull LogDomain domain, @NonNull LogLevel level) {
        if (domain == null) { throw new IllegalArgumentException("domain cannot be null."); }
        if (level == null) { throw new IllegalArgumentException("level cannot be null."); }

        Log.setLogLevel(domain, level);
    }

    private static File getDatabasePath(File dir, String name) {
        name = name.replaceAll("/", ":");
        name = String.format(Locale.ENGLISH, "%s.%s", name, DB_EXTENSION);
        return new File(dir, name);
    }

    //---------------------------------------------
    // Member variables
    //---------------------------------------------

    final Object lock = new Object();     // Main database lock object for thread-safety
    final DatabaseConfiguration config;

    private final SharedKeys sharedKeys;
    private final boolean shellMode;

    private ScheduledExecutorService queryExecutor; // Executor for LiveQuery. one per db.
    private ScheduledExecutorService postExecutor;  // Executor for posting Database/Document Change notification
    private ChangeNotifier<DatabaseChange> dbChangeNotifier;

    private C4DatabaseObserver c4DbObserver;
    private Map<String, DocumentChangeNotifier> docChangeNotifiers;
    private Set<Replicator> activeReplications;

    // Attributes:
    private Set<LiveQuery> activeLiveQueries;
    private Timer purgeTimer;
    private String name;

    protected C4Database c4db;

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
    protected AbstractDatabase(@NonNull String name, @NonNull DatabaseConfiguration config)
        throws CouchbaseLiteException {
        // Logging:
        Run.once("DATABASE_INIT_LOGGING", new Runnable() {
            @Override
            public void run() {
                // Logging version number of CBL
                Log.info(DOMAIN, CBLVersion.getUserAgent());

                // Check file logging
                if (Database.log.getFile().getConfig() == null) {
                    Log.w(
                        DOMAIN,
                        "Database.log.getFile().getConfig() is null, meaning file logging is disabled.  "
                        + "Log files required for product support are not being generated.");
                }
            }
        });

        if (name == null || name.length() == 0) { throw new IllegalArgumentException("id cannot be null."); }
        if (config == null) { throw new IllegalArgumentException("id cannot be null."); }

        // Name:
        this.name = name;

        // Copy configuration
        this.config = config.readonlyCopy();

        this.shellMode = false;
        this.postExecutor = Executors.newSingleThreadScheduledExecutor();
        this.queryExecutor = Executors.newSingleThreadScheduledExecutor();
        this.activeReplications = Collections.synchronizedSet(new HashSet<>());
        this.activeLiveQueries = Collections.synchronizedSet(new HashSet<>());

        // Set the temp directory based on Database Configuration:
        config.setTempDir();

        // Open the database:
        open();

        // Initialize a shared keys:
        this.sharedKeys = new SharedKeys(c4db);
    }

    /**
     * Initialize Database with a give C4Database object in the shell mode. The life of the
     * C4Database object will be managed by the caller. This is currently used for creating a
     * Dictionary as an input of the predict() method of the PredictiveModel.
     */
    protected AbstractDatabase(C4Database c4db) {
        this.c4db = c4db;
        this.config = null;
        this.shellMode = true;
        this.sharedKeys = null;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    // GET EXISTING DOCUMENT

    /**
     * Return the database name
     *
     * @return the database's name
     */
    @NonNull
    public String getName() {
        return this.name;
    }

    /**
     * Return the database's path. If the database is closed or deleted, null value will be returned.
     *
     * @return the database's path.
     */
    public String getPath() {
        synchronized (lock) {
            return c4db != null ? getC4Database().getPath() : null;
        }
    }

    /**
     * The number of documents in the database.
     *
     * @return the number of documents in the database, 0 if database is closed.
     */
    public long getCount() {
        synchronized (lock) {
            return c4db != null ? getC4Database().getDocumentCount() : 0L;
        }
    }

    /**
     * Returns a READONLY config object which will throw a runtime exception
     * when any setter methods are called.
     *
     * @return the READONLY copied config object
     */
    @NonNull
    public DatabaseConfiguration getConfig() {
        return config.readonlyCopy();
    }

    /**
     * Gets an existing Document object with the given ID. If the document with the given ID doesn't
     * exist in the database, the value returned will be null.
     *
     * @param id the document ID
     * @return the Document object
     */
    public Document getDocument(@NonNull String id) {
        if (id == null) { throw new IllegalArgumentException("id cannot be null."); }

        synchronized (lock) {
            mustBeOpen();
            try {
                return new Document((Database) this, id, false);
            }
            catch (CouchbaseLiteException ex) {
                // only 404 - Not Found error throws CouchbaseLiteException
                return null;
            }
        }
    }

    /**
     * Saves a document to the database. When write operations are executed
     * concurrently, the last writer will overwrite all other written values.
     * Calling this method is the same as calling the ave(MutableDocument, ConcurrencyControl)
     * method with LAST_WRITE_WINS concurrency control.
     *
     * @param document The document.
     * @throws CouchbaseLiteException
     */
    public void save(@NonNull MutableDocument document) throws CouchbaseLiteException {
        save(document, ConcurrencyControl.LAST_WRITE_WINS);
    }

    /**
     * Saves a document to the database. When used with LAST_WRITE_WINS
     * concurrency control, the last write operation will win if there is a conflict.
     * When used with FAIL_ON_CONFLICT concurrency control, save will fail with false value
     *
     * @param document           The document.
     * @param concurrencyControl The concurrency control.
     * @return true if successful. false if the FAIL_ON_CONFLICT concurrency
     * @throws CouchbaseLiteException
     */
    public boolean save(@NonNull MutableDocument document, @NonNull ConcurrencyControl concurrencyControl)
        throws CouchbaseLiteException {
        if (document == null) { throw new IllegalArgumentException("document cannot be null."); }
        if (concurrencyControl == null) { throw new IllegalArgumentException("concurrencyControl cannot be null."); }

        // NOTE: synchronized in save(Document, boolean) method
        return save(document, false, concurrencyControl);
    }

    /**
     * Deletes a document from the database. When write operations are executed
     * concurrently, the last writer will overwrite all other written values.
     * Calling this function is the same as calling the delete(Document, ConcurrencyControl)
     * function with LAST_WRITE_WINS concurrency control.
     *
     * @param document 　The document.
     * @throws CouchbaseLiteException
     */
    public void delete(@NonNull Document document) throws CouchbaseLiteException {
        delete(document, ConcurrencyControl.LAST_WRITE_WINS);
    }

    /**
     * Deletes a document from the database. When used with lastWriteWins concurrency
     * control, the last write operation will win if there is a conflict.
     * When used with FAIL_ON_CONFLICT concurrency control, delete will fail with
     * 'false' value returned.
     *
     * @param document           The document.
     * @param concurrencyControl The concurrency control.
     * @throws CouchbaseLiteException
     */
    public boolean delete(@NonNull Document document, @NonNull ConcurrencyControl concurrencyControl)
        throws CouchbaseLiteException {
        if (document == null) { throw new IllegalArgumentException("document cannot be null."); }
        if (concurrencyControl == null) { throw new IllegalArgumentException("concurrencyControl cannot be null."); }

        // NOTE: synchronized in save(Document, boolean) method
        return save(document, true, concurrencyControl);
    }

    // Batch operations:

    /**
     * Purges the given document from the database. This is more drastic than delete(Document),
     * it removes all traces of the document. The purge will NOT be replicated to other databases.
     *
     * @param document
     */
    public void purge(@NonNull Document document) throws CouchbaseLiteException {
        if (document == null) { throw new IllegalArgumentException("document cannot be null."); }

        if (document.isNewDocument()) {
            throw new CouchbaseLiteException("Document doesn't exist in the database.",
                CBLError.Domain.CBLErrorDomain, CBLError.Code.CBLErrorNotFound);
        }

        synchronized (lock) {
            prepareDocument(document);

            boolean commit = false;
            beginTransaction();
            try {
                // revID: null, all revisions are purged.
                if (document.getC4doc().purgeRevision(null) >= 0) {
                    document.getC4doc().save(0);
                    // Reset c4doc:
                    document.replaceC4Document(null);
                    commit = true;
                }
            }
            catch (LiteCoreException e) {
                throw CBLStatus.convertException(e);
            }
            finally {
                endTransaction(commit);
            }
        }
    }

    /**
     * Purges the given document id for the document in database. This is more drastic than delete(Document),
     * it removes all traces of the document. The purge will NOT be replicated to other databases.
     *
     * @param id the document ID
     */
    public void purge(@NonNull String id) throws CouchbaseLiteException {
        if (id == null) { throw new IllegalArgumentException("document id cannot be null."); }

        synchronized (lock) {
            boolean commit = false;
            beginTransaction();
            try {
                getC4Database().purgeDoc(id);
                commit = true;
            }
            catch (LiteCoreException e) {
                throw CBLStatus.convertException(e);
            }
            finally {
                endTransaction(commit);
            }
        }
    }

    // Database changes:

    /**
     * Sets an expiration date on a document. After this time, the document
     * will be purged from the database.
     *
     * @param id         The ID of the Document
     * @param expiration Nullable expiration timestamp as a Date, set timestamp to null
     *                   to remove expiration date time from doc.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void setDocumentExpiration(@NonNull String id, Date expiration) throws CouchbaseLiteException {
        if (id == null) { throw new IllegalArgumentException("document id cannot be null."); }

        synchronized (lock) {
            try {
                if (expiration == null) {
                    getC4Database().setExpiration(id, 0);
                }
                else {
                    getC4Database().setExpiration(id, expiration.getTime());
                }
                scheduleDocumentExpiration(0);
            }
            catch (LiteCoreException e) {
                throw CBLStatus.convertException(e);
            }
        }
    }

    /**
     * Returns the expiration time of the document. null will be returned if there is
     * no expiration time set
     *
     * @param id The ID of the Document
     * @return Date a nullable expiration timestamp of the document or null if time not set.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public Date getDocumentExpiration(@NonNull String id) throws CouchbaseLiteException {
        if (id == null) { throw new IllegalArgumentException("document id cannot be null."); }

        synchronized (lock) {
            try {
                if (getC4Database().get(id, true) == null) {
                    throw new CouchbaseLiteException("Document doesn't exist in the database.",
                        CBLError.Domain.CBLErrorDomain, CBLError.Code.CBLErrorNotFound);
                }
                final long timestamp = getC4Database().getExpiration(id);
                return (timestamp == 0) ? null : new Date(timestamp);
            }
            catch (LiteCoreException e) {
                throw CBLStatus.convertException(e);
            }
        }
    }

    /**
     * Runs a group of database operations in a batch. Use this when performing bulk write operations
     * like multiple inserts/updates; it saves the overhead of multiple database commits, greatly
     * improving performance.
     *
     * @param runnable the action which is implementation of Runnable interface
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void inBatch(@NonNull Runnable runnable) throws CouchbaseLiteException {
        if (runnable == null) { throw new IllegalArgumentException("runnable cannot be null."); }

        synchronized (lock) {
            mustBeOpen();
            try {
                boolean commit = false;
                getC4Database().beginTransaction();
                try {
                    try {
                        runnable.run();
                        commit = true;
                    }
                    catch (RuntimeException e) {
                        throw new CouchbaseLiteException(e);
                    }
                }
                finally {
                    getC4Database().endTransaction(commit);
                }
            }
            catch (LiteCoreException e) {
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
            }
            catch (LiteCoreException e) {
                throw CBLStatus.convertException(e);
            }
        }
    }

    // Document changes:

    /**
     * Set the given DatabaseChangeListener to the this database.
     *
     * @param listener
     */
    @NonNull
    public ListenerToken addChangeListener(@NonNull DatabaseChangeListener listener) {
        return addChangeListener(null, listener);
    }

    // Others:

    /**
     * Set the given DatabaseChangeListener to the this database with an executor on which the
     * the changes will be posted to the listener.
     *
     * @param listener
     */
    @NonNull
    public ListenerToken addChangeListener(Executor executor, @NonNull DatabaseChangeListener listener) {
        if (listener == null) { throw new IllegalArgumentException("listener cannot be null."); }

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
    public void removeChangeListener(@NonNull ListenerToken token) {
        if (token == null) { throw new IllegalArgumentException("token cannot be null."); }

        synchronized (lock) {
            mustBeOpen();
            if (token instanceof ChangeListenerToken && ((ChangeListenerToken) token).getKey() != null) {
                removeDocumentChangeListener((ChangeListenerToken) token);
            }
            else { removeDatabaseChangeListener(token); }
        }
    }

    // Maintenance operations:

    /**
     * Add the given DocumentChangeListener to the specified document.
     */
    @NonNull
    public ListenerToken addDocumentChangeListener(
        @NonNull String id,
        @NonNull DocumentChangeListener listener) {
        return addDocumentChangeListener(id, null, listener);
    }

    /**
     * Add the given DocumentChangeListener to the specified document with an executor on which
     * the changes will be posted to the listener.
     */
    @NonNull
    public ListenerToken addDocumentChangeListener(
        @NonNull String id,
        Executor executor,
        @NonNull DocumentChangeListener listener) {
        if (id == null) { throw new IllegalArgumentException("id cannot be null."); }
        if (listener == null) { throw new IllegalArgumentException("listener cannot be null."); }

        synchronized (lock) {
            mustBeOpen();
            return addDocumentChangeListener(executor, listener, id);
        }
    }

    /**
     * Closes a database.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void close() throws CouchbaseLiteException {
        synchronized (lock) {
            if (c4db == null) { return; }

            Log.i(DOMAIN, "Closing %s at path %s", this, getC4Database().getPath());

            if (activeReplications.size() > 0) {
                throw new CouchbaseLiteException(
                    "Cannot close the database.  Please stop all of the replicators before closing the database.",
                    CBLError.Domain.CBLErrorDomain, CBLError.Code.CBLErrorBusy);
            }


            if (activeLiveQueries.size() > 0) {
                throw new CouchbaseLiteException(
                    "Cannot close the database.  Please remove all of the query listeners before closing the database.",
                    CBLError.Domain.CBLErrorDomain,
                    CBLError.Code.CBLErrorBusy);
            }

            // cancel purge timer
            cancelPurgeTimer();

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

            Log.i(DOMAIN, "Deleting %s at path %s", this, getC4Database().getPath());

            if (activeReplications.size() > 0) {
                throw new CouchbaseLiteException(
                    "Cannot delete the database.  Please stop all of the replicators before closing the database.",
                    CBLError.Domain.CBLErrorDomain, CBLError.Code.CBLErrorBusy);
            }


            if (activeLiveQueries.size() > 0) {
                throw new CouchbaseLiteException(
                    "Cannot delete the database.  Please remove all of the query listeners before closing the "
                        + "database.",
                    CBLError.Domain.CBLErrorDomain,
                    CBLError.Code.CBLErrorBusy);
            }

            // cancel purge timer
            cancelPurgeTimer();

            // delete db
            deleteC4DB();

            // release instances
            freeC4Observers();
            freeC4DB();

            // shutdown executor service
            shutdownExecutorService();
        }
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public List<String> getIndexes() throws CouchbaseLiteException {
        synchronized (lock) {
            mustBeOpen();
            try { return (List<String>) c4db.getIndexes().asObject(); }
            catch (LiteCoreException e) {
                throw CBLStatus.convertException(e);
            }
        }
    }

    public void createIndex(@NonNull String name, @NonNull Index index) throws CouchbaseLiteException {
        if (name == null) { throw new IllegalArgumentException("name cannot be null."); }
        if (index == null) { throw new IllegalArgumentException("index cannot be null."); }
        synchronized (lock) {
            mustBeOpen();
            try {
                final AbstractIndex abstractIndex = (AbstractIndex) index;
                final String json = JsonUtils.toJson(abstractIndex.items()).toString();
                getC4Database().createIndex(
                    name,
                    json,
                    abstractIndex.type().getValue(),
                    abstractIndex.language(),
                    abstractIndex.ignoreAccents());
            }
            catch (LiteCoreException e) {
                throw CBLStatus.convertException(e);
            }
            catch (JSONException e) {
                throw new CouchbaseLiteException(e);
            }
        }
    }

    public void deleteIndex(@NonNull String name) throws CouchbaseLiteException {
        synchronized (lock) {
            mustBeOpen();
            try {
                c4db.deleteIndex(name);
            }
            catch (LiteCoreException e) {
                throw CBLStatus.convertException(e);
            }
        }
    }

    //---------------------------------------------
    // Override public method
    //---------------------------------------------

    @NonNull
    @Override
    public String toString() {
        return "Database@" + Integer.toHexString(hashCode()) + "{" + "name='" + name + '\'' + '}';
    }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    protected void mustBeOpen() {
        if (c4db == null) { throw new IllegalStateException("Attempt to perform an operation on a closed database"); }
    }

    @SuppressWarnings("NoFinalizer")
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
        if (other == null) { return false; }
        final File path = getFilePath();
        final File otherPath = other.getFilePath();
        if (path == null && otherPath == null) { return true; }
        else if ((path == null) || (otherPath == null)) { return false; }
        else { return path.equals(otherPath); }
    }

    C4BlobStore getBlobStore() throws LiteCoreException {
        synchronized (lock) {
            mustBeOpen();
            return c4db.getBlobStore();
        }
    }

    // Instead of clone()
    Database copy() throws CouchbaseLiteException {
        return new Database(this.name, this.config);
    }

    //////// DATABASES:

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

    SharedKeys getSharedKeys() {
        return sharedKeys;
    }

    //////// DOCUMENTS:

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
                final Document localDoc = new Document((Database) this, docID, true);

                // Read the conflicting remote revision:
                final Document remoteDoc = new Document((Database) this, docID, true);
                try {
                    if (!remoteDoc.selectConflictingRevision()) {
                        Log.w(DOMAIN, "Unable to select conflicting revision for '%s', skipping...", docID);
                        return;
                    }
                }
                catch (LiteCoreException e) {
                    throw CBLStatus.convertException(e);
                }

                // Resolve conflict:
                Log.v(DOMAIN, "Resolving doc '%s' (local=%s and remote=%s)", docID,
                    localDoc.getRevID(), remoteDoc.getRevID());
                final Document resolvedDoc = resolveConflict(localDoc, remoteDoc);

                // Save resolved document:
                try {
                    saveResolvedDocument(resolvedDoc, localDoc, remoteDoc);
                }
                catch (LiteCoreException e) {
                    throw CBLStatus.convertException(e);
                }

                commit = true;
            }
            finally {
                endTransaction(commit);
            }
        }
    }

    File getFilePath() {
        final String path = getPath();
        return path != null ? new File(path) : null;
    }

    //////// Execution:

    void scheduleOnPostNotificationExecutor(@NonNull Runnable runnable, /* Milliseconds */ long delay) {
        try { postExecutor.schedule(runnable, delay, TimeUnit.MILLISECONDS); }
        catch (RejectedExecutionException ignore) { }
    }

    void scheduleOnQueryExecutor(@NonNull Runnable runnable, /* Milliseconds */ long delay) {
        try { queryExecutor.schedule(runnable, delay, TimeUnit.MILLISECONDS); }
        catch (RejectedExecutionException ignore) { }
    }

    abstract int getEncryptionAlgorithm();

    abstract byte[] getEncryptionKey();

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    //////// DATABASES:

    private void beginTransaction() throws CouchbaseLiteException {
        try {
            getC4Database().beginTransaction();
        }
        catch (LiteCoreException e) {
            throw CBLStatus.convertException(e);
        }
    }

    private void endTransaction(boolean commit) throws CouchbaseLiteException {
        try {
            getC4Database().endTransaction(commit);
        }
        catch (LiteCoreException e) {
            throw CBLStatus.convertException(e);
        }
    }

    private void open() throws CouchbaseLiteException {
        if (c4db != null) { return; }

        final File dir = config.getDirectory() != null ? new File(config.getDirectory()) : getDefaultDirectory();
        setupDirectory(dir);

        final File dbFile = getDatabasePath(dir, this.name);
        final int databaseFlags = getDatabaseFlags();

        Log.i(DOMAIN, "Opening %s at path %s", this, dbFile.getPath());

        try {
            c4db = new C4Database(
                dbFile.getPath(),
                databaseFlags,
                null,
                C4DocumentVersioning.kC4RevisionTrees,
                getEncryptionAlgorithm(),
                getEncryptionKey());
        }
        catch (LiteCoreException e) {
            if (e.code == CBLError.Code.CBLErrorNotADatabaseFile) {
                throw new CouchbaseLiteException("The provided encryption key was incorrect.", e,
                    CBLError.Domain.CBLErrorDomain, e.code);
            }
            else if (e.code == CBLError.Code.CBLErrorCantOpenFile) {
                throw new CouchbaseLiteException("TUnable to create database directory.", e,
                    CBLError.Domain.CBLErrorDomain, e.code);
            }
            else { throw CBLStatus.convertException(e); }
        }

        c4DbObserver = null;
        dbChangeNotifier = null;
        docChangeNotifiers = new HashMap<>();

        scheduleDocumentExpiration(HOUSEKEEPING_DELAY_AFTER_OPENING);
    }

    private int getDatabaseFlags() {
        return DEFAULT_DATABASE_FLAGS;
    }

    private File getDefaultDirectory() {
        throw new UnsupportedOperationException("getDefaultDirectory() is not supported.");
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED_BAD_PRACTICE")
    private void setupDirectory(File dir) throws CouchbaseLiteException {
        if (!dir.exists()) { dir.mkdirs(); }
        if (!dir.isDirectory()) {
            throw new CouchbaseLiteException(String.format(Locale.ENGLISH,
                "Unable to create directory for: %s.", dir));
        }
    }

    // --- C4Database
    private void closeC4DB() throws CouchbaseLiteException {
        try {
            getC4Database().close();
        }
        catch (LiteCoreException e) {
            throw CBLStatus.convertException(e);
        }
    }

    //////// DOCUMENTS:

    private void deleteC4DB() throws CouchbaseLiteException {
        try {
            getC4Database().delete();
        }
        catch (LiteCoreException e) {
            throw CBLStatus.convertException(e);
        }
    }

    private void freeC4DB() {
        if (c4db != null && !shellMode) {
            getC4Database().free();
            c4db = null;
        }
    }

    // Database changes:
    private ListenerToken addDatabaseChangeListener(Executor executor, DatabaseChangeListener listener) {
        // NOTE: caller method is synchronized.
        if (dbChangeNotifier == null) {
            dbChangeNotifier = new ChangeNotifier<>();
            registerC4DBObserver();
        }
        return dbChangeNotifier.addChangeListener(executor, listener);
    }

    // --- Notification: - C4DatabaseObserver/C4DocumentObserver

    private void removeDatabaseChangeListener(ListenerToken token) {
        // NOTE: caller method is synchronized.
        if (dbChangeNotifier.removeChangeListener(token) == 0) {
            freeC4DBObserver();
            dbChangeNotifier = null;
        }
    }

    // Document changes:
    private ListenerToken addDocumentChangeListener(
        Executor executor,
        DocumentChangeListener listener,
        String docID) {
        // NOTE: caller method is synchronized.
        DocumentChangeNotifier docNotifier = docChangeNotifiers.get(docID);
        if (docNotifier == null) {
            docNotifier = new DocumentChangeNotifier((Database) this, docID);
            docChangeNotifiers.put(docID, docNotifier);
        }
        final ChangeListenerToken token = docNotifier.addChangeListener(executor, listener);
        token.setKey(docID);
        return token;
    }

    private void removeDocumentChangeListener(ChangeListenerToken token) {
        // NOTE: caller method is synchronized.
        final String docID = (String) token.getKey();
        if (docChangeNotifiers.containsKey(docID)) {
            final DocumentChangeNotifier notifier = docChangeNotifiers.get(docID);
            if (notifier != null && notifier.removeChangeListener(token) == 0) {
                notifier.stop();
                docChangeNotifiers.remove(docID);
            }
        }
    }

    private void registerC4DBObserver() {
        c4DbObserver = c4db.createDatabaseObserver(new C4DatabaseObserverListener() {
            @Override
            public void callback(C4DatabaseObserver observer, Object context) {
                scheduleOnPostNotificationExecutor(new Runnable() {
                    @Override
                    public void run() {
                        postDatabaseChanged();
                    }
                }, 0);
            }
        }, this);
    }

    private void freeC4DBObserver() {
        if (c4DbObserver != null) {
            c4DbObserver.free();
            c4DbObserver = null;
        }
    }

    private void freeC4Observers() {
        freeC4DBObserver();

        if (docChangeNotifiers != null) {
            for (DocumentChangeNotifier notifier : docChangeNotifiers.values()) { notifier.stop(); }
            docChangeNotifiers.clear();
        }
    }

    private void postDatabaseChanged() {
        synchronized (lock) {
            if (c4DbObserver == null || c4db == null || getC4Database().isInTransaction()) { return; }

            boolean external = false;
            int nChanges;
            List<String> docIDs = new ArrayList<>();
            do {
                // Read changes in batches of kMaxChanges:
                final C4DatabaseChange[] c4DbChanges = c4DbObserver.getChanges(MAX_CHANGES);
                nChanges = (c4DbChanges == null) ? 0 : c4DbChanges.length;
                final boolean newExternal = nChanges > 0 && c4DbChanges[0].isExternal();
                if (((nChanges <= 0) || (external != newExternal) || (docIDs.size() > 1000)) && (docIDs.size() > 0)) {
                    dbChangeNotifier.postChange(new DatabaseChange((Database) this, docIDs));
                    docIDs = new ArrayList<>();
                }

                external = newExternal;
                for (int i = 0; i < nChanges; i++) { docIDs.add(c4DbChanges[i].getDocID()); }
            }
            while (nChanges > 0);
        }
    }

    private void prepareDocument(Document document) throws CouchbaseLiteException {
        mustBeOpen();

        if (document.getDatabase() == null) { document.setDatabase((Database) this); }
        else if (document.getDatabase() != this) {
            throw new CouchbaseLiteException("Cannot operate on a document from another database.",
                CBLError.Domain.CBLErrorDomain, CBLError.Code.CBLErrorInvalidParameter);
        }
    }

    // The main save method.
    private boolean save(Document document, boolean deletion, ConcurrencyControl concurrencyControl)
        throws CouchbaseLiteException {
        if (deletion && !document.exists()) {
            throw new CouchbaseLiteException("Cannot delete a document that has not yet been saved.",
                CBLError.Domain.CBLErrorDomain, CBLError.Code.CBLErrorNotFound);
        }

        C4Document curDoc = null;
        C4Document newDoc = null;
        synchronized (lock) {
            mustBeOpen();
            prepareDocument(document);
            boolean commit = false;
            beginTransaction();
            try {
                try {
                    newDoc = save(document, null, deletion);
                    commit = true;
                }
                catch (LiteCoreException e) {
                    if (!(e.domain == C4ErrorDomain.LiteCoreDomain && e.code == LiteCoreError.kC4ErrorConflict)) {
                        throw CBLStatus.convertException(e);
                    }
                }

                if (newDoc == null) {
                    // Handle conflict:
                    if (concurrencyControl.equals(ConcurrencyControl.FAIL_ON_CONFLICT)) {
                        return false; // document is conflicted and return false because of OPTIMISTIC
                    }

                    try {
                        curDoc = getC4Database().get(document.getId(), true);
                    }
                    catch (LiteCoreException e) {
                        if (deletion
                            && e.domain == C4ErrorDomain.LiteCoreDomain && e.code == LiteCoreError.kC4ErrorNotFound) {
                            return true;
                        }
                        else { throw CBLStatus.convertException(e); }
                    }

                    if (deletion && curDoc.deleted()) {
                        document.replaceC4Document(curDoc);
                        curDoc = null; // NOTE: prevent to call curDoc.free() in finally block
                        return true;
                    }

                    // Save changes on the current branch:
                    // NOTE: curDoc null check is done in prev try-catch blcok
                    try {
                        newDoc = save(document, curDoc, deletion);
                    }
                    catch (LiteCoreException e) {
                        throw CBLStatus.convertException(e);
                    }
                }

                document.replaceC4Document(newDoc);
                commit = true;
            }
            finally {
                if (curDoc != null) {
                    curDoc.retain();
                    curDoc.release(); // curDoc is not retained
                }
                try {
                    endTransaction(commit); // true: commit the transaction, false: abort the transaction
                }
                catch (CouchbaseLiteException e) {
                    if (newDoc != null) {
                        newDoc.release(); // newDoc is already retained
                    }
                    throw e;
                }
            }
            return true;
        }
    }

    // Lower-level save method. On conflict, returns YES but sets *outDoc to NULL.
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE")
    private C4Document save(Document document, C4Document base, boolean deletion)
        throws LiteCoreException {
        FLSliceResult body = null;
        try {
            int revFlags = 0;
            if (deletion) { revFlags = C4RevisionFlags.kRevDeleted; }
            if (!deletion && !document.isEmpty()) {
                // Encode properties to Fleece data:
                body = document.encode();
                if (C4Document.dictContainsBlobs(body, sharedKeys.getFLSharedKeys())) {
                    revFlags |= C4Constants.C4RevisionFlags.kRevHasAttachments;
                }
            }

            // Save to database:
            final C4Document c4Doc = base != null ? base : document.getC4doc();
            if (c4Doc != null) { return c4Doc.update(body, revFlags); }

            return getC4Database().create(document.getId(), body, revFlags);
        }
        finally {
            if (body != null) { body.free(); }
        }
    }

    private Document resolveConflict(Document localDoc, Document remoteDoc) {
        if (remoteDoc.isDeleted()) { return remoteDoc; }
        else if (localDoc.isDeleted()) { return localDoc; }
        else if (localDoc.generation() > remoteDoc.generation()) { return localDoc; }
        else if (localDoc.generation() < remoteDoc.generation()) { return remoteDoc; }
        else if (localDoc.getRevID() != null && localDoc.getRevID().compareTo(remoteDoc.getRevID()) > 0) {
            return localDoc;
        }
        else { return remoteDoc; }
    }

    private boolean saveResolvedDocument(Document resolvedDoc, Document localDoc, Document remoteDoc)
        throws CouchbaseLiteException, LiteCoreException {

        synchronized (lock) {
            boolean commit = false;
            beginTransaction();
            try {
                if (remoteDoc != localDoc) { resolvedDoc.setDatabase((Database) this); }

                // The remote branch has to win, so that the doc revision history matches the server's.
                final String winningRevID = remoteDoc.getRevID();
                final String losingRevID = localDoc.getRevID();

                byte[] mergedBody = null;
                int mergedFlags = 0x00;
                if (resolvedDoc != remoteDoc) {
                    // Unless the remote revision is being used as-is, we need a new revision:
                    mergedBody = resolvedDoc.encode().getBuf();
                    if (mergedBody == null) { return false; }
                    if (resolvedDoc.isDeleted()) { mergedFlags |= C4RevisionFlags.kRevDeleted; }
                }

                // Tell LiteCore to do the resolution:
                final C4Document rawDoc = localDoc.getC4doc();
                rawDoc.resolveConflict(winningRevID, losingRevID, mergedBody, mergedFlags);
                rawDoc.save(0);
                Log.i(DOMAIN, "Conflict resolved as doc '%s' rev %s",
                    rawDoc.getDocID(), rawDoc.getRevID());

                commit = true;
            }
            finally {
                endTransaction(commit);
            }
            return commit;
        }
    }

    private void shutdownExecutorService() {
        ExecutorUtils.shutdownAndAwaitTermination(postExecutor, 60);
        ExecutorUtils.shutdownAndAwaitTermination(queryExecutor, 60);
    }

    private void scheduleDocumentExpiration(long minimumDelay /*milliseconds*/) {
        final long nextExpiration = getC4Database().nextDocExpiration();
        if (nextExpiration <= 0) { Log.v(DOMAIN, "No pending doc expirations"); }
        else {
            final long delay = Math.max(nextExpiration - System.currentTimeMillis(), minimumDelay);
            Log.v(DOMAIN, "Scheduling next doc expiration in %d sec", delay);
            synchronized (lock) {
                cancelPurgeTimer();
                purgeTimer = new Timer();
                purgeTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (isOpen()) { purgeExpiredDocuments(); }
                    }
                }, delay);
            }
        }
    }

    // #pragma mark - EXPIRATION:

    private void purgeExpiredDocuments() {
        // Aligning with database/document change notification to avoid race condition
        // between ending transaction and handling change notification when the documents
        // are purged
        scheduleOnPostNotificationExecutor(
            new Runnable() {
                @Override
                public void run() {
                    final int nPurged = getC4Database().purgeExpiredDocs();
                    Log.v(DOMAIN, "Purged %d expired documents", nPurged);
                    scheduleDocumentExpiration(1000);
                }
            },
            0);
    }

    private void cancelPurgeTimer() {
        synchronized (lock) {
            if (purgeTimer != null) {
                purgeTimer.cancel();
                purgeTimer = null;
            }
        }
    }
}
