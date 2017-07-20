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
import com.couchbase.litecore.C4Constants;
import com.couchbase.litecore.C4Document;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.FLSliceResult;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static com.couchbase.lite.internal.Misc.CreateUUID;
import static com.couchbase.litecore.C4Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.C4Constants.C4RevisionFlags.kRevHasAttachments;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorConflict;

/**
 * A Couchbase Lite Document. A document has key/value properties like a Map.
 */
public final class Document extends ReadOnlyDocument implements DictionaryInterface, C4Constants {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
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
        this(null, id != null ? id : CreateUUID(), null, null);
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

    Document(Database database, String documentID, CBLC4Doc c4doc, CBLFLDict data) {
        super(database, documentID, c4doc, data);
        this.dictionary = new Dictionary();
    }

    Document(Database database, String documentID, boolean mustExist) {
        super(database, documentID, mustExist);
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

    @Override
    public List<String> getKeys() {
        return dictionary.getKeys();
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
     * Gets a property's value as a String.
     * Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param key the key
     * @return the String or null.
     */
    @Override
    public String getString(String key) {
        return dictionary.getString(key);
    }

    /**
     * Gets a property's value as a Number.
     * Returns null if the value doesn't exist, or its value is not a Number.
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
     * This can be less expensive than getObject(String),
     * because it does not have to allocate an Object for the property value.
     *
     * @param key the key
     * @return the boolean value representing whether a property exists or not.
     */
    @Override
    public boolean contains(String key) {
        return dictionary.contains(key);
    }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------
    // Sets c4doc and updates my root dictionary
    @Override
    protected void setC4Doc(CBLC4Doc c4doc) {
        super.setC4Doc(c4doc);
        // Update delegate dictionary:
        this.dictionary = new Dictionary(getData());
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    void save() throws CouchbaseLiteException {
        save(effectiveConflictResolver(), false);
    }

    void delete() throws CouchbaseLiteException {
        save(effectiveConflictResolver(), true);
    }

    void purge() throws CouchbaseLiteException {
        if (!exists())
            throw new CouchbaseLiteException(Status.CBLErrorDomain, Status.NotFound);

        boolean commit = false;
        getDatabase().beginTransaction();
        try {
            // revID: null, all revisions are purged.
            if (getC4doc().getRawDoc().purgeRevision(null) >= 0) {
                getC4doc().getRawDoc().save(0);
                commit = true;
            }
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        } finally {
            getDatabase().endTransaction(commit);
        }

        // reset
        setC4Doc(null);
    }

    @Override
    boolean isEmpty() {
        return dictionary.isEmpty();
    }

    @Override
    long generation() {
        return super.generation() + (isChanged() ? 1 : 0);
    }

    // #pragma mark - FLEECE ENCODING

    @Override
    byte[] encode() {
        FLEncoder encoder = getDatabase().getC4Database().createFleeceEncoder();
        try {
            dictionary.fleeceEncode(encoder, getDatabase());
            return encoder.finish();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertRuntimeException(e);
        } finally {
            encoder.free();
        }
    }

    FLSliceResult encode2() {
        FLEncoder encoder = getDatabase().getC4Database().createFleeceEncoder();
        try {
            dictionary.fleeceEncode(encoder, getDatabase());
            return encoder.finish2();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertRuntimeException(e);
        } finally {
            encoder.free();
        }
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    private void save(ConflictResolver resolver, boolean deletion) throws CouchbaseLiteException {
        if (deletion && !exists())
            throw new CouchbaseLiteException(Status.CBLErrorDomain, Status.NotFound);

        C4Document newDoc = null;

        // Begin a database transaction:
        boolean commit = false;
        getDatabase().beginTransaction();
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
                getDatabase().endTransaction(commit);
            } catch (CouchbaseLiteException e) {
                // NOTE: newDoc could be null if initial save() throws Exception.
                if (newDoc != null)
                    newDoc.free();
                throw e;
            }
        }

        // Update my state and post a notification:
        setC4Doc(new CBLC4Doc(newDoc));
    }

    // "Pulls" from the database, merging the latest revision into the in-memory properties,
    //  without saving. */
    private void merge(ConflictResolver resolver, boolean deletion) throws CouchbaseLiteException {
        if (resolver == null)
            throw new CouchbaseLiteException(LiteCoreDomain, kC4ErrorConflict);

        ReadOnlyDocument current = new ReadOnlyDocument(getDatabase(), getId(), true);
        CBLC4Doc curC4doc = current.getC4doc();

        // Resolve conflict:
        ReadOnlyDocument resolved;
        if (deletion) {
            // Deletion always loses a conflict:
            resolved = current;
        } else {
            // Call the custom conflict resolver
            ReadOnlyDocument base = new ReadOnlyDocument(
                    getDatabase(), getId(), super.getC4doc(), super.getData());
            Conflict conflict = new Conflict(this, current, base);
            resolved = resolver.resolve(conflict);
            if (resolved == null) {
                throw new CouchbaseLiteException(LiteCoreDomain, kC4ErrorConflict);
            }
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
    private C4Document save(boolean deletion) throws LiteCoreException {

        // history
        List<String> revIDs = new ArrayList<>();
        if (getC4doc() != null && getC4doc().getRevID() != null)
            revIDs.add(getC4doc().getRevID());
        String[] history = revIDs.toArray(new String[revIDs.size()]);

        FLSliceResult body = null;
        int revFlags = 0;
        if (deletion)
            revFlags = C4RevisionFlags.kRevDeleted;
        if (containsBlob(this))
            revFlags |= kRevHasAttachments;
        if (!deletion && !isEmpty()) {
            // Encode properties to Fleece data:
            body = encode2();
            if (body == null)
                return null;
        }

        // Save to database:
        C4Document newDoc = null;
        C4Document c4Doc = getC4doc() != null ? getC4doc().getRawDoc() : null;
        if (c4Doc != null)
            newDoc = c4Doc.update(body, revFlags);
        else
            newDoc = getDatabase().getC4Database().create(getId(), body, revFlags);
        if (body != null)
            body.free();
        return newDoc;
    }

    private boolean isChanged() {
        return dictionary.isChanged();
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
        for (String key : dict.getKeys()) {
            if (containsBlob = objectContainsBlob(dict.getObject(key)))
                break;
        }
        return containsBlob;
    }

    private static boolean containsBlob(Document doc) {
        if (doc == null)
            return false;
        boolean containsBlob = false;
        for (String key : doc.getKeys()) {
            if (containsBlob = objectContainsBlob(doc.getObject(key)))
                break;
        }
        return containsBlob;
    }
}
