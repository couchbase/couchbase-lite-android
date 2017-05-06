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

import com.couchbase.lite.internal.bridge.LiteCoreBridge;
import com.couchbase.litecore.Constants;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.FLValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.couchbase.litecore.Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.Constants.C4RevisionFlags.kRevHasAttachments;
import static com.couchbase.litecore.Constants.LiteCoreError.kC4ErrorConflict;

/**
 * A Couchbase Lite document. A document has key/value properties like a Dictionary;
 * their API is defined by the superclass Properties.
 * To learn how to work with properties, see {@code Properties} documentation.
 */
public final class Document extends Properties {

    private Database db;
    private String id;
    private ConflictResolver conflictResolver;
    private com.couchbase.litecore.Document c4doc;

    Document(Database db, String docID, boolean mustExist) throws CouchbaseLiteException {
        super(db.getSharedKeys());
        this.db = db;
        this.id = docID;

        // TODO: Reconsider if loading litecore.Document in constructor is good.
        //       Should we pass litecoreDocument to Document constructor?
        //       Current impl: DB.getDoc() -> Doc constructor -> db.read().....
        load(mustExist);
    }

    //---------------------------------------------
    // public API methods
    //---------------------------------------------

    /**
     * Return the document's owning database.
     *
     * @return the document's owning database.
     */
    public Database getDatabase() {
        return db;
    }

    /**
     * Returns the ConflictResolver added to this Document with {@code setConflictResolver()}.
     *
     * @return the ConflictResolver
     */
    public ConflictResolver getConflictResolver() {
        return conflictResolver;
    }

    /**
     * Set the conflict resolver, if any, specific to this document.
     * If nil, the database's conflict resolver will be used.
     *
     * @param conflictResolver the conflict resolver
     */
    public void setConflictResolver(ConflictResolver conflictResolver) {
        this.conflictResolver = conflictResolver;
    }

    /**
     * return the document's ID.
     *
     * @return the document's ID
     */
    public String getID() {
        return id;
    }

    /**
     * Return the sequence number of the document in the database.
     * This indicates how recently the document has been changed: every time any document is updated,
     * the database assigns it the next sequential sequence number. Thus, if a document's `sequence`
     * property changes that means it's been changed (on-disk); and if one document's `sequence`
     * is greater than another's, that means it was changed more recently.
     *
     * @return the sequence number of the document in the database.
     */
    public long getSequence() {
        return c4doc.getSequence();
    }

    /**
     * Return whether the document is deleted
     *
     * @return true if deleted, false otherwise
     */
    public boolean isDeleted() {
        return c4doc.deleted();
    }

    /**
     * Return whether the document exists in the database.
     *
     * @return true if exists, false otherwise.
     */
    public boolean exists() {
        return c4doc.exists();
    }

