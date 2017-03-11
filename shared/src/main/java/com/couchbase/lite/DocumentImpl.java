package com.couchbase.lite;

import java.util.Map;

class DocumentImpl implements Document {

    private Database db;
    private String id;
    private com.couchbase.litecore.Document _doc;

    DocumentImpl(Database db, String docID, boolean mustExist) throws CouchbaseLiteException {
        this.db = db;
        this.id = docID;

        // TODO: Reconsider if loading litecore.Document in constructor is good.
        //       Should we pass litecoreDocument to Document constructor?
        //       Current impl: DB.getDoc() -> Doc constructor -> db.read().....
        load(mustExist);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    @Override
    public Database getDatabase() {
        return db;
    }

    @Override
    public ConflictResolver getConflictResolver() {
        return null;
    }

    @Override
    public void setConflictResolver(ConflictResolver conflictResolver) {

    }

    @Override
    public String getID() {
        return id;
    }

    @Override
    public long getSequence() {
        return 0;
    }

    @Override
    public boolean isDeleted() {
        return false;
    }

    @Override
    public boolean exists() {
        return false;
    }

    @Override
    public void save() throws CouchbaseLiteException {
        // TODO: Need to implement ConflictResolver
        save(null, false);
    }

    @Override
    public void delete() throws CouchbaseLiteException {

    }

    @Override
    public void purge() throws CouchbaseLiteException {

    }

    @Override
    public void revert() {

    }

    @Override
    public void addChangeListener(DocumentChangeListener listener) {

    }

    @Override
    public void removeChangeListener(DocumentChangeListener listener) {

    }

    @Override
    public Map<String, Object> getProperties() {
        return null;
    }

    @Override
    public void setProperties(Map<String, Object> properties) {

    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    private void load(boolean mustExist) throws CouchbaseLiteException {

        _doc = db.read(id, mustExist);
        // NOTE: _doc should not be null.

        // TODO: setC4Doc() is Properties related

        // TODO: hasChanges = false. Notification releated
    }

    private void save(ConflictResolver resolver, boolean deletion) throws CouchbaseLiteException {

    }

}
