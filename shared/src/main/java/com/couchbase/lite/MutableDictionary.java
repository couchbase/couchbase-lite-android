//
// MutableDictionary.java
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

import java.util.Date;
import java.util.Map;

import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MDict;
import com.couchbase.lite.internal.fleece.MValue;


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
    public MutableDictionary() { }

    /**
     * Initializes a new CBLDictionary object with dictionary content. Allowed value types are List,
     * Date, Map, Number, null, String, Array, Blob, and Dictionary. The List and Map must contain
     * only the above types.
     *
     * @param data the dictionary object.
     */
    public MutableDictionary(Map<String, Object> data) { setData(data); }

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
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setData(Map<String, Object> data) {
        synchronized (lock) {
            internalDict.clear();
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                internalDict.set(
                    entry.getKey(),
                    new MValue(Fleece.toCBLObject(entry.getValue())));
            }
            return this;
        }
    }

    /**
     * Set an object value by key. Allowed value types are List, Date, Map, Number, null, String,
     * Array, Blob, and Dictionary. The List and Map must contain only the above types.
     * An Date object will be converted to an ISO-8601 format string.
     *
     * @param key   the key.
     * @param value the object value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setValue(@NonNull String key, Object value) {
        if (key == null) { throw new IllegalArgumentException("key cannot be null."); }

        synchronized (lock) {
            final MValue oldValue = internalDict.get(key);
            value = Fleece.toCBLObject(value);
            if (Fleece.valueWouldChange(value, oldValue, internalDict)) { internalDict.set(key, new MValue(value)); }
            return this;
        }
    }

    /**
     * Set a String value for the given key.
     *
     * @param key   The key
     * @param value The String value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setString(@NonNull String key, String value) {
        return setValue(key, value);
    }

    /**
     * Set a Number value for the given key.
     *
     * @param key   The key
     * @param value The number value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setNumber(@NonNull String key, Number value) {
        return setValue(key, value);
    }

    /**
     * Set an int value for the given key.
     *
     * @param key   The key
     * @param value The int value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setInt(@NonNull String key, int value) {
        return setValue(key, value);
    }

    /**
     * Set a long value for the given key.
     *
     * @param key   The key
     * @param value The long value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setLong(@NonNull String key, long value) {
        return setValue(key, value);
    }

    /**
     * Set a float value for the given key.
     *
     * @param key   The key
     * @param value The float value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setFloat(@NonNull String key, float value) {
        return setValue(key, value);
    }

    /**
     * Set a double value for the given key.
     *
     * @param key   The key
     * @param value The double value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setDouble(@NonNull String key, double value) {
        return setValue(key, value);
    }

    /**
     * Set a boolean value for the given key.
     *
     * @param key   The key
     * @param value The boolean value.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setBoolean(@NonNull String key, boolean value) {
        return setValue(key, value);
    }

    /**
     * Set a Blob object for the given key.
     *
     * @param key   The key
     * @param value The Blob object.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setBlob(@NonNull String key, Blob value) {
        return setValue(key, value);
    }

    /**
     * Set a Date object for the given key.
     *
     * @param key   The key
     * @param value The Date object.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setDate(@NonNull String key, Date value) {
        return setValue(key, value);
    }

    /**
     * Set an Array object for the given key.
     *
     * @param key   The key
     * @param value The Array object.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setArray(@NonNull String key, Array value) {
        return setValue(key, value);
    }

    /**
     * Set a Dictionary object for the given key.
     *
     * @param key   The key
     * @param value The Dictionary object.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary setDictionary(@NonNull String key, Dictionary value) {
        return setValue(key, value);
    }

    /**
     * Removes the mapping for a key from this Dictionary
     *
     * @param key the key.
     * @return The self object.
     */
    @NonNull
    @Override
    public MutableDictionary remove(@NonNull String key) {
        if (key == null) { throw new IllegalArgumentException("key cannot be null."); }

        synchronized (lock) {
            internalDict.remove(key);
            return this;
        }
    }

    /**
     * Get a property's value as a Array, which is a mapping object of an array value.
     * Returns null if the property doesn't exists, or its value is not an array.
     *
     * @param key the key.
     * @return the Array object.
     */
    @Override
    public MutableArray getArray(@NonNull String key) {
        return (MutableArray) super.getArray(key);
    }

    /**
     * Get a property's value as a Dictionary, which is a mapping object of an dictionary value.
     * Returns null if the property doesn't exists, or its value is not an dictionary.
     *
     * @param key the key.
     * @return the Dictionary object or null if the key doesn't exist.
     */
    @Override
    public MutableDictionary getDictionary(@NonNull String key) {
        return (MutableDictionary) super.getDictionary(key);
    }


    protected boolean isChanged() {
        synchronized (lock) {
            return internalDict.isMutated();
        }
    }
}
