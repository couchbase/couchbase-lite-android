//
// Dictionary.java
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

import android.support.annotation.NonNull;

import com.couchbase.lite.internal.utils.DateUtils;
import com.couchbase.litecore.fleece.Encoder;
import com.couchbase.litecore.fleece.FLEncodable;
import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.MCollection;
import com.couchbase.litecore.fleece.MContext;
import com.couchbase.litecore.fleece.MDict;
import com.couchbase.litecore.fleece.MValue;

import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Dictionary provides readonly access to dictionary data.
 */
public class Dictionary implements DictionaryInterface, FLEncodable, Iterable<String> {
    //---------------------------------------------
    // Load LiteCore library and its dependencies
    //---------------------------------------------
    static {
        NativeLibraryLoader.load();
    }

    //-------------------------------------------------------------------------
    // member variables
    //-------------------------------------------------------------------------

    MDict _dict;

    protected Object _sharedLock;

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    Dictionary() {
        _dict = new MDict();
        setupSharedLock();
    }

    Dictionary(MValue mv, MCollection parent) {
        _dict = new MDict();
        _dict.initInSlot(mv, parent);
        setupSharedLock();
    }

    Dictionary(MDict mDict, boolean isMutable) {
        _dict = new MDict();
        _dict.initAsCopyOf(mDict, isMutable);
        setupSharedLock();
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
        synchronized (_sharedLock) {
            return (int) _dict.count();
        }
    }