    /**
     * Saves property changes back to the database.
     * If the document in the database has been updated since it was read by this CBLDocument, a
     * conflict occurs, which will be resolved by invoking the conflict handler. This can happen if
     * multiple application threads are writing to the database, or a pull replication is copying
     * changes from a server.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void save() throws CouchbaseLiteException {
        save(effectiveConflictResolver(), false);
    }

    /**
     * Deletes this document. All properties are removed, and subsequent calls to
     * {@code getDocument(String)} will return nil.
     * Deletion adds a special "tombstone" revision to the database, as bookkeeping so that the
     * change can be replicated to other databases. Thus, it does not free up all of the disk space
     * occupied by the document.
     * To delete a document entirely (but without the ability to replicate this), use {@code purge()}.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    public void delete() throws CouchbaseLiteException {
        save(effectiveConflictResolver(), true);
    }

    /**
     * Purges this document from the database.
     * This is more drastic than deletion: it removes all traces of the document.
     * The purge will NOT be replicated to other databases.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
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

    /**
     * Reverts unsaved changes made to the document's properties.
     */
    public void revert() {
        resetChanges();
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

    @Override
    Blob blobWithProperties(Map<String, Object> dict) {
        return new Blob(db, dict);
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
                // In case of (LiteCoreDomain, kC4ErrorDeleted) -> body = null if we directly bind to C4 APIs.
                body = c4doc.getSelectedBody();
            } catch (LiteCoreException e) {
                if (e.domain != LiteCoreDomain || e.code != Constants.LiteCoreError.kC4ErrorDeleted)
                    throw LiteCoreBridge.convertException(e);
            }
            if (body != null && body.length > 0) {
                FLDict root = FLValue.fromData(body).asFLDict();
                setRoot(root);
            }
        }
        useNewRoot();
    }

    private void save(ConflictResolver resolver, boolean deletion) throws CouchbaseLiteException {
        // No-op case of unchanged document:
        if (!hasChanges && !deletion && exists())
            return;

        com.couchbase.litecore.Document newDoc = null;

        // Begin a db transaction:
        boolean commit = false;
        db.beginTransaction();
        try {
            // Attempt to save. (On conflict, this will succeed but newDoc will be null.)
            try {
                newDoc = save(deletion);
            } catch (LiteCoreException e) {
                if (e.domain != LiteCoreDomain || e.code != kC4ErrorConflict)
                    throw LiteCoreBridge.convertException(e);
            }
            if (newDoc == null) {
                // There's been a conflict; first merge with the new saved revision:
                merge(resolver, deletion);

                // The merge might have turned the save into a no-op:
                if (!hasChanges)
                    return;

                // Now save the merged properties:
                try {
                    newDoc = save(deletion);
                } catch (LiteCoreException e) {
                    throw LiteCoreBridge.convertException(e);
                }
            }

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
    }

    private void merge(ConflictResolver resolver, boolean deletion) throws CouchbaseLiteException {
        com.couchbase.litecore.Document currentDoc = db.read(id, true);

        Map<String, Object> current = null;
        try {
            // TODO: better to return C4String instead of byte[]
            //       This part of code is not efficient.
            byte[] currentData = currentDoc.getSelectedBody();
            if (currentData.length > 0) {
                FLValue currentRoot = FLValue.fromData(currentData);
                SharedKeys currentKeys = new SharedKeys(this.sharedKeys, currentRoot.asFLDict());
                current = (Map<String, Object>) SharedKeys.valueToObject(currentRoot, currentKeys);
                Log.e(Log.DATABASE, "merge() current -> " + current);
                // TODO: Need currentRoot.free()?
            }
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }

        Map<String, Object> resolved = null;
        if (deletion) {
            // Deletion always losses a conflit
            resolved = current;
        } else if (resolver != null) {
            // Call the custom conflict resolver
            resolved = resolver.resolve(
                    getProperties() != null ? getProperties() : new HashMap<String, Object>(),
                    current != null ? current : new HashMap<String, Object>(),
                    getSavedProperties());
            if (resolved == null) {
                // Resolver gave up:
                currentDoc.free();
                throw new CouchbaseLiteException(LiteCoreDomain, kC4ErrorConflict);
            }
        } else {
            // Default resolution algorithm is "most active wins", i.e. higher generation number.
            // TODO: Once conflict resolvers can access the document generation, move this logic
            //       into a default ConflictResolver.
            long myGeneraton = generation() + 1;
            long theirGeneration = generationFromRevID(currentDoc.getRevID());
            if (myGeneraton >= theirGeneration)
                resolved = getProperties();
            else
                resolved = current;
        }

        // Now update my state to the current C4Document and the merged/resolved properties:
        setC4Doc(currentDoc);
        this.setProperties(resolved);
        // NOTE: AbstractMap.equals() calls all values in the map. Nested data's equality check is
        // depends on its implementation.
        if (resolved.equals(current))
            this.setHasChanges(false); // Document is now identical to current revision
    }

    // Lower-level save method. On conflict, returns YES but sets *outDoc to NULL. */
    private com.couchbase.litecore.Document save(boolean deletion) throws LiteCoreException {
        Map<String, Object> propertiesToSave = deletion ? null : getProperties();
        int flags = 0;
        byte[] body = null;
        if (deletion)
            flags = Constants.C4RevisionFlags.kRevDeleted;
        if (propertiesToSave != null && dictContainsBlob(propertiesToSave))
            flags |= kRevHasAttachments;
        if (propertiesToSave != null && propertiesToSave.size() > 0) {
            // Encode properties to Fleece data:
            FLEncoder encoder = db.internal().createFleeceEncoder();
            body = encode(encoder);
            encoder.free();
            if (body == null)
                return null;
        }
        List<String> revIDs = new ArrayList<>();
        if (c4doc.getRevID() != null)
            revIDs.add(c4doc.getRevID());
        String[] history = revIDs.toArray(new String[revIDs.size()]);

        // Save to database:
        com.couchbase.litecore.Document newDoc = db.internal().put(c4doc.getDocID(), body, false, false, history, flags, true, 0);
        return newDoc;
    }

    private void resetChanges() {
        this.properties = null; // not calling setProperties(null)
        setHasChanges(false);
    }

    private void store(Blob blob) {
        blob.installInDatabase(db);
    }

    private Blob blob(Map<String, Object> properties) {
        return new Blob(db, properties);
    }

    private static boolean dictContainsBlob(Map<String, Object> dict) {
        if (dict == null)
            return false;

        Object obj = dict.get("_cbltype");
        if (obj != null && obj instanceof String && ((String) obj).equals("blob"))
            return true;
        boolean containsBlob = false;
        for (Map.Entry<String, Object> entry : dict.entrySet()) {
            containsBlob = objectContainsBlob(entry.getValue());
            if (containsBlob)
                break;
        }
        return containsBlob;
    }

    private static boolean arrayContainsBlob(List<Object> array) {
        if (array == null)
            return false;

        for (Object obj : array) {
            if (objectContainsBlob(obj))
                return true;
        }
        return false;
    }

    private static boolean objectContainsBlob(Object obj) {
        if (obj == null)
            return false;
        else if (obj instanceof Blob)
            return true;
        else if (obj instanceof Map)
            return dictContainsBlob((Map<String, Object>) obj);
        else if (obj instanceof List)
            return arrayContainsBlob((List<Object>) obj);
        else
            return false;
    }

    // TODO: Instead of byte[], should we use FLSliceResult? Less memory transfer through JNI.
    private byte[] encode(FLEncoder encoder) throws CouchbaseLiteException {
        try {
            encoder.beginDict(getProperties().size());
            Iterator<String> keys = getProperties().keySet().iterator();
            while (keys.hasNext()) {
                String key = keys.next();
                encoder.writeKey(key);
                Object value = getProperties().get(key);
                if (value instanceof Blob)
                    store((Blob) value);
                writeValue(encoder, value);
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

    private boolean writeValue(FLEncoder encoder, Object value) {
        if (value == null)
            return encoder.writeNull();
        else if (value instanceof Boolean)
            return encoder.writeBool((Boolean) value);
        else if (value instanceof Number) {
            if (value instanceof Integer || value instanceof Long)
                return encoder.writeInt(((Number) value).longValue());
            else if (value instanceof Double)
                return encoder.writeDouble(((Double) value).doubleValue());
            else
                return encoder.writeFloat(((Float) value).floatValue());
        } else if (value instanceof String)
            return encoder.writeString((String) value);
        else if (value instanceof byte[])
            return encoder.writeData((byte[]) value);
        else if (value instanceof List)
            return encoder.write((List) value);
        else if (value instanceof Map)
            return write(encoder, (Map) value);
        else if (value instanceof Blob)
            return writeValueForObject(encoder, value);
        return false;
    }

    private boolean write(FLEncoder encoder, Map map) {
        encoder.beginDict(map.size());
        Iterator keys = map.keySet().iterator();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            Object value = map.get(key);
            if (value instanceof Blob)
                store((Blob) value);
            encoder.writeKey(key);
            writeValueForObject(encoder, value);
        }
        return encoder.endDict();
    }

    private boolean writeValueForObject(FLEncoder encoder, Object value) {
        if (value instanceof Blob)
            value = ((Blob) value).jsonRepresentation();
        return writeValue(encoder, value);
    }

    private ConflictResolver effectiveConflictResolver() {
        return conflictResolver != null ? conflictResolver : db.getConflictResolver();
    }

    private long generation() {
        return generationFromRevID(c4doc.getRevID());
    }

    /**
     * TODO: This code is from v1.x. Better to replace with c4rev_getGeneration().
     */
    private static long generationFromRevID(String revID) {
        long generation = 0;
        long length = Math.min(revID == null ? 0 : revID.length(), 9);
        for (int i = 0; i < length; ++i) {
            char c = revID.charAt(i);
            if (Character.isDigit(c))
                generation = 10 * generation + Character.getNumericValue(c);
            else if (c == '-')
                return generation;
            else
                break;
        }
        return 0;
    }
}
