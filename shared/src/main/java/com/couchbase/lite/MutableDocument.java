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

import com.couchbase.litecore.C4Constants;

import java.util.Date;
import java.util.Map;

import static com.couchbase.lite.internal.Misc.CreateUUID;

/**
 * A Couchbase Lite Document. A document has key/value properties like a Map.
 */
public final class MutableDocument extends Document implements MutableDictionaryInterface, C4Constants {
    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Creates a new Document object with a new random UUID. The created document will be
     * saved into a database when you call the Database's save(Document) method with the document
     * object given.
     */
    public MutableDocument() {
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
    public MutableDocument(String id) {
        super(null, id != null ? id : CreateUUID(), null);
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
    public MutableDocument(Map<String, Object> dictionary) {
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
    public MutableDocument(String id, Map<String, Object> dictionary) {
        this(id);
        set(dictionary);
    }

    MutableDocument(Document doc, Dictionary dict) {
        super(doc.getDatabase(), doc.getId(), doc.getC4doc());
        if (dict != null)
            _dict = dict.toMutable();
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
    public MutableDocument set(Map<String, Object> dictionary) {
        ((MutableDictionary) _dict).set(dictionary);
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
    public MutableDocument setValue(String key, Object value) {
        ((MutableDictionary) _dict).setValue(key, value);
        return this;
    }

    @Override
    public MutableDocument setString(String key, String value) {
        return setValue(key, value);
    }

    @Override
    public MutableDocument setNumber(String key, Number value) {
        return setValue(key, value);
    }

    @Override
    public MutableDocument setInt(String key, int value) {
        return setValue(key, value);
    }

    @Override
    public MutableDocument setLong(String key, long value) {
        return setValue(key, value);
    }

    @Override
    public MutableDocument setFloat(String key, float value) {
        return setValue(key, value);
    }

    @Override
    public MutableDocument setDouble(String key, double value) {
        return setValue(key, value);
    }

    @Override
    public MutableDocument setBoolean(String key, boolean value) {
        return setValue(key, value);
    }

    @Override
    public MutableDocument setBlob(String key, Blob value) {
        return setValue(key, value);
    }

    @Override
    public MutableDocument setDate(String key, Date value) {
        return setValue(key, value);
    }

    @Override
    public MutableDocument setArray(String key, Array value) {
        return setValue(key, value);
    }

    @Override
    public MutableDocument setDictionary(String key, Dictionary value) {
        return setValue(key, value);
    }

    /**
     * Removes the mapping for a key from this Dictionary
     *
     * @param key the key.
     * @return this Document instance
     */
    @Override
    public MutableDocument remove(String key) {
        ((MutableDictionary) _dict).remove(key);
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
    public MutableArray getArray(String key) {
        return ((MutableDictionary) _dict).getArray(key);
    }

    /**
     * Get a property's value as a Dictionary, which is a mapping object of an dictionary value.
     * Returns null if the property doesn't exists, or its value is not an dictionary.
     *
     * @param key the key.
     * @return the Dictionary object or null if the key doesn't exist.
     */
    @Override
    public MutableDictionary getDictionary(String key) {
        return ((MutableDictionary) _dict).getDictionary(key);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    @Override
    boolean isMutable() {
        return true;
    }

    @Override
    long generation() {
        return super.generation() + (isChanged() ? 1 : 0);
    }

    boolean isChanged() {
        return ((MutableDictionary) _dict).isChanged();
    }
}
