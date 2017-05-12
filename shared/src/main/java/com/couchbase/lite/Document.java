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
import java.util.List;
import java.util.Map;

import static com.couchbase.lite.internal.Misc.CreateUUID;
import static com.couchbase.litecore.Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.Constants.C4RevisionFlags.kRevHasAttachments;
import static com.couchbase.litecore.Constants.LiteCoreError.kC4ErrorConflict;

/**
 * A Couchbase Lite document. A document has key/value properties like a DictionaryInterface;
 * their API is defined by the superclass Properties.
 * To learn how to work with properties, see {@code Properties} documentation.
 */
public final class Document extends ReadOnlyDocument implements DictionaryInterface {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Database database;
    private Dictionary dict;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    public Document() {
        this((String) null);
    }

    public Document(String id) {
        super(id != null ? id : CreateUUID(), null, null);
        this.dict = new Dictionary();
    }

    public Document(Map<String, Object> dictionary) {
        this((String) null);
        set(dictionary);
    }

    public Document(String documentID, Map<String, Object> dictionary) {
        this(documentID);
        set(dictionary);
    }

    /*package*/ Document(Database database, String documentID, boolean mustExist) throws CouchbaseLiteException {
        super(documentID, null, null);
        this.database = database;
        loadDoc(mustExist);
    }

    //---------------------------------------------
    // public API methods
    //---------------------------------------------


    //---------------------------------------------
    // DictionaryInterface implementation
    //---------------------------------------------

    @Override
    public Document set(String key, Object value) {
        dict.set(key, value);
        return this;
    }

    @Override
    public Document remove(String key) {
        dict.remove(key);
        return this;
    }

    @Override
    public Document set(Map<String, Object> dictionary) {
        dict.set(dictionary);
        return this;
    }

    @Override
    public Array getArray(String key) {
        //TODO
        return null;
    }

    @Override
    public Dictionary getDictionary(String key) {
        //TODO
        return null;
    }


    /**
     * Return the document's owning database.
     *
     * @return the document's owning database.
     */
//    public Database getDatabase() {
//        return database;
//    }

    /**
     * Returns the ConflictResolver added to this Document with {@code setConflictResolver()}.
     *
     * @return the ConflictResolver
     */
//    public ConflictResolver getConflictResolver() {
//        return conflictResolver;
//    }

    /**
     * Set the conflict resolver, if any, specific to this document.
     * If nil, the database's conflict resolver will be used.
     *
     * @param conflictResolver the conflict resolver
     */
//    public void setConflictResolver(ConflictResolver conflictResolver) {
//        this.conflictResolver = conflictResolver;
//    }


    /**
     * Saves property changes back to the database.
     * If the document in the database has been updated since it was read by this CBLDocument, a
     * conflict occurs, which will be resolved by invoking the conflict handler. This can happen if
     * multiple application threads are writing to the database, or a pull replication is copying
     * changes from a server.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
//    public void save() throws CouchbaseLiteException {
//        save(effectiveConflictResolver(), false);
//    }


    /**
     * Reverts unsaved changes made to the document's properties.
     */
//    public void revert() {
//        resetChanges();
//    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------
    /*package*/  Database getDatabase() {
        return database;
    }

    /*package*/ void setDatabase(Database database) {
        this.database = database;
    }

