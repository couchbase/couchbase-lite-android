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
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.couchbase.lite.internal.Misc.CreateUUID;
import static com.couchbase.litecore.Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.Constants.C4RevisionFlags.kRevHasAttachments;
import static com.couchbase.litecore.Constants.LiteCoreError.kC4ErrorConflict;

/**
 * A Couchbase Lite Document. A document has key/value properties like a Map.
 */
public final class Document extends ReadOnlyDocument implements DictionaryInterface {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Database database;
    private Dictionary dictionary;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Creates a new Document object with a new random UUID. The created document will be
     * saved into a database when you call the Database's save(Document) method with the document
     * object given.
     */
    public Document() {
        this((String) null);
    }

    /**
     * Creates a new Document object with the given ID. If a null ID value is given, the document
     * will be created with a new random UUID. The created document will be
     * saved into a database when you call the Database's save(Document) method with the document
     * object given.
     *
     * @param id the document ID.
     */
    public Document(String id) {
        super(id != null ? id : CreateUUID(), null, null);
        this.dictionary = new Dictionary();
    }

    /**
     * Initializes a new CBLDocument object with a new random UUID and the dictionary as the content.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * The List and Map must contain only the above types.
     * The created document will be
     * saved into a database when you call the Database's save(Document) method with the document
     * object given.
     *
     * @param dictionary the Map object
     */
    public Document(Map<String, Object> dictionary) {
        this((String) null);
        set(dictionary);
    }

