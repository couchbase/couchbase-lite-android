package com.couchbase.lite;

import com.couchbase.litecore.fleece.MCollection;
import com.couchbase.litecore.fleece.MValue;

import java.util.Date;
import java.util.Map;

/**
 * Dictionary provides access to dictionary data.
 */
public class Dictionary extends ReadOnlyDictionary implements DictionaryInterface {
    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Initialize a new empty Dictionary object.
     */
    public Dictionary() {
        super();
    }

    /**
     * Initializes a new CBLDictionary object with dictionary content. Allowed value types are List,
     * Date, Map, Number, null, String, Array, Blob, and Dictionary. The List and Map must contain
     * only the above types.
     *
     * @param dictionary the dictionary object.
     */
    public Dictionary(Map<String, Object> dictionary) {
        super();
        set(dictionary);
    }

    Dictionary(MValue mv, MCollection parent) {
        super(mv, parent);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Set a dictionary as a content. Allowed value types are List, Date, Map, Number, null, String,
     * Array, Blob, and Dictionary. The List and Map must contain only the above types.
     * Setting the new dictionary content will replace the current data including the existing Array
     * and Dictionary objects.
     *
     * @param dictionary the dictionary object.
     * @return this Dictionary instance
     */
    @Override
    public Dictionary set(Map<String, Object> dictionary) {
        _dict.clear();
        for (Map.Entry<String, Object> entry : dictionary.entrySet())
            _dict.set(entry.getKey(), new MValue(CBLFleece.toCBLObject(entry.getValue())));
        return this;
    }

    /**
     * Set an object value by key. Allowed value types are List, Date, Map, Number, null, String,
     * Array, Blob, and Dictionary. The List and Map must contain only the above types.
     * An Date object will be converted to an ISO-8601 format string.
     *
     * @param key   the key.
     * @param value the object value.
     * @return this Dictionary instance
     */
    @Override
    public Dictionary setObject(String key, Object value) {
        MValue oldValue = _dict.get(key);
        if (value != null) {
            value = CBLFleece.toCBLObject(value);
            if (CBLFleece.valueWouldChange(value, oldValue, _dict))
                _dict.set(key, new MValue(value));
        } else {
            if (!oldValue.isEmpty())
                _dict.remove(key);
        }
        return this;
    }

    @Override
    public Dictionary setString(String key, String value) {
        return setObject(key, value);
    }

    @Override
    public Dictionary setNumber(String key, Number value) {
        return setObject(key, value);
    }

    @Override
    public Dictionary setInt(String key, int value) {
        return setObject(key, value);
    }

    @Override
    public Dictionary setLong(String key, long value) {
        return setObject(key, value);
    }

    @Override
    public Dictionary setFloat(String key, float value) {
        return setObject(key, value);
    }

    @Override
    public Dictionary setDouble(String key, double value) {
        return setObject(key, value);
    }

    @Override
    public Dictionary setBoolean(String key, boolean value) {
        return setObject(key, value);
    }

    @Override
    public Dictionary setBlob(String key, Blob value) {
        return setObject(key, value);
    }

    @Override
    public Dictionary setDate(String key, Date value) {
        return setObject(key, value);
    }

    @Override
    public Dictionary setArray(String key, Array value) {
        return setObject(key, value);
    }

    @Override
    public Dictionary setDictionary(String key, Dictionary value) {
        return setObject(key, value);
    }

    /**
     * Removes the mapping for a key from this Dictionary
     *
     * @param key the key.
     * @return this Dictionary instance
     */
    @Override
    public Dictionary remove(String key) {
        _dict.remove(key);
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
        Object obj = _get(_dict, key).asNative(_dict);
        return obj instanceof Array ? (Array) obj : null;
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
        Object obj = _get(_dict, key).asNative(_dict);
        return obj instanceof Dictionary ? (Dictionary) obj : null;
    }


    protected boolean isChanged() {
        return _dict.isMutated();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }
}