    @NonNull
    @Override
    public List<String> getKeys() {
        synchronized (_sharedLock) {
            return _dict.getKeys();
        }
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
    public Object getValue(@NonNull String key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null.");

        synchronized (_sharedLock) {
            return _get(_dict, key).asNative(_dict);
        }
    }

    /**
     * Gets a property's value as a String. Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param key the key
     * @return the String or null.
     */
    @Override
    public String getString(@NonNull String key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null.");

        synchronized (_sharedLock) {
            Object obj = _get(_dict, key).asNative(_dict);
            return obj instanceof String ? (String) obj : null;
        }
    }

    /**
     * Gets a property's value as a Number. Returns null if the value doesn't exist, or its value is not a Number.
     *
     * @param key the key
     * @return the Number or nil.
     */
    @Override
    public Number getNumber(@NonNull String key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null.");

        synchronized (_sharedLock) {
            Object obj = _get(_dict, key).asNative(_dict);
            return CBLConverter.asNumber(obj);
        }
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
    public int getInt(@NonNull String key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null.");

        synchronized (_sharedLock) {
            return CBLConverter.asInteger(_get(_dict, key), _dict);
        }
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
    public long getLong(@NonNull String key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null.");

        synchronized (_sharedLock) {
            return CBLConverter.asLong(_get(_dict, key), _dict);
        }
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
    public float getFloat(@NonNull String key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null.");

        synchronized (_sharedLock) {
            return CBLConverter.asFloat(_get(_dict, key), _dict);
        }
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
    public double getDouble(@NonNull String key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null.");

        synchronized (_sharedLock) {
            return CBLConverter.asDouble(_get(_dict, key), _dict);
        }
    }

    /**
     * Gets a property's value as a boolean. Returns true if the value exists, and is either `true`
     * or a nonzero number.
     *
     * @param key the key
     * @return the boolean value.
     */
    @Override
    public boolean getBoolean(@NonNull String key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null.");

        synchronized (_sharedLock) {
            Object obj = _get(_dict, key).asNative(_dict);
            return CBLConverter.asBoolean(obj);
        }
    }

    /**
     * Gets a property's value as a Blob.
     * Returns null if the value doesn't exist, or its value is not a Blob.
     *
     * @param key the key
     * @return the Blob value or null.
     */
    @Override
    public Blob getBlob(@NonNull String key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null.");

        synchronized (_sharedLock) {
            Object obj = _get(_dict, key).asNative(_dict);
            return obj instanceof Blob ? (Blob) obj : null;
        }
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
    public Date getDate(@NonNull String key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null.");

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
    public Array getArray(@NonNull String key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null.");

        synchronized (_sharedLock) {
            Object obj = _get(_dict, key).asNative(_dict);
            return obj instanceof Array ? (Array) obj : null;
        }
    }

    /**
     * Get a property's value as a Dictionary, which is a mapping object of an dictionary value.
     * Returns null if the property doesn't exists, or its value is not an dictionary.
     *
     * @param key the key.
     * @return the Dictionary object or null if the key doesn't exist.
     */
    @Override
    public Dictionary getDictionary(@NonNull String key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null.");

        synchronized (_sharedLock) {
            Object obj = _get(_dict, key).asNative(_dict);
            return obj instanceof Dictionary ? (Dictionary) obj : null;
        }
    }

    /**
     * Gets content of the current object as an Map. The values contained in the returned
     * Map object are all JSON based values.
     *
     * @return the Map object representing the content of the current object in the JSON format.
     */
    @Override
    public Map<String, Object> toMap() {
        synchronized (_sharedLock) {
            Map<String, Object> result = new HashMap<>();
            for (String key : _dict) {
                result.put(key, Fleece.toObject(_get(_dict, key).asNative(_dict)));
            }
            return result;
        }
    }

    /**
     * Tests whether a property exists or not.
     * This can be less expensive than getValue(String), because it does not have to allocate an Object for the property value.
     *
     * @param key the key
     * @return the boolean value representing whether a property exists or not.
     */
    @Override
    public boolean contains(@NonNull String key) {
        if (key == null)
            throw new IllegalArgumentException("key cannot be null.");

        synchronized (_sharedLock) {
            return !_get(_dict, key).isEmpty();
        }
    }

    /**
     * Return a mutable copy of the dictionary
     *
     * @return the MutableDictionary instance
     */
    @NonNull
    public MutableDictionary toMutable() {
        synchronized (_sharedLock) {
            return new MutableDictionary(_dict, true);
        }
    }

    //-------------------------------------------------------------------------
    // Implementation of FLEncodable
    //-------------------------------------------------------------------------

    /**
     * encodeTo(FlEncoder) is internal method. Please don't use this method.
     */
    @NonNull
    @Override
    public void encodeTo(FLEncoder enc) {
        Encoder encoder = new Encoder(enc);
        _dict.encodeTo(encoder);
        encoder.release();
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------

    @NonNull
    @Override
    public Iterator<String> iterator() {
        return getKeys().iterator();
    }

    //---------------------------------------------
    // Override
    //---------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Dictionary)) return false;

        Dictionary m = (Dictionary) o;

        if (m.count() != count()) return false;
        Iterator<String> i = iterator();
        while (i.hasNext()) {
            String key = i.next();
            Object value = getValue(key);
            if (value == null) {
                if (!(m.getValue(key) == null && m.contains(key)))
                    return false;
            } else {
                if (!value.equals(m.getValue(key)))
                    return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        Iterator<String> i = iterator();
        while (i.hasNext()) {
            String key = i.next();
            h += hashCode(key, getValue(key));
        }
        return h;
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
    }

    protected boolean isEmpty() {
        return count() == 0;
    }

    //---------------------------------------------
    // private
    //---------------------------------------------

    private void setupSharedLock() {
        MContext context = _dict.getContext();
        if (context != null && context != MContext.NULL)
            _sharedLock = ((DocContext)context).getDatabase().getLock();
        else
            _sharedLock = new Object();
    }

    // hashCode for pair of key and value
    private int hashCode(String key, Object value) {
        return (key == null ? 0 : key.hashCode()) ^ (value == null ? 0 : value.hashCode());
    }

    //---------------------------------------------
    // package level access
    //---------------------------------------------

    MCollection toMCollection() {
        return _dict;
    }
}
