package com.couchbase.lite;

import com.couchbase.lite.internal.bridge.LiteCoreBridge;
import com.couchbase.litecore.Constants;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.FLValue;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class DocumentImpl extends PropertiesImpl implements Document {

    private Database db;
    private String id;
    private com.couchbase.litecore.Document c4doc;

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

    //---------------------------------------------
    // Implementation of Document
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
        return c4doc.getSequence();
    }

    @Override
    public boolean isDeleted() {
        return c4doc.deleted();
    }

    @Override
    public boolean exists() {
        return c4doc.exists();
    }

    @Override
    public void save() throws CouchbaseLiteException {
        // TODO: DB005 - Need to implement ConflictResolver
        save(null, false);
    }

    @Override
    public void delete() throws CouchbaseLiteException {
        // TODO: DB005 - Need to implement ConflictResolver
        save(null, true);
    }

    @Override
    public void purge() throws CouchbaseLiteException {
        if (!exists())
            throw new CouchbaseLiteException("the document does not exist.");

        boolean commit = false;
        db.beginTransaction();
        try {
            // revID: null, all revisions are purged.
            if (c4doc.purgeRevision(null) >= 0) {
                c4doc.save(0);
                commit = true;
            }
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        } finally {
            db.endTransaction(commit);
        }

        // reload
        load(false);

        // reset
        resetChanges();
    }

    @Override
    public void revert() {
        resetChanges();
    }

    @Override
    public void addChangeListener(DocumentChangeListener listener) {
        // TODO: DB00x
    }

    @Override
    public void removeChangeListener(DocumentChangeListener listener) {
        // TODO: DB00x
    }

    //---------------------------------------------
    // Implementation of Iterable
    //---------------------------------------------

    @Override
    public Iterator iterator() {
        return null;
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    @Override
    void setHasChanges(boolean hasChanges) {
        if (this.hasChanges != hasChanges) {
            super.setHasChanges(hasChanges);
            getDatabase().unsavedDocument(this, hasChanges);
        }
    }

    @Override
    void markChanges() {
        super.markChanges();
        // TODO DB00x: send notification
    }


    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    private void load(boolean mustExist) throws CouchbaseLiteException {
        com.couchbase.litecore.Document doc = db.read(id, mustExist);
        // NOTE: c4doc should not be null.
        setC4Doc(doc);
        setHasChanges(false);
    }

    private void setC4Doc(com.couchbase.litecore.Document doc) throws CouchbaseLiteException {
        if (c4doc != null)
            c4doc.free();
        c4doc = doc;
        setRoot(null);
        if (c4doc != null) {
            byte[] body = null;
            try {
                body = c4doc.getSelectedBody();
            } catch (LiteCoreException e) {
                // TODO: <-- I don't have confidence about following logic
                if (e.domain != Constants.C4ErrorDomain.LiteCoreDomain || e.code != Constants.LiteCoreError.kC4ErrorDeleted)
                    throw LiteCoreBridge.convertException(e);
            }
            if (body != null && body.length > 0) {
                FLDict root = FLValue.fromData(body).asFLDict();
                setRoot(root);
            }
        }
    }

    private void save(ConflictResolver resolver, boolean deletion) throws CouchbaseLiteException {
        // No-op case of unchanged document:
        if (!hasChanges && !deletion && exists())
            return;

        com.couchbase.litecore.Document newDoc;

        // Begin a db transaction:
        boolean commit = false;
        db.beginTransaction();
        try {
            // Attempt to save. (On conflict, this will succeed but newDoc will be null.)
            newDoc = save(deletion);
            // TODO: DB005 Conflict handling
            commit = true;
        } finally {
            // End a db transaction.
            db.endTransaction(commit);
        }

        // Update my state and post a notification:
        setC4Doc(newDoc);
        setHasChanges(false);
        if (deletion)
            resetChanges();

        // TODO DB00x: postChangedNotificationExternal

        // NOTE: need to release newDoc? or replace with c4doc and releaes c4doc?
    }

    // Lower-level save method. On conflict, returns YES but sets *outDoc to NULL. */
    private com.couchbase.litecore.Document save(boolean deletion) {

        Map<String, Object> propertiesToSave = deletion ? null : getProperties();
        try {
            int flags = 0;
            byte[] body = null;
            String docType = null;
            if (deletion)
                flags = Constants.C4RevisionFlags.kRevDeleted;
            // TODO: Blob
            if (propertiesToSave != null && propertiesToSave.size() > 0) {
                // Encode properties to Fleece data:
                body = encode();
                if (body == null)
                    return null;
                docType = getString("type");
            }
            List<String> revIDs = new ArrayList<>();
            if (c4doc.getRevID() != null)
                revIDs.add(c4doc.getRevID());
            String[] history = revIDs.toArray(new String[revIDs.size()]);

            // Save to database:
            com.couchbase.litecore.Document newDoc = db.internal().put(c4doc.getDocID(), body, null, false, false, history, flags, true, 0);
            return newDoc;
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    private byte[] encode() throws CouchbaseLiteException {
        FLEncoder encoder = new FLEncoder();
        try {
            encoder.beginDict(getProperties().size());
            Iterator<String> keys = getProperties().keySet().iterator();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = getProperties().get(key);
                // TODO DB005: Blob
                encoder.writeKey(key);
                encoder.writeValue(value);

            }
            encoder.endDict();
            byte[] body;
            try {
                body = encoder.finish();
            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertException(e);
            }
            return body;
        } finally {
            encoder.free();
        }
    }

    private void resetChanges() {
        this.properties = null; // not calling setProperties(null)
        setHasChanges(false);
    }

}
