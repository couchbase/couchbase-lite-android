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

import com.couchbase.lite.internal.Misc;
import com.couchbase.lite.internal.bridge.LiteCoreBridge;
import com.couchbase.lite.internal.support.JsonUtils;
import com.couchbase.lite.internal.support.WeakValueHashMap;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.NativeLibraryLoader;

import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static android.R.attr.path;
import static com.couchbase.litecore.Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.Constants.LiteCoreError.kC4ErrorNotFound;

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
    private static final String LOG_TAG = Log.DATABASE;
    private static final String DB_EXTENSION = "cblite2";

    // TODO: DB00x - kC4DB_SharedKeys
    private static final int DEFAULT_DATABASE_FLAGS = com.couchbase.litecore.Database.Create
            | com.couchbase.litecore.Database.Bundle
            | com.couchbase.litecore.Database.AutoCompact;

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

    /**
     * Construct a  Database with a given name and database options.
     * If the database does not yet exist, it will be created, unless the `readOnly` option is used.
     *
     * @param name    The name of the database. May NOT contain capital letters!
     * @param options The database options, or null for the default options.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the open operation.
     */
    public Database(String name, DatabaseOptions options) throws CouchbaseLiteException {
        this.name = name;
        this.options = options != null ? options : new DatabaseOptions();
        open();
    }

    /**
     * Construct a  Database with a given name and database options.
     * If the database does not yet exist, it will be created.
     *
     * @param name The name of the database. May NOT contain capital letters!
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public Database(String name) throws CouchbaseLiteException {
        this(name, new DatabaseOptions());
    }

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

    /**
     * Closes a database.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void close() throws CouchbaseLiteException {
        if (c4db == null) return;

        Log.i(LOG_TAG, "Closing %s at path %s", this, path);

        if (unsavedDocuments.size() > 0)
            Log.w(LOG_TAG, "Closing database with %d unsaved docs", unsavedDocuments.size());

        documents.clear();
        documents = null;
        unsavedDocuments.clear();
        unsavedDocuments = null;

        try {
            c4db.close();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }

        // TODO: DB005 - free observer

        c4db.free();
        c4db = null;
    }

    /**
     * Changes the database's encryption key, or removes encryption if the new key is null.
     *
     * @param key The encryption key in the form of an String (a password) or
     *            an byte[] object exactly 32 bytes in length (a raw AES key.)
     *            If a string is given, it will be internally converted to a raw key
     *            using 64,000 rounds of PBKDF2 hashing. A null value will decrypt the database.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void changeEncryptionKey(Object key) throws CouchbaseLiteException {
        // TODO: DB00x
        throw new UnsupportedOperationException("Work in Progress!");
    }

    /**
     * Deletes a database.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void delete() throws CouchbaseLiteException {
        try {
            c4db.delete();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
        c4db.free();
        c4db = null;
        // TODO: DB005 - free observer
    }

    /**
     * Deletes a database of the given name in the given directory.
     *
     * @param name the database's name
     * @param dir  the path where the database is located.
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public static void delete(String name, File dir) throws CouchbaseLiteException {
        File path = getDatabasePath(dir, name);
        try {
            Log.e(LOG_TAG, "delete(): path=%s", path.toString());
            com.couchbase.litecore.Database.deleteAtPath(path.getPath(), DEFAULT_DATABASE_FLAGS);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    /**
     * Checks whether a database of the given name exists in the given directory or not.
     *
     * @param name the database's name
     * @param dir  the path where the database is located.
     * @return true if exists, false otherwise.
     */
    public static boolean databaseExists(String name, File dir) {
        if (name == null || dir == null)
            throw new IllegalArgumentException("name and/or dir arguments are null.");
        return getDatabasePath(dir, name).exists();
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

    /**
     * Gets or creates a Document object with the given ID.
     * The existence of the Document in the database can be checked by checking its documentExists() method.
     * Documents are cached, so there will never be more than one instance in this Database
     * object at a time with the same documentID.
     *
     * @param docID the document ID
     * @return the Document object
     */
    public Document getDocument(String docID) {
        return getDocument(docID, false);
    }

    // TODO: DB00x Model will be implemented later
    // func getDocument<T:DocumentModel>(type: T.Type) -> T
    // func getDocument<T:DocumentModel>(id: String?, type: T.Type) -> T

    /**
     * Checks whether a document of the given document ID exists in the database or not.
     *
     * @param docID the document ID
     * @return true if exists, false otherwise
     */
    public boolean documentExists(String docID) {
        try {
            getDocument(docID, true);
            return true;
        } catch (CouchbaseLiteException e) {
            if (e.getDomain() == LiteCoreDomain && e.getCode() == kC4ErrorNotFound)
                return false;

            // unexpected error...
            Log.w(LOG_TAG, "Unexpected Error with calling documentExists(docID => %s) method.", e, docID);
            return false;
        }
    }

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
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    // TODO: DB005
    // var conflictResolver: ConflictResolver? { get set }

    // TODO: DB005 - Notification will be implemented
    // func addChangeListener(docListener: DocumentChangeListener)
    // func removeChangeListener(docListener: DocumentChangeListener)

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
     * Creates an index based on the given expressions, index type, and index options. This will
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
                            IndexOptions options) throws CouchbaseLiteException {
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
            c4db.createIndex(json, type.getValue(), language, ignoreDiacritics);
        } catch (JSONException e) {
            throw new CouchbaseLiteException(e);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s[%s]", super.toString(), name);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    // Instead of clone()
    /* package */ Database copy(){
        return new Database(this.name, this.options);
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

        // TODO: DB00x - Maybe change to C4DatabaseConfig??
        int databaseFlags = getDatabaseFlags();

        // TODO: DB00x encryptionAlgorithm, encryptionKey
        int encryptionAlgorithm = com.couchbase.litecore.Database.NoEncryption;
        byte[] encryptionKey = null;

        Log.i(LOG_TAG, "Opening %s at path %s", this, dbFile.getPath());

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

        // TODO: DB00x SharedKey
        // TODO: DB005 Observation

        documents = new WeakValueHashMap<>();
        unsavedDocuments = new HashSet<>();
    }

    private int getDatabaseFlags() {
        int databaseFlags = DEFAULT_DATABASE_FLAGS;
        if (options.isReadOnly())
            databaseFlags |= com.couchbase.litecore.Database.ReadOnly;
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
        // TODO: DB00x - CBL Java - Windows - This does not work with Windows platform.
        name = name.replaceAll("/", ":");
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
            // TODO: I don't think calling Database method from Document consturctor is straightforward.
            doc = new Document(this, docID, mustExist);
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