    /**
     * Initializes a new Document object with a given ID and the dictionary as the content.
     * If a null ID value is given, the document will be created with a new random UUID.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * The List and Map must contain only the above types.
     * The created document will be saved into a database when you call
     * the Database's save(Document) method with the document object given.
     *
     * @param id         the document ID.
     * @param dictionary the Map object
     */
    public Document(String id, Map<String, Object> dictionary) {
        this(id);
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

    /**
     * Set a dictionary as a content. Allowed value types are List, Date, Map, Number, null, String,
     * Array, Blob, and Dictionary. The List and Map must contain only the above types.
     * Setting the new dictionary content will replace the current data including the existing Array
     * and Dictionary objects.
     *
     * @param dictionary the dictionary object.
     * @return this Document instance
     */
    @Override
    public Document set(Map<String, Object> dictionary) {
        this.dictionary.set(dictionary);
        return this;
    }

    /**
     * Set an object value by key. Allowed value types are List, Date, Map, Number, null, String,
     * Array, Blob, and Dictionary. The List and Map must contain only the above types.
     * An Date object will be converted to an ISO-8601 format string.
     *
     * @param key   the key.
     * @param value the object value.
     * @return this Document instance
     */
    @Override
    public Document set(String key, Object value) {
        dictionary.set(key, value);
        return this;
    }

    /**
     * Removes the mapping for a key from this Dictionary
     *
     * @param key the key.
     * @return this Document instance
     */
    @Override
    public Document remove(String key) {
        dictionary.remove(key);
        return this;
    }

    /**
     * Get a property's value as a Array, which is a mapping object of an array value.
     * Returns null if the property doesn't exists, or its value is not an array.
     *
     * @param key the key.
     * @return the Array object.
     */
    @Override
    public Array getArray(String key) {
        return dictionary.getArray(key);
    }

    /**
     * Get a property's value as a Dictionary, which is a mapping object of an dictionary value.
     * Returns null if the property doesn't exists, or its value is not an dictionary.
     *
     * @param key the key.
     * @return the Dictionary object or null if the key doesn't exist.
     */
    @Override
    public Dictionary getDictionary(String key) {
        return dictionary.getDictionary(key);
    }

    //---------------------------------------------
    // API - overridden from ReadOnlyDocument
    //---------------------------------------------

    /**
     * Gets a number of the entries in the dictionary.
     *
     * @return
     */
    @Override
    public int count() {
        return dictionary.count();
    }

    /**
     * Gets a property's value as an object. The object types are Blob, Array,
     * Dictionary, Number, or String based on the underlying data type; or nil if the
     * property value is null or the property doesn't exist.
     *
     * @param key the key.
     * @return the object value or nil.
     */
    @Override
    public Object getObject(String key) {
        return dictionary.getObject(key);
    }

    /**
     * Gets a property's value as a String. Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param key the key
     * @return the String or null.
     */
    @Override
    public String getString(String key) {
        return dictionary.getString(key);
    }

    /**
     * Gets a property's value as a Number. Returns null if the value doesn't exist, or its value is not a Number.
     *
     * @param key the key
     * @return the Number or nil.
     */
    @Override
    public Number getNumber(String key) {
        return dictionary.getNumber(key);
    }

    /**
     * Gets a property's value as an int.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the int value.
     */
    @Override
    public int getInt(String key) {
        return dictionary.getInt(key);
    }

    /**
     * Gets a property's value as an long.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the long value.
     */
    @Override
    public long getLong(String key) {
        return dictionary.getLong(key);
    }

    /**
     * Gets a property's value as an float.
     * Integers will be converted to float. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the float value.
     */
    @Override
    public float getFloat(String key) {
        return dictionary.getFloat(key);
    }

    /**
     * Gets a property's value as an double.
     * Integers will be converted to double. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the property doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the double value.
     */
    @Override
    public double getDouble(String key) {
        return dictionary.getDouble(key);
    }

    /**
     * Gets a property's value as a boolean. Returns true if the value exists, and is either `true`
     * or a nonzero number.
     *
     * @param key the key
     * @return the boolean value.
     */
    @Override
    public boolean getBoolean(String key) {
        return dictionary.getBoolean(key);
    }

    /**
     * Gets a property's value as a Blob.
     * Returns null if the value doesn't exist, or its value is not a Blob.
     *
     * @param key the key
     * @return the Blob value or null.
     */
    @Override
    public Blob getBlob(String key) {
        return dictionary.getBlob(key);
    }

    /**
     * Gets a property's value as a Date.
     * JSON does not directly support dates, so the actual property value must be a string, which is
     * then parsed according to the ISO-8601 date format (the default used in JSON.)
     * Returns null if the value doesn't exist, is not a string, or is not parseable as a date.
     * NOTE: This is not a generic date parser! It only recognizes the ISO-8601 format, with or
     * without milliseconds.
     *
     * @param key the key
     * @return the Date value or null.
     */
    @Override
    public Date getDate(String key) {
        return dictionary.getDate(key);
    }

    /**
     * Gets content of the current object as an Map. The values contained in the returned
     * Map object are all JSON based values.
     *
     * @return the Map object representing the content of the current object in the JSON format.
     */
    @Override
    public Map<String, Object> toMap() {
        return dictionary.toMap();
    }

    /**
     * Tests whether a property exists or not.
     * This can be less expensive than getObject(String), because it does not have to allocate an Object for the property value.
     *
     * @param key the key
     * @return the boolean value representing whether a property exists or not.
     */
    @Override
    public boolean contains(String key) {
        return dictionary.contains(key);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    // TODO: Once Iterable is implemented, change back to package level accessor
    @Override
    public List<String> allKeys() {
        return dictionary.allKeys();
    }

    /*package*/  Database getDatabase() {
        return database;
    }

    /*package*/ void setDatabase(Database database) {
        this.database = database;
    }

    /*package*/ void save() throws CouchbaseLiteException {
        save(effectiveConflictResolver(), false);
    }

    /*package*/  void delete() throws CouchbaseLiteException {
        save(effectiveConflictResolver(), true);
    }

    /*package*/  void purge() throws CouchbaseLiteException {
        if (!exists())
            throw new CouchbaseLiteException(Status.CBLErrorDomain, Status.NotFound);

        boolean commit = false;
        database.beginTransaction();
        try {
            // revID: null, all revisions are purged.
            if (getC4doc().getRawDoc().purgeRevision(null) >= 0) {
                getC4doc().getRawDoc().save(0);
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

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    // // (Re)loads the document from the db, updating _c4doc and other state.
    private void loadDoc(boolean mustExist) throws CouchbaseLiteException {
        com.couchbase.litecore.Document rawDoc = readC4Doc(mustExist);
        if (rawDoc == null)
            return;
        // NOTE: c4doc should not be null.
        setC4Doc(new CBLC4Doc(rawDoc));

    }

    // Reads the document from the db into a new C4Document and returns it, w/o affecting my state.
    private com.couchbase.litecore.Document readC4Doc(boolean mustExist) throws CouchbaseLiteException {
        try {
            return database.internal().getDocument(getId(), mustExist);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }


    // Sets c4doc and updates my root dictionary
    private void setC4Doc(CBLC4Doc c4doc) throws CouchbaseLiteException {
        super.setC4doc(c4doc);
        if (c4doc != null) {
            FLDict root = null;
            byte[] body = null;
            try {
                if (!c4doc.getRawDoc().deleted())
                    body = c4doc.getRawDoc().getSelectedBody();
            } catch (LiteCoreException e) {
                // in case body is empty, deleted is thrown.
                if (e.code != Constants.LiteCoreError.kC4ErrorDeleted)
                    throw LiteCoreBridge.convertException(e);
            }
            if (body != null && body.length > 0)
                root = FLValue.fromData(body).asFLDict();
            setData(new CBLFLDict(root, c4doc, database));
        } else
            setData(null);

        this.dictionary = new Dictionary(getData());
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

                // There's been a conflict; first merge with the new saved revision:
                merge(resolver, deletion);

                // The merge might have turned the save into a no-op:
                if (!isChanged())
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
            // Save succeeded; now commit the transaction: Otherwise; abort the transaction
            try {
                database.endTransaction(commit);
            } catch (CouchbaseLiteException e) {
                newDoc.free();
                throw e;
            }
        }

        // Update my state and post a notification:
        setC4Doc(new CBLC4Doc(newDoc));
    }

    private void merge(ConflictResolver resolver, boolean deletion) throws CouchbaseLiteException {
        com.couchbase.litecore.Document rawDoc = database.read(getId(), true);

        FLDict curRoot = null;
        try {
            byte[] currentData = rawDoc.getSelectedBody();
            if (currentData.length > 0)
                curRoot = FLValue.fromData(currentData).asFLDict();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }

        CBLC4Doc curC4doc = new CBLC4Doc(rawDoc);
        CBLFLDict curDict = new CBLFLDict(curRoot, curC4doc, database);
        ReadOnlyDocument current = new ReadOnlyDocument(getId(), curC4doc, curDict);

        // Resolve conflict:
        ReadOnlyDocument resolved;
        if (deletion) {
            // Deletion always loses a conflict:
            resolved = current;
        } else if (resolver != null) {
            // Call the custom conflict resolver
            ReadOnlyDocument base = new ReadOnlyDocument(getId(), super.getC4doc(), super.getData());
            Conflict conflict = new Conflict(this, current, base, Conflict.OperationType.kCBLDatabaseWrite);
            resolved = resolver.resolve(conflict);
            if (resolved == null) {
                rawDoc.free();
                throw new CouchbaseLiteException(LiteCoreDomain, kC4ErrorConflict);
            }
        } else {
            // Default resolution algorithm is "most active wins", i.e. higher generation number.
            // TODO: Once conflict resolvers can access the document generation, move this logic
            //       into a default ConflictResolver.
            long myGeneraton = generation() + 1;
            long theirGeneration = generationFromRevID(curC4doc.getRevID());
            if (myGeneraton >= theirGeneration)
                resolved = this;
            else
                resolved = current;
        }

        // Now update my state to the current C4Document and the merged/resolved properties:

        // NOTE: AbstractMap.equals() calls all values in the map. Nested data's equality check is
        // depends on its implementation.
        if (!resolved.equals(current)) {
            Map map = resolved.toMap();
            setC4Doc(curC4doc);
            set(map);
        } else {
            setC4Doc(curC4doc);
        }
    }


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

    // #pragma mark - FLEECE ENCODING

    private byte[] encode(FLEncoder encoder) throws CouchbaseLiteException {
        dictionary.fleeceEncode(encoder, database);
        try {
            return encoder.finish();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    private ConflictResolver effectiveConflictResolver() {
        return database != null ? database.getConflictResolver() : null;
    }

    private boolean isChanged() {
        return dictionary.isChanged();
    }

    private boolean isEmpty() {
        return dictionary.isEmpty();
    }


    // The next four functions search recursively for a property "_cbltype":"blob".

    private static boolean objectContainsBlob(Object obj) {
        if (obj == null)
            return false;
        else if (obj instanceof Blob)
            return true;
        else if (obj instanceof Dictionary)
            return dictionaryContainsBlob((Dictionary) obj);
        else if (obj instanceof Array)
            return arrayContainsBlob((Array) obj);
        else
            return false;
    }

    private static boolean arrayContainsBlob(Array array) {
        if (array == null) return false;
        for (int i = 0; i < array.count(); i++) {
            if (objectContainsBlob(array.getObject(i)))
                return true;
        }
        return false;
    }

    private static boolean dictionaryContainsBlob(Dictionary dict) {
        if (dict == null)
            return false;
        boolean containsBlob = false;
        for (String key : dict.allKeys()) {
            if (containsBlob = objectContainsBlob(dict.getObject(key)))
                break;
        }
        return containsBlob;
    }

    private static boolean containsBlob(Document doc) {
        if (doc == null)
            return false;
        boolean containsBlob = false;
        for (String key : doc.allKeys()) {
            if (containsBlob = objectContainsBlob(doc.getObject(key)))
                break;
        }
        return containsBlob;
    }
}
