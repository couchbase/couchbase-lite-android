//
// Result.java
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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.couchbase.lite.internal.core.C4QueryEnumerator;
import com.couchbase.lite.internal.fleece.FLArrayIterator;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.fleece.MContext;
import com.couchbase.lite.internal.fleece.MRoot;
import com.couchbase.lite.internal.utils.DateUtils;


/**
 * Result represents a row of result set returned by a Query.
 */
public final class Result implements ArrayInterface, DictionaryInterface, Iterable<String> {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final ResultSet rs;
    private final List<FLValue> values;
    private final long missingColumns;
    private final MContext context;

    //---------------------------------------------
    // constructors
    //---------------------------------------------
    Result(ResultSet rs, C4QueryEnumerator c4enum, MContext context) {
        this.rs = rs;
        this.values = extractColumns(c4enum.getColumns());
        this.missingColumns = c4enum.getMissingColumns();
        this.context = context;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    //---------------------------------------------
    // implementation of common betwen ReadOnlyArrayInterface and ReadOnlyDictionaryInterface
    //--------------------------------------------

    /**
     * Return A number of the projecting values in the result.
     */
    @Override
    public int count() {
        return rs.columnCount();
    }

    //---------------------------------------------
    // implementation of ReadOnlyArrayInterface
    //---------------------------------------------

    /**
     * The projecting result value at the given index.
     *
     * @param index The select result index as a Object.
     * @return The value.
     */
    @Override
    public Object getValue(int index) {
        check(index);
        return fleeceValueToObject(index);
    }

    /**
     * The projecting result value at the given index as a String object
     *
     * @param index The select result index.
     * @return The String object.
     */
    @Override
    public String getString(int index) {
        check(index);
        final Object obj = fleeceValueToObject(index);
        return obj instanceof String ? (String) obj : null;
    }

    /**
     * The projecting result value at the given index as a Number object
     *
     * @param index The select result index.
     * @return The Number object.
     */
    @Override
    public Number getNumber(int index) {
        check(index);
        return CBLConverter.asNumber(fleeceValueToObject(index));
    }

    /**
     * The projecting result value at the given index as a integer value
     *
     * @param index The select result index.
     * @return The integer value.
     */
    @Override
    public int getInt(int index) {
        check(index);
        final FLValue flValue = values.get(index);
        return flValue != null ? (int) flValue.asInt() : 0;
    }

    /**
     * The projecting result value at the given index as a long value
     *
     * @param index The select result index.
     * @return The long value.
     */
    @Override
    public long getLong(int index) {
        check(index);
        final FLValue flValue = values.get(index);
        return flValue != null ? flValue.asInt() : 0L;
    }

    /**
     * The projecting result value at the given index  as a float value
     *
     * @param index The select result index.
     * @return The float value.
     */
    @Override
    public float getFloat(int index) {
        check(index);
        final FLValue flValue = values.get(index);
        return flValue != null ? flValue.asFloat() : 0.0F;
    }

    /**
     * The projecting result value at the given index  as a double value
     *
     * @param index The select result index.
     * @return The double value.
     */
    @Override
    public double getDouble(int index) {
        check(index);
        final FLValue flValue = values.get(index);
        return flValue != null ? flValue.asDouble() : 0.0;
    }

    /**
     * The projecting result value at the given index  as a boolean value
     *
     * @param index The select result index.
     * @return The boolean value.
     */
    @Override
    public boolean getBoolean(int index) {
        check(index);
        final FLValue flValue = values.get(index);
        return flValue != null && flValue.asBool();
    }

    /**
     * The projecting result value at the given index  as a Blob object
     *
     * @param index The select result index.
     * @return The Blob object.
     */
    @Override
    public Blob getBlob(int index) {
        check(index);
        final Object obj = fleeceValueToObject(index);
        return obj instanceof Blob ? (Blob) obj : null;
    }

    /**
     * The projecting result value at the given index  as an Array object
     *
     * @param index The select result index.
     * @return The object.
     */
    @Override
    public Date getDate(int index) {
        check(index);
        return DateUtils.fromJson(getString(index));
    }

    /**
     * The projecting result value at the given index as a Array object
     *
     * @param index The select result index.
     * @return The object.
     */
    @Override
    public Array getArray(int index) {
        check(index);
        final Object obj = fleeceValueToObject(index);
        return obj instanceof Array ? (Array) obj : null;
    }

    /**
     * The projecting result value at the given index  as a Dictionary object
     *
     * @param index The select result index.
     * @return The object.
     */
    @Override
    public Dictionary getDictionary(int index) {
        check(index);
        final Object obj = fleeceValueToObject(index);
        return obj instanceof Dictionary ? (Dictionary) obj : null;
    }

    /**
     * Gets all values as an List. The value types of the values contained
     * in the returned List object are Array, Blob, Dictionary, Number types, String, and null.
     *
     * @return The List representing all values.
     */
    @NonNull
    @Override
    public List<Object> toList() {
        final List<Object> array = new ArrayList<>();
        for (int i = 0; i < count(); i++) {
            array.add(values.get(i).asObject());
        }
        return array;
    }

    //---------------------------------------------
    // implementation of ReadOnlyDictionaryInterface
    //---------------------------------------------

    /**
     * Return All projecting keys
     */
    @NonNull
    @Override
    public List<String> getKeys() {
        return new ArrayList<>(rs.getColumnNames().keySet());
    }

    /**
     * The projecting result value for the given key as a Object
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The Object.
     */
    @Override
    public Object getValue(@NonNull String key) {
        if (key == null) { throw new IllegalArgumentException("key cannot be null."); }

        final int index = indexForColumnName(key);
        return index >= 0 ? getValue(index) : null;
    }

    /**
     * The projecting result value for the given key as a String object
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The String object.
     */
    @Override
    public String getString(@NonNull String key) {
        if (key == null) { throw new IllegalArgumentException("key cannot be null."); }

        final int index = indexForColumnName(key);
        return index >= 0 ? getString(index) : null;
    }

    /**
     * The projecting result value for the given key  as a Number object
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The Number object.
     */
    @Override
    public Number getNumber(@NonNull String key) {
        if (key == null) { throw new IllegalArgumentException("key cannot be null."); }

        final int index = indexForColumnName(key);
        return index >= 0 ? getNumber(index) : null;
    }

    /**
     * The projecting result value for the given key  as a integer value
     * Returns 0 if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The integer value.
     */
    @Override
    public int getInt(@NonNull String key) {
        if (key == null) { throw new IllegalArgumentException("key cannot be null."); }

        final int index = indexForColumnName(key);
        return index >= 0 ? getInt(index) : 0;
    }

    /**
     * The projecting result value for the given key  as a long value
     * Returns 0L if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The long value.
     */
    @Override
    public long getLong(@NonNull String key) {
        if (key == null) { throw new IllegalArgumentException("key cannot be null."); }

        final int index = indexForColumnName(key);
        return index >= 0 ? getLong(index) : 0L;
    }

    /**
     * The projecting result value for the given key  as a float value.
     * Returns 0.0f if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The float value.
     */
    @Override
    public float getFloat(@NonNull String key) {
        if (key == null) { throw new IllegalArgumentException("key cannot be null."); }

        final int index = indexForColumnName(key);
        return index >= 0 ? getFloat(index) : 0.0f;
    }

    /**
     * The projecting result value for the given key as a double value.
     * Returns 0.0 if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The double value.
     */
    @Override
    public double getDouble(@NonNull String key) {
        if (key == null) { throw new IllegalArgumentException("key cannot be null."); }

        final int index = indexForColumnName(key);
        return index >= 0 ? getDouble(index) : 0.0;
    }

    /**
     * The projecting result value for the given key  as a boolean value.
     * Returns false if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The boolean value.
     */
    @Override
    public boolean getBoolean(@NonNull String key) {
        if (key == null) { throw new IllegalArgumentException("key cannot be null."); }

        final int index = indexForColumnName(key);
        return index >= 0 && getBoolean(index);
    }

    /**
     * The projecting result value for the given key  as a Blob object.
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The Blob object.
     */
    @Override
    public Blob getBlob(@NonNull String key) {
        if (key == null) { throw new IllegalArgumentException("key cannot be null."); }

        final int index = indexForColumnName(key);
        return index >= 0 ? getBlob(index) : null;
    }

    /**
     * The projecting result value for the given key as a Date object.
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The Date object.
     */
    @Override
    public Date getDate(@NonNull String key) {
        if (key == null) { throw new IllegalArgumentException("key cannot be null."); }

        final int index = indexForColumnName(key);
        return index >= 0 ? getDate(index) : null;
    }

    /**
     * The projecting result value for the given key as a readonly Array object.
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The Array object.
     */
    @Override
    public Array getArray(@NonNull String key) {
        if (key == null) { throw new IllegalArgumentException("key cannot be null."); }

        final int index = indexForColumnName(key);
        return index >= 0 ? getArray(index) : null;
    }

    /**
     * The projecting result value for the given key as a readonly Dictionary object.
     * Returns null if the key doesn't exist.
     *
     * @param key The select result key.
     * @return The Dictionary object.
     */
    @Override
    public Dictionary getDictionary(@NonNull String key) {
        if (key == null) { throw new IllegalArgumentException("key cannot be null."); }

        final int index = indexForColumnName(key);
        return index >= 0 ? getDictionary(index) : null;
    }

    /**
     * Gets all values as a Dictionary. The value types of the values contained
     * in the returned Dictionary object are Array, Blob, Dictionary,
     * Number types, String, and null.
     *
     * @return The Map representing all values.
     */
    @NonNull
    @Override
    public Map<String, Object> toMap() {
        final List<Object> values = toList();
        final Map<String, Object> dict = new HashMap<>();
        for (String name : rs.getColumnNames().keySet()) {
            final int index = indexForColumnName(name);
            if (index >= 0) { dict.put(name, values.get(index)); }
        }
        return dict;
    }

    /**
     * Tests whether a projecting result key exists or not.
     *
     * @param key The select result key.
     * @return True if exists, otherwise false.
     */
    @Override
    public boolean contains(@NonNull String key) {
        if (key == null) { throw new IllegalArgumentException("key cannot be null."); }

        return indexForColumnName(key) >= 0;
    }

    //---------------------------------------------
    // implementation of Iterable
    //---------------------------------------------

    /**
     * Gets  an iterator over the projecting result keys.
     *
     * @return The Iterator object of all result keys.
     */
    @NonNull
    @Override
    public Iterator<String> iterator() {
        return getKeys().iterator();
    }

    //---------------------------------------------
    // private level access
    //---------------------------------------------

    // - (NSInteger) indexForColumnName: (NSString*)name
    private int indexForColumnName(String name) {
        final Integer index = rs.getColumnNames().get(name);
        if (index == null) { return -1; }
        return ((missingColumns & (1 << index.intValue())) == 0) ? index.intValue() : -1;
    }

    // - (id) fleeceValueToObjectAtIndex: (NSUInteger)index
    private Object fleeceValueToObject(int index) {
        check(index);
        final FLValue value = values.get(index);
        if (value == null) { return null; }
        final MRoot root = new MRoot(context, value, false);
        synchronized (rs.getQuery().getDatabase().getLock()) {
            return root.asNative();
        }
    }

    private List<FLValue> extractColumns(FLArrayIterator columns) {
        final int count = rs.getColumnNames().size();
        final List<FLValue> values = new ArrayList<>();
        for (int i = 0; i < count; i++) { values.add(columns.getValueAt(i)); }
        return values;
    }

    private void check(int index) {
        if (index < 0 || index >= count()) {
            throw new ArrayIndexOutOfBoundsException(
                String.format(Locale.ENGLISH, "length=%d; index=%d", count(), index));
        }
    }
}
