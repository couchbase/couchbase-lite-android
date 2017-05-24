package com.couchbase.lite;


import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLDictIterator;
import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.FLValue;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * ReadOnlyDictionary provides readonly access to dictionary data.
 */
public class ReadOnlyDictionary implements ReadOnlyDictionaryInterface, FleeceEncodable {

    //-------------------------------------------------------------------------
    // member variables
    //-------------------------------------------------------------------------
    private CBLFLDict data;
    private FLDict flDict;
    private SharedKeys sharedKeys;
    private List<String> keys = null; // dictionary key cache

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    /* package */ ReadOnlyDictionary(CBLFLDict data) {
        setData(data);
    }

    //-------------------------------------------------------------------------
    // API - public methods
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    // Implementation of ReadOnlyDictionaryInterface
    //-------------------------------------------------------------------------

    /**
     * Gets a number of the entries in the dictionary.
     *
     * @return
     */
    @Override
    public int count() {
        return flDict != null ? (int) flDict.count() : 0;
    }

    @Override
    public List<String> getKeys() {
        return fleeceKeys();
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
        return fleeceValueToObject(key);
    }

    /**
     * Gets a property's value as a String. Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param key the key
     * @return the String or null.
     */
    @Override
    public String getString(String key) {
        return (String) fleeceValueToObject(key);
    }

    /**
     * Gets a property's value as a Number. Returns null if the value doesn't exist, or its value is not a Number.
     *
     * @param key the key
     * @return the Number or nil.
     */
    @Override
    public Number getNumber(String key) {
        return (Number) fleeceValueToObject(key);
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
        FLValue value = fleeceValue(key);
        return value != null ? (int)value.asInt() : 0;
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
        // TODO asLong()?
        FLValue value = fleeceValue(key);
        return value != null ? value.asInt() : 0;
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
        FLValue value = fleeceValue(key);
        return value != null ? value.asFloat() : 0.0f;
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
        FLValue value = fleeceValue(key);
        return value != null ? value.asDouble() : 0.0;
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
        FLValue value = fleeceValue(key);
        return value != null ? value.asBool() : false;
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
        return (Blob) fleeceValueToObject(key);
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
        return DateUtils.fromJson(getString(key));
    }

    /**
     * Get a property's value as a Array, which is a mapping object of an array value.
     * Returns null if the property doesn't exists, or its value is not an array.
     *
     * @param key the key.
     * @return the Array object.
     */
    @Override
    public ReadOnlyArray getArray(String key) {
        return (ReadOnlyArray) fleeceValueToObject(key);
    }

    /**
     * Get a property's value as a Dictionary, which is a mapping object of an dictionary value.
     * Returns null if the property doesn't exists, or its value is not an dictionary.
     *
     * @param key the key.
     * @return the Dictionary object or null if the key doesn't exist.
     */
    @Override
    public ReadOnlyDictionary getDictionary(String key) {
        return (ReadOnlyDictionary) fleeceValueToObject(key);
    }

    /**
     * Gets content of the current object as an Map. The values contained in the returned
     * Map object are all JSON based values.
     *
     * @return the Map object representing the content of the current object in the JSON format.
     */
    @Override
    public Map<String, Object> toMap() {
        if (flDict != null)
            // TODO: Need to review!
            return flDictToMap(flDict);
        else
            return new HashMap<>();
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
        // TODO: Need to review!
        return fleeceValue(key) != null;
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    protected CBLFLDict getData() {
        return data;
    }

    protected void setData(CBLFLDict data) {
        this.data = data;
        this.flDict = null;
        this.sharedKeys = null;
        if (data != null) {
            this.flDict = data.getFlDict();
            if (data.getDatabase() != null)
                this.sharedKeys = data.getDatabase().getSharedKeys();
        }
    }


    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    // FleeceEncodable implementation
    @Override
    public void fleeceEncode(FLEncoder encoder, Database database) throws CouchbaseLiteException {
        encoder.writeValue(flDict);
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------
    @Override
    public Iterator<String> iterator() {
        return getKeys().iterator();
    }

    //---------------------------------------------
    // Package
    //---------------------------------------------
    /* package */boolean isEmpty() {
        return count() == 0;
    }
    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    // #pragma mark - FLEECE

    private FLValue fleeceValue(String key) {
        return SharedKeys.getValue(flDict, key, sharedKeys);
    }

    private Object fleeceValueToObject(String key) {
        FLValue value = fleeceValue(key);
        if (value != null)
            return CBLData.fleeceValueToObject(value, data.getC4doc(), data.getDatabase());
        else
            return null;
    }

    private Map<String, Object> flDictToMap(FLDict flDict) {
        if (flDict == null) return null;

        Map<String, Object> dict = new HashMap<>();
        FLDictIterator itr = new FLDictIterator();
        itr.begin(flDict);
        String key;
        while ((key = SharedKeys.getKey(itr, sharedKeys)) != null) {
            dict.put(key, CBLData.fleeceValueToObject(itr.getValue(), data.getC4doc(), data.getDatabase()));
            itr.next();
        }
        itr.free();

        return dict;
    }

    private List<String> fleeceKeys() {
        if (keys == null) {
            List<String> results = new ArrayList<>();
            if (flDict != null) {
                FLDictIterator itr = new FLDictIterator();
                itr.begin(flDict);
                String key;
                while ((key = SharedKeys.getKey(itr, this.sharedKeys)) != null) {
                    results.add(key);
                    itr.next();
                }
                itr.free();
            }

            keys = results;
        }
        return keys;
    }
}
