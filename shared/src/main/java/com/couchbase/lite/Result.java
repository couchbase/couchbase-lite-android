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

import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.SharedKeys;
import com.couchbase.litecore.fleece.FLArrayIterator;
import com.couchbase.litecore.fleece.FLValue;
import com.couchbase.litecore.fleece.MContext;
import com.couchbase.litecore.fleece.MRoot;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Result represents a row of result set returned by a Query.
 */
public class Result
        implements ArrayInterface, DictionaryInterface, Iterable<String> {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private ResultSet rs;
    private FLArrayIterator columns;
    MContext context;

    //---------------------------------------------
    // constructors
    //---------------------------------------------
    Result(ResultSet rs, FLArrayIterator columns, MContext context) {
        this.rs = rs;
        this.columns = columns;
        this.context = context;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    //---------------------------------------------
    // implementation of common betwen ReadOnlyArrayInterface and ReadOnlyDictionaryInterface
    //--------------------------------------------

    @Override
    public int count() {
        return rs.columnCount();
    }

    //---------------------------------------------
    // implementation of ReadOnlyArrayInterface
    //---------------------------------------------

    @Override
    public Object getValue(int index) {
        return fleeceValueToObject(index);
    }

    @Override
    public String getString(int index) {
        Object obj = fleeceValueToObject(index);
        return obj instanceof String ? (String) obj : null;
    }

    @Override
    public Number getNumber(int index) {
        return CBLConverter.getNumber(fleeceValueToObject(index));
    }

    @Override
    public int getInt(int index) {
        FLValue flValue = fleeceValue(index);
        return flValue != null ? (int) flValue.asInt() : 0;
    }

    @Override
    public long getLong(int index) {
        FLValue flValue = fleeceValue(index);
        return flValue != null ? flValue.asInt() : 0L;
    }

    @Override
    public float getFloat(int index) {
        FLValue flValue = fleeceValue(index);
        return flValue != null ? flValue.asFloat() : 0.0F;
    }

    @Override
    public double getDouble(int index) {
        FLValue flValue = fleeceValue(index);
        return flValue != null ? flValue.asDouble() : 0.0;
    }

    @Override
    public boolean getBoolean(int index) {
        FLValue flValue = fleeceValue(index);
        return flValue != null ? flValue.asBool() : false;
    }

    @Override
    public Blob getBlob(int index) {
        Object obj = fleeceValueToObject(index);
        return obj instanceof Blob ? (Blob) obj : null;
    }

    @Override
    public Date getDate(int index) {
        return DateUtils.fromJson(getString(index));
    }

    @Override
    public Array getArray(int index) {
        Object obj = fleeceValueToObject(index);
        return obj instanceof Array ? (Array) obj : null;
    }

    @Override
    public Dictionary getDictionary(int index) {
        Object obj = fleeceValueToObject(index);
        return obj instanceof Dictionary ? (Dictionary) obj : null;
    }

    @Override
    public List<Object> toList() {
        List<Object> array = new ArrayList<>();
        for (int i = 0; i < count(); i++) {
            SharedKeys sk = rs.getQuery().getDatabase().getSharedKeys();
            array.add(SharedKeys.valueToObject(fleeceValue(i), sk));
        }
        return array;
    }

    //---------------------------------------------
    // implementation of ReadOnlyDictionaryInterface
    //---------------------------------------------
    @Override
    public List<String> getKeys() {
        return new ArrayList<>(rs.getColumnNames().keySet());
    }

    @Override
    public Object getValue(String key) {
        int index = indexForColumnName(key);
        return index >= 0 ? getValue(index) : null;
    }

    @Override
    public String getString(String key) {
        int index = indexForColumnName(key);
        return index >= 0 ? getString(index) : null;
    }

    @Override
    public Number getNumber(String key) {
        int index = indexForColumnName(key);
        return index >= 0 ? getNumber(index) : null;
    }

    @Override
    public int getInt(String key) {
        int index = indexForColumnName(key);
        return index >= 0 ? getInt(index) : 0;
    }

    @Override
    public long getLong(String key) {
        int index = indexForColumnName(key);
        return index >= 0 ? getLong(index) : 0L;
    }

    @Override
    public float getFloat(String key) {
        int index = indexForColumnName(key);
        return index >= 0 ? getFloat(index) : 0.0f;
    }

    @Override
    public double getDouble(String key) {
        int index = indexForColumnName(key);
        return index >= 0 ? getDouble(index) : 0.0;
    }

    @Override
    public boolean getBoolean(String key) {
        int index = indexForColumnName(key);
        return index >= 0 ? getBoolean(index) : false;
    }

    @Override
    public Blob getBlob(String key) {
        int index = indexForColumnName(key);
        return index >= 0 ? getBlob(index) : null;
    }

    @Override
    public Date getDate(String key) {
        int index = indexForColumnName(key);
        return index >= 0 ? getDate(index) : null;
    }

    @Override
    public Array getArray(String key) {
        int index = indexForColumnName(key);
        return index >= 0 ? getArray(index) : null;
    }

    @Override
    public Dictionary getDictionary(String key) {
        int index = indexForColumnName(key);
        return index >= 0 ? getDictionary(index) : null;
    }

    @Override
    public Map<String, Object> toMap() {
        List<Object> values = toList();
        Map<String, Object> dict = new HashMap<>();
        for (String name : rs.getColumnNames().keySet()) {
            int index = indexForColumnName(name);
            dict.put(name, values.get(index));
        }
        return dict;
    }

    @Override
    public boolean contains(String key) {
        return indexForColumnName(key) >= 0;
    }

    //---------------------------------------------
    // implementation of Iterable
    //---------------------------------------------
    @Override
    public Iterator<String> iterator() {
        // TODO !!!
        return null;
    }

    //---------------------------------------------
    // private level access
    //---------------------------------------------

    // - (NSInteger) indexForColumnName: (NSString*)name
    private int indexForColumnName(String name) {
        Integer index = rs.getColumnNames().get(name);
        return index != null ? index.intValue() : -1;
    }

    // - (FLValue) fleeceValueAtIndex: (NSUInteger)index
    private FLValue fleeceValue(int index) {
        return columns.getValueAt(index);
    }

    // - (id) fleeceValueToObjectAtIndex: (NSUInteger)index
    private Object fleeceValueToObject(int index) {
        FLValue value = fleeceValue(index);
        if (value == null) return null;
        MRoot root = new MRoot(context, value, false);
        return root.asNative();
    }
}
