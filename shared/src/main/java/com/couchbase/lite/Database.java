package com.couchbase.lite;

public final class Database {
    private String name;
    private String path;

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

    public void delete(String name, String directory) throws CouchbaseLiteException {

    }

    public void exists(String name, String directory) throws CouchbaseLiteException {

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

    public boolean exists(String docID) {
        return false;
    }

    public void inBatch(Runnable action) throws CouchbaseLiteException {

    }

    // TODO:
    // var conflictResolver: ConflictResolver? { get set }

    // TODO: Notification will be implemented in DB4
    // func addChangeListener(docListener: DocumentChangeListener)
    // func removeChangeListener(docListener: DocumentChangeListener)
}
