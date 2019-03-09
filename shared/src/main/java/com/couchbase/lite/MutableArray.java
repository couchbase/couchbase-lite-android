//
// MutableArray.java
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
import java.util.List;

import com.couchbase.lite.internal.fleece.MArray;
import com.couchbase.lite.internal.fleece.MCollection;
import com.couchbase.lite.internal.fleece.MValue;


/**
 * MutableArray provides access to array data.
 */
public final class MutableArray extends Array implements MutableArrayInterface {
    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Constructs a new empty Array object.
     */
    public MutableArray() {
        super();
    }

    /**
     * Constructs a new Array object with an array content. Allowed value types are List, Date,
     * Map, Number, null, String, Array, Blob, and Dictionary. The List and Map must contain
     * only the above types.
     *
     * @param data the array object.
     */
    public MutableArray(List<Object> data) {
        super();
        setData(data);
    }

    // to create copy
    MutableArray(MArray mArray, boolean isMutable) {
        super(mArray, isMutable);
    }

    // Call from native method
    MutableArray(MValue mv, MCollection parent) {
        super(mv, parent);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Set an array as a content. Allowed value types are List, Date,
     * Map, Number, null, String, Array, Blob, and Dictionary. The List and Map must contain
     * only the above types. Setting the new array content will replcace the current data
     * including the existing Array and Dictionary objects.
     *
     * @param data the array
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setData(List<Object> data) {
        synchronized (lock) {
            internalArray.clear();
            for (Object obj : data) { internalArray.append(Fleece.toCBLObject(obj)); }
            return this;
        }
    }

    /**
     * Set an object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setValue(int index, Object value) {
        synchronized (lock) {
            if (Fleece.valueWouldChange(value, internalArray.get(index), internalArray)
                && (!internalArray.set(index, Fleece.toCBLObject(value)))) {
                throwRangeException(index);
            }
            return this;
        }
    }

    /**
     * Sets an String object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the String object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setString(int index, String value) {
        return setValue(index, value);
    }

    /**
     * Sets an NSNumber object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Number object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setNumber(int index, Number value) {
        return setValue(index, value);
    }

    /**
     * Sets an integer value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the int value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setInt(int index, int value) {
        return setValue(index, value);
    }

    /**
     * Sets an integer value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the long value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setLong(int index, long value) {
        return setValue(index, value);
    }

    /**
     * Sets a float value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the float value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setFloat(int index, float value) {
        return setValue(index, value);
    }

    /**
     * Sets a double value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the double value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setDouble(int index, double value) {
        return setValue(index, value);
    }

    /**
     * Sets a boolean value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the boolean value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setBoolean(int index, boolean value) {
        return setValue(index, value);
    }

    /**
     * Sets a Blob object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Blob object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setBlob(int index, Blob value) {
        return setValue(index, value);
    }

    /**
     * Sets a Date object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Date object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setDate(int index, Date value) {
        return setValue(index, value);
    }

    /**
     * Sets a Array object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Array object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setArray(int index, Array value) {
        return setValue(index, value);
    }

    /**
     * Sets a Dictionary object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Dictionary object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray setDictionary(int index, Dictionary value) {
        return setValue(index, value);
    }

    /**
     * Adds an object to the end of the array.
     *
     * @param value the object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addValue(Object value) {
        synchronized (lock) {
            internalArray.append(Fleece.toCBLObject(value));
            return this;
        }
    }

    /**
     * Adds a String object to the end of the array.
     *
     * @param value the String object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addString(String value) {
        return addValue(value);
    }

    /**
     * Adds a Number object to the end of the array.
     *
     * @param value the Number object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addNumber(Number value) {
        return addValue(value);
    }

    /**
     * Adds an integer value to the end of the array.
     *
     * @param value the int value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addInt(int value) {
        return addValue(value);
    }

    /**
     * Adds a long value to the end of the array.
     *
     * @param value the long value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addLong(long value) {
        return addValue(value);
    }

    /**
     * Adds a float value to the end of the array.
     *
     * @param value the float value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addFloat(float value) {
        return addValue(value);
    }

    /**
     * Adds a double value to the end of the array.
     *
     * @param value the double value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addDouble(double value) {
        return addValue(value);
    }

    /**
     * Adds a boolean value to the end of the array.
     *
     * @param value the boolean value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addBoolean(boolean value) {
        return addValue(value);
    }

    /**
     * Adds a Blob object to the end of the array.
     *
     * @param value the Blob object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addBlob(Blob value) {
        return addValue(value);
    }

    /**
     * Adds a Date object to the end of the array.
     *
     * @param value the Date object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addDate(Date value) {
        return addValue(value);
    }

    /**
     * Adds an Array object to the end of the array.
     *
     * @param value the Array object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addArray(Array value) {
        return addValue(value);
    }

    /**
     * Adds a Dictionary object to the end of the array.
     *
     * @param value the Dictonary object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray addDictionary(Dictionary value) {
        return addValue(value);
    }

    /**
     * Inserts an object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertValue(int index, Object value) {
        synchronized (lock) {
            if (!internalArray.insert(index, Fleece.toCBLObject(value))) { throwRangeException(index); }
            return this;
        }
    }

    /**
     * Inserts a String object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the String object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertString(int index, String value) {
        return insertValue(index, value);
    }

    /**
     * Inserts a Number object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Number object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertNumber(int index, Number value) {
        return insertValue(index, value);
    }

    /**
     * Inserts an integer value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the int value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertInt(int index, int value) {
        return insertValue(index, value);
    }

    /**
     * Inserts a long value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the long value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertLong(int index, long value) {
        return insertValue(index, value);
    }

    /**
     * Inserts a float value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the float value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertFloat(int index, float value) {
        return insertValue(index, value);
    }

    /**
     * Inserts a double value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the double value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertDouble(int index, double value) {
        return insertValue(index, value);
    }

    /**
     * Inserts a boolean value at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the boolean value
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertBoolean(int index, boolean value) {
        return insertValue(index, value);
    }

    /**
     * Inserts a Blob object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Blob object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertBlob(int index, Blob value) {
        return insertValue(index, value);
    }

    /**
     * Inserts a Date object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Date object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertDate(int index, Date value) {
        return insertValue(index, value);
    }

    /**
     * Inserts an Array object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Array object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertArray(int index, Array value) {
        return insertValue(index, value);
    }

    /**
     * Inserts a Dictionary object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the Dictionary object
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray insertDictionary(int index, Dictionary value) {
        return insertValue(index, value);
    }

    /**
     * Removes the object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return The self object
     */
    @NonNull
    @Override
    public MutableArray remove(int index) {
        synchronized (lock) {
            if (!internalArray.remove(index)) { throwRangeException(index); }
            return this;
        }
    }

    /**
     * Gets a Array at the given index. Return null if the value is not an array.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Array object.
     */
    @Override
    public MutableArray getArray(int index) {
        return (MutableArray) super.getArray(index);
    }

    /**
     * Gets a Dictionary at the given index. Return null if the value is not an dictionary.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Dictionary object.
     */
    @Override
    public MutableDictionary getDictionary(int index) {
        return (MutableDictionary) super.getDictionary(index);
    }
}
