package com.couchbase.lite;


import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.fleece.Encoder;
import com.couchbase.litecore.fleece.FLEncodable;
import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.MCollection;
import com.couchbase.litecore.fleece.MDict;
import com.couchbase.litecore.fleece.MDictIterator;
import com.couchbase.litecore.fleece.MValue;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * ReadOnlyDictionary provides readonly access to dictionary data.
 */
public class ReadOnlyDictionary implements ReadOnlyDictionaryInterface, FLEncodable, Iterable<String> {

    //-------------------------------------------------------------------------
    // member variables
    //-------------------------------------------------------------------------

    MDict _dict = new MDict(); // pointer to MDict<JNIRef> in native

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    ReadOnlyDictionary() {
        super();
    }

    ReadOnlyDictionary(MValue mv, MCollection parent) {
        super();
        _dict.initInSlot(mv, parent);
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
        return (int)_dict.count();
    }

    @Override
    public List<String> getKeys() {
        List<String> keys = new ArrayList<>(count());
        MDictIterator itr = new MDictIterator(_dict);
        String key;
        while ((key = itr.key()) != null) {
            keys.add(key);
            if (!itr.next())
                break;
        }
        return keys;
    }

    /**
     * @param dict
     * @param key
     * @return pointer to MValue
     */
    static MValue _get(MDict dict, String key) {
        return dict.get(key);
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
        return _get(_dict, key).asNative(_dict);
    }

    /**
     * Gets a property's value as a String. Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param key the key
     * @return the String or null.
     */
    @Override
    public String getString(String key) {
        Object obj = _get(_dict, key).asNative(_dict);
        return obj instanceof String ? (String) obj : null;
    }

    /**
     * Gets a property's value as a Number. Returns null if the value doesn't exist, or its value is not a Number.
     *
     * @param key the key
     * @return the Number or nil.
     */
    @Override
    public Number getNumber(String key) {
        return CBLFleece.getNumber(_get(_dict, key).asNative(_dict));
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
        return CBLFleece.asInteger(_get(_dict, key), _dict);
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
        return CBLFleece.asLong(_get(_dict, key), _dict);
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
        return CBLFleece.asFloat(_get(_dict, key), _dict);
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
        return CBLFleece.asDouble(_get(_dict, key), _dict);
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
        return CBLFleece.asBool(_get(_dict, key), _dict);
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
        Object obj = _get(_dict, key).asNative(_dict);
        return obj instanceof Blob ? (Blob) obj : null;
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
        Object obj = _get(_dict, key).asNative(_dict);
        return obj instanceof ReadOnlyArray ? (ReadOnlyArray) obj : null;
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
        Object obj = _get(_dict, key).asNative(_dict);
        return obj instanceof ReadOnlyDictionary ? (ReadOnlyDictionary) obj : null;
    }

    /**
     * Gets content of the current object as an Map. The values contained in the returned
     * Map object are all JSON based values.
     *
     * @return the Map object representing the content of the current object in the JSON format.
     */
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> result = new HashMap<>();
        MDictIterator itr = new MDictIterator(_dict);
        String key;
        while ((key = itr.key()) != null) {
            result.put(key,  CBLFleece.toObject(_get(_dict, key).asNative(_dict)));
            if (!itr.next())
                break;
        }
        return result;
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
        return !_get(_dict, key).isEmpty();
    }

    //-------------------------------------------------------------------------
    // Implementation of FLEncodable
    //-------------------------------------------------------------------------

    @Override
    public void encodeTo(FLEncoder enc) {
        Encoder encoder = new Encoder(enc);
        _dict.encodeTo(encoder);
        encoder.release();
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
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
    boolean isEmpty() {
        return count() == 0;
    }
}
