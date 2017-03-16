package com.couchbase.lite;

interface Document extends Properties {

    Database getDatabase();

    ConflictResolver getConflictResolver();

    void setConflictResolver(ConflictResolver conflictResolver);

    String getID();

    long getSequence();

    boolean isDeleted();

    boolean exists();

    void save() throws CouchbaseLiteException;

    void delete() throws CouchbaseLiteException;

    void purge() throws CouchbaseLiteException;

    void revert();

    void addChangeListener(DocumentChangeListener listener);

    void removeChangeListener(DocumentChangeListener listener);

    //TODO:
    // func addChangeListener(propertyListener:PropertyChangeListener)
    // func removeChangeListener(propertyListener: DocumentChangeListener)
}
