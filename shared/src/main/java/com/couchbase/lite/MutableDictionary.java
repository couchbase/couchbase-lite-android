package com.couchbase.lite;

import com.couchbase.litecore.fleece.MCollection;
import com.couchbase.litecore.fleece.MDict;
import com.couchbase.litecore.fleece.MValue;

import java.util.Date;
import java.util.Map;

/**
 * Dictionary provides access to dictionary data.
 */
public final class MutableDictionary extends Dictionary implements MutableDictionaryInterface {
    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Initialize a new empty Dictionary object.
     */
    public MutableDictionary() {
        super();
    }

    /**
     * Initializes a new CBLDictionary object with dictionary content. Allowed value types are List,
     * Date, Map, Number, null, String, Array, Blob, and Dictionary. The List and Map must contain
     * only the above types.
     *
     * @param data the dictionary object.
     */
    public MutableDictionary(Map<String, Object> data) {
        super();
        setData(data);
    }

    // to create copy of dictionary
    MutableDictionary(MDict mDict, boolean isMutable) {
        super(mDict, isMutable);
    }

    MutableDictionary(MValue mv, MCollection parent) {
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
     * @param data the dictionary object.
     * @return this Dictionary instance
     */
    @Override
    public MutableDictionary setData(Map<String, Object> data) {
        _dict.clear();
        for (Map.Entry<String, Object> entry : data.entrySet())
            _dict.set(entry.getKey(), new MValue(Fleece.toCBLObject(entry.getValue())));
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
    public MutableDictionary setValue(String key, Object value) {
        MValue oldValue = _dict.get(key);
        value = Fleece.toCBLObject(value);
        if (Fleece.valueWouldChange(value, oldValue, _dict))
            _dict.set(key, new MValue(value));
        return this;
    }

    @Override
    public MutableDictionary setString(String key, String value) {
        return setValue(key, value);
    }

    @Override
    public MutableDictionary setNumber(String key, Number value) {
        return setValue(key, value);
    }

    @Override
    public MutableDictionary setInt(String key, int value) {
        return setValue(key, value);
    }

    @Override
    public MutableDictionary setLong(String key, long value) {
        return setValue(key, value);
    }

    @Override
    public MutableDictionary setFloat(String key, float value) {
        return setValue(key, value);
    }

    @Override
    public MutableDictionary setDouble(String key, double value) {
        return setValue(key, value);
    }

    @Override
    public MutableDictionary setBoolean(String key, boolean value) {
        return setValue(key, value);
    }

    @Override
    public MutableDictionary setBlob(String key, Blob value) {
        return setValue(key, value);
    }

    @Override
    public MutableDictionary setDate(String key, Date value) {
        return setValue(key, value);
    }

    @Override
    public MutableDictionary setArray(String key, Array value) {
        return setValue(key, value);
    }

    @Override
    public MutableDictionary setDictionary(String key, Dictionary value) {
        return setValue(key, value);
    }

    /**
     * Removes the mapping for a key from this Dictionary
     *
     * @param key the key.
     * @return this Dictionary instance
     */
    @Override
    public MutableDictionary remove(String key) {
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
    public MutableArray getArray(String key) {
        Object obj = _get(_dict, key).asNative(_dict);
        return obj instanceof MutableArray ? (MutableArray) obj : null;
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
        Object obj = _get(_dict, key).asNative(_dict);
        return obj instanceof MutableDictionary ? (MutableDictionary) obj : null;
    }


    protected boolean isChanged() {
        return _dict.isMutated();
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }
}
