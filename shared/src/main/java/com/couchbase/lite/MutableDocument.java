//
// MutableDocument.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import com.couchbase.litecore.C4Constants;
import com.couchbase.litecore.C4Document;

import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
        super(null, id != null ? id : createUUID(), (C4Document)null);
    }

    /**
     * Initializes a new CBLDocument object with a new random UUID and the dictionary as the content.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * The List and Map must contain only the above types.
     * The created document will be
     * saved into a database when you call the Database's save(Document) method with the document
     * object given.
     *
     * @param data the Map object
     */
    public MutableDocument(Map<String, Object> data) {
        this((String) null);
        setData(data);
    }

    /**
     * Initializes a new Document object with a given ID and the dictionary as the content.
     * If a null ID value is given, the document will be created with a new random UUID.
     * Allowed value types are List, Date, Map, Number, null, String, Array, Blob, and Dictionary.
     * The List and Map must contain only the above types.
     * The created document will be saved into a database when you call
     * the Database's save(Document) method with the document object given.
     *
     * @param id   the document ID.
     * @param data the Map object
     */
    public MutableDocument(String id, Map<String, Object> data) {
        this(id);
        setData(data);
    }

    MutableDocument(Document doc, Dictionary dict) {
        super(doc.getDatabase(), doc.getId(), doc.getC4doc());
        if (dict != null)
            _dict = dict.toMutable();
    }

    //---------------------------------------------
    // public API methods
    //---------------------------------------------

    /**
     * Returns the copy of this MutableDocument object.
     *
     * @return The MutableDocument object
     */
    @Override
    public MutableDocument toMutable() {
        return new MutableDocument(this, _dict);
    }

    //---------------------------------------------
    // DictionaryInterface implementation
    //---------------------------------------------

    /**
     * Set a dictionary as a content. Allowed value types are List, Date, Map, Number, null, String,
     * Array, Blob, and Dictionary. The List and Map must contain only the above types.
     * Setting the new dictionary content will replace the current data including the existing Array
     * and Dictionary objects.
     *
     * @param data the dictionary object.
     * @return this Document instance
     */
    @Override
    public MutableDocument setData(Map<String, Object> data) {
        ((MutableDictionary) _dict).setData(data);
        return this;
    }

    /**
     * Set an object value by key. Allowed value types are List, Date, Map, Number, null, String,
     * Array, Blob, and Dictionary. The List and Map must contain only the above types.
     * An Date object will be converted to an ISO-8601 format string.
     *
     * @param key   the key.
     * @param value the Object value.
     * @return this Document instance
     */
    @Override
    public MutableDocument setValue(String key, Object value) {
        ((MutableDictionary) _dict).setValue(key, value);
        return this;
    }

    /**
     * Set a String value for the given key
     *
     * @param key the key.
     * @param key the String value.
     * @return this MutableDocument instance
     */
    @Override
    public MutableDocument setString(String key, String value) {
        return setValue(key, value);
    }

    /**
     * Set a Number value for the given key
     *
     * @param key the key.
     * @param key the Number value.
     * @return this MutableDocument instance
     */
    @Override
    public MutableDocument setNumber(String key, Number value) {
        return setValue(key, value);
    }

    /**
     * Set a integer value for the given key
     *
     * @param key the key.
     * @param key the integer value.
     * @return this MutableDocument instance
     */
    @Override
    public MutableDocument setInt(String key, int value) {
        return setValue(key, value);
    }

    /**
     * Set a long value for the given key
     *
     * @param key the key.
     * @param key the long value.
     * @return this MutableDocument instance
     */
    @Override
    public MutableDocument setLong(String key, long value) {
        return setValue(key, value);
    }

    /**
     * Set a float value for the given key
     *
     * @param key the key.
     * @param key the float value.
     * @return this MutableDocument instance
     */
    @Override
    public MutableDocument setFloat(String key, float value) {
        return setValue(key, value);
    }

    /**
     * Set a double value for the given key
     *
     * @param key the key.
     * @param key the double value.
     * @return this MutableDocument instance
     */
    @Override
    public MutableDocument setDouble(String key, double value) {
        return setValue(key, value);
    }

    /**
     * Set a boolean value for the given key
     *
     * @param key the key.
     * @param key the boolean value.
     * @return this MutableDocument instance
     */
    @Override
    public MutableDocument setBoolean(String key, boolean value) {
        return setValue(key, value);
    }

    /**
     * Set a Blob value for the given key
     *
     * @param key the key.
     * @param key the Blob value.
     * @return this MutableDocument instance
     */
    @Override
    public MutableDocument setBlob(String key, Blob value) {
        return setValue(key, value);
    }

    /**
     * Set a Date value for the given key
     *
     * @param key the key.
     * @param key the Date value.
     * @return this MutableDocument instance
     */
    @Override
    public MutableDocument setDate(String key, Date value) {
        return setValue(key, value);
    }

    /**
     * Set an Array value for the given key
     *
     * @param key the key.
     * @param key the Array value.
     * @return this MutableDocument instance
     */
    @Override
    public MutableDocument setArray(String key, Array value) {
        return setValue(key, value);
    }

    /**
     * Set a Dictionary value for the given key
     *
     * @param key the key.
     * @param key the Dictionary value.
     * @return this MutableDocument instance
     */
    @Override
    public MutableDocument setDictionary(String key, Dictionary value) {
        return setValue(key, value);
    }

    /**
     * Removes the mapping for a key from this Dictionary
     *
     * @param key the key.
     * @return this MutableDocument instance
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

    static String createUUID() {
        return UUID.randomUUID().toString().toLowerCase(Locale.ENGLISH);
    }
}