    /*package*/ void save() throws CouchbaseLiteException {
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
    /*package*/  void delete() throws CouchbaseLiteException {
        save(effectiveConflictResolver(), true);
    }

    /**
     * Purges this document from the database.
     * This is more drastic than deletion: it removes all traces of the document.
     * The purge will NOT be replicated to other databases.
     *
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the operation.
     */
    /*package*/  void purge() throws CouchbaseLiteException {
        if (!exists())
            throw new CouchbaseLiteException(Status.CBLErrorDomain, Status.NotFound);

        boolean commit = false;
        database.beginTransaction();
        try {
            // revID: null, all revisions are purged.
            if (getC4doc().purgeRevision(null) >= 0) {
                getC4doc().save(0);
                commit = true;
            }
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        } finally {
            database.endTransaction(commit);
        }

        // reset
        setC4Doc(null);
    }


//    void setHasChanges(boolean hasChanges) {
//        if (this.hasChanges != hasChanges) {
//            super.setHasChanges(hasChanges);
//            getDatabase().unsavedDocument(this, hasChanges);
//        }
//    }
//
//
//    void markChanges() {
//        super.markChanges();
//        // TODO DB00x: send notification
//    }
//
//
//    Blob blobWithProperties(Map<String, Object> dict) {
//        return new Blob(database, dict);
//    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    // // (Re)loads the document from the db, updating _c4doc and other state.
    private void loadDoc(boolean mustExist) throws CouchbaseLiteException {
        com.couchbase.litecore.Document doc = readC4Doc(mustExist);
        if (doc == null)
            return;
        // NOTE: c4doc should not be null.
        setC4Doc(doc);

    }

    // Reads the document from the db into a new C4Document and returns it, w/o affecting my state.
    private com.couchbase.litecore.Document readC4Doc(boolean mustExist) throws CouchbaseLiteException {
        try {
            return database.internal().getDocument(getId(), mustExist);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }


    // Sets c4doc and updates my root dict
    private void setC4Doc(com.couchbase.litecore.Document c4doc) throws CouchbaseLiteException {
        super.setC4doc(c4doc);
        if (c4doc != null) {
            FLDict root = null;
            byte[] body = null;
            try {
                if(!c4doc.deleted())
                    body = c4doc.getSelectedBody();
            } catch (LiteCoreException e) {
                // in case body is empty, deleted is thrown.
                if(e.code!=Constants.LiteCoreError.kC4ErrorDeleted)
                    throw LiteCoreBridge.convertException(e);
            }
            if (body != null && body.length > 0)
                root = FLValue.fromData(body).asFLDict();
            setData(new CBLFLDict(root, c4doc, database));
        } else
            setData(null);

        this.dict = new Dictionary(getData());
    }

    private void save(ConflictResolver resolver, boolean deletion) throws CouchbaseLiteException {
        // No-op case of unchanged document:
        if (!isChanged() && !deletion && exists())
            return;


        if (deletion && !exists())
            throw new CouchbaseLiteException(Status.CBLErrorDomain, Status.NotFound);

        com.couchbase.litecore.Document newDoc = null;

        // Begin a database transaction:
        boolean commit = false;
        database.beginTransaction();
        try {
            // Attempt to save. (On conflict, this will succeed but newDoc will be null.)
            try {
                newDoc = save(deletion);
            } catch (LiteCoreException e) {
                // conflict is not an error, here
                if (e.domain != LiteCoreDomain || e.code != kC4ErrorConflict)
                    throw LiteCoreBridge.convertException(e);
            }
            if (newDoc == null) {
                //TODO
                /*
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
                */
            }

            commit = true;
        } finally {
            // Save succeeded; now commit the transaction: Otherwise; abort the transaction
            try {
                database.endTransaction(commit);
            } catch (CouchbaseLiteException e) {
                newDoc.free();
                throw e;
            }
        }

        // Update my state and post a notification:
        setC4Doc(newDoc);
    }

//
//    private void merge(ConflictResolver resolver, boolean deletion) throws CouchbaseLiteException {
//        com.couchbase.litecore.Document currentDoc = database.read(id, true);
//
//        Map<String, Object> current = null;
//        try {
//            // TODO: better to return C4String instead of byte[]
//            //       This part of code is not efficient.
//            byte[] currentData = currentDoc.getSelectedBody();
//            if (currentData.length > 0) {
//                FLValue currentRoot = FLValue.fromData(currentData);
//                SharedKeys currentKeys = new SharedKeys(this.sharedKeys, currentRoot.asFLDict());
//                current = (Map<String, Object>) SharedKeys.valueToObject(currentRoot, currentKeys);
//                Log.e(Log.DATABASE, "merge() current -> " + current);
//                // TODO: Need currentRoot.free()?
//            }
//        } catch (LiteCoreException e) {
//            throw LiteCoreBridge.convertException(e);
//        }
//
//        Map<String, Object> resolved = null;
//        if (deletion) {
//            // Deletion always losses a conflit
//            resolved = current;
//        } else if (resolver != null) {
//            // Call the custom conflict resolver
//            resolved = resolver.resolve(
//                    getProperties() != null ? getProperties() : new HashMap<String, Object>(),
//                    current != null ? current : new HashMap<String, Object>(),
//                    getSavedProperties());
//            if (resolved == null) {
//                // Resolver gave up:
//                currentDoc.free();
//                throw new CouchbaseLiteException(LiteCoreDomain, kC4ErrorConflict);
//            }
//        } else {
//            // Default resolution algorithm is "most active wins", i.e. higher generation number.
//            // TODO: Once conflict resolvers can access the document generation, move this logic
//            //       into a default ConflictResolver.
//            long myGeneraton = generation() + 1;
//            long theirGeneration = generationFromRevID(currentDoc.getRevID());
//            if (myGeneraton >= theirGeneration)
//                resolved = getProperties();
//            else
//                resolved = current;
//        }
//
//        // Now update my state to the current C4Document and the merged/resolved properties:
//        setC4Doc(currentDoc);
//        this.setProperties(resolved);
//        // NOTE: AbstractMap.equals() calls all values in the map. Nested data's equality check is
//        // depends on its implementation.
//        if (resolved.equals(current))
//            this.setHasChanges(false); // Document is now identical to current revision
//    }
//

    // Lower-level save method. On conflict, returns YES but sets *outDoc to NULL. */
    private com.couchbase.litecore.Document save(boolean deletion) throws LiteCoreException {

        //String docID = this.id;
        byte[] body = null;
        List<String> revIDs = new ArrayList<>();
        if (getC4doc() != null && getC4doc().getRevID() != null)
            revIDs.add(getC4doc().getRevID());
        String[] history = revIDs.toArray(new String[revIDs.size()]);
        int flags = 0;
        if (deletion)
            flags = Constants.C4RevisionFlags.kRevDeleted;
        if (containsBlob(this))
            flags |= kRevHasAttachments;
        if (!deletion && !isEmpty()) {
            // Encode properties to Fleece data:
            FLEncoder encoder = database.internal().createFleeceEncoder();
            try {
                body = encode(encoder);
            } finally {
                encoder.free();
            }

            if (body == null)
                return null;
        }

        // Save to database:
        return database.internal().put(getId(), body, false, false, history, flags, true, 0);
    }


//
//    private void resetChanges() {
//        this.properties = null; // not calling setProperties(null)
//        setHasChanges(false);
//    }
//
//    private void store(Blob blob) {
//        blob.installInDatabase(database);
//    }
//
//    private Blob blob(Map<String, Object> properties) {
//        return new Blob(database, properties);
//    }
//
//    private static boolean dictContainsBlob(Map<String, Object> dict) {
//        if (dict == null)
//            return false;
//
//        Object obj = dict.get("_cbltype");
//        if (obj != null && obj instanceof String && ((String) obj).equals("blob"))
//            return true;
//        boolean containsBlob = false;
//        for (Map.Entry<String, Object> entry : dict.entrySet()) {
//            containsBlob = objectContainsBlob(entry.getValue());
//            if (containsBlob)
//                break;
//        }
//        return containsBlob;
//    }
//
//    private static boolean arrayContainsBlob(List<Object> array) {
//        if (array == null)
//            return false;
//
//        for (Object obj : array) {
//            if (objectContainsBlob(obj))
//                return true;
//        }
//        return false;
//    }
//
//    private static boolean objectContainsBlob(Object obj) {
//        if (obj == null)
//            return false;
//        else if (obj instanceof Blob)
//            return true;
//        else if (obj instanceof Map)
//            return dictContainsBlob((Map<String, Object>) obj);
//        else if (obj instanceof List)
//            return arrayContainsBlob((List<Object>) obj);
//        else
//            return false;
//    }
//

    // #pragma mark - FLEECE ENCODING

    // TODO: Instead of byte[], should we use FLSliceResult? Less memory transfer through JNI.
    private byte[] encode(FLEncoder encoder) throws CouchbaseLiteException {

        dict.fleeceEncode(encoder);

        try {
            return encoder.finish();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }

        /*
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
        */
    }


//

//
//    private ConflictResolver effectiveConflictResolver() {
//        return conflictResolver != null ? conflictResolver : database.getConflictResolver();
//    }
//
//    private long generation() {
//        return generationFromRevID(c4doc.getRevID());
//    }
//
//    /**
//     * TODO: This code is from v1.x. Better to replace with c4rev_getGeneration().
//     */
//    private static long generationFromRevID(String revID) {
//        long generation = 0;
//        long length = Math.min(revID == null ? 0 : revID.length(), 9);
//        for (int i = 0; i < length; ++i) {
//            char c = revID.charAt(i);
//            if (Character.isDigit(c))
//                generation = 10 * generation + Character.getNumericValue(c);
//            else if (c == '-')
//                return generation;
//            else
//                break;
//        }
//        return 0;
//    }

    private ConflictResolver effectiveConflictResolver() {
        return database != null ? database.getConflictResolver() : null;
    }

    private boolean isChanged() {
        return dict.isChanged();
    }

    private boolean isEmpty() {
        return dict.isEmpty();
    }

    private static boolean containsBlob(Document doc) {
        // TODO:
        return false;
    }
}
