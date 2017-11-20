package com.couchbase.lite;


import com.couchbase.litecore.fleece.MCollection;
import com.couchbase.litecore.fleece.MValue;

import java.util.Date;
import java.util.List;

/**
 * Array provides access to array data.
 */
public class Array extends ReadOnlyArray implements ArrayInterface {
    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Constructs a new empty Array object.
     */
    public Array() {
    }

    /**
     * Constructs a new Array object with an array content. Allowed value types are List, Date,
     * Map, Number, null, String, Array, Blob, and Dictionary. The List and Map must contain
     * only the above types.
     *
     * @param array the array object.
     */
    public Array(List<Object> array) {
        this();
        set(array);
    }

    // Call from native method
    Array(MValue mv, MCollection parent) {
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
     * @param array the array
     * @return this Array instance
     */
    @Override
    public Array set(List<Object> array) {
        _array.clear();
        for (Object obj : array)
            _array.append(CBLFleece.toCBLObject(obj));
        return this;
    }

    /**
     * Set an object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the object
     * @return this Array instance
     */
    @Override
    public Array setObject(int index, Object value) {
        rangeCheck(index);
        if (CBLFleece.valueWouldChange(value, _array.get(index), _array))
            _array.set(index, CBLFleece.toCBLObject(value));
        return this;
    }

    @Override
    public Array setString(int index, String value) {
        return setObject(index, value);
    }

    @Override
    public Array setNumber(int index, Number value) {
        return setObject(index, value);
    }

    @Override
    public Array setInt(int index, int value) {
        return setObject(index, value);
    }

    @Override
    public Array setLong(int index, long value) {
        return setObject(index, value);
    }

    @Override
    public Array setFloat(int index, float value) {
        return setObject(index, value);
    }

    @Override
    public Array setDouble(int index, double value) {
        return setObject(index, value);
    }

    @Override
    public Array setBoolean(int index, boolean value) {
        return setObject(index, value);
    }

    @Override
    public Array setBlob(int index, Blob value) {
        return setObject(index, value);
    }

    @Override
    public Array setDate(int index, Date value) {
        return setObject(index, value);
    }

    @Override
    public Array setArray(int index, Array value) {
        return setObject(index, value);
    }

    @Override
    public Array setDictionary(int index, Dictionary value) {
        return setObject(index, value);
    }

    /**
     * Adds an object to the end of the array.
     *
     * @param value the object
     * @return this Array instance
     */
    @Override
    public Array addObject(Object value) {
        _array.append(CBLFleece.toCBLObject(value));
        return this;
    }

    @Override
    public Array addString(String value) {
        return addObject(value);
    }

    @Override
    public Array addNumber(Number value) {
        return addObject(value);
    }

    @Override
    public Array addInt(int value) {
        return addObject(value);
    }

    @Override
    public Array addLong(long value) {
        return addObject(value);
    }

    @Override
    public Array addFloat(float value) {
        return addObject(value);
    }

    @Override
    public Array addDouble(double value) {
        return addObject(value);
    }

    @Override
    public Array addBoolean(boolean value) {
        return addObject(value);
    }

    @Override
    public Array addBlob(Blob value) {
        return addObject(value);
    }

    @Override
    public Array addDate(Date value) {
        return addObject(value);
    }

    @Override
    public Array addArray(Array value) {
        return addObject(value);
    }

    @Override
    public Array addDictionary(Dictionary value) {
        return addObject(value);
    }

    /**
     * Inserts an object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the object
     * @return this Array instance
     */
    @Override
    public Array insertObject(int index, Object value) {
        rangeCheck(index);
        _array.insert(index, CBLFleece.toCBLObject(value));
        return this;
    }

    @Override
    public Array insertString(int index, String value) {
        return insertObject(index, value);
    }

    @Override
    public Array insertNumber(int index, Number value) {
        return insertObject(index, value);
    }

    @Override
    public Array insertInt(int index, int value) {
        return insertObject(index, value);
    }

    @Override
    public Array insertLong(int index, long value) {
        return insertObject(index, value);
    }

    @Override
    public Array insertFloat(int index, float value) {
        return insertObject(index, value);
    }

    @Override
    public Array insertDouble(int index, double value) {
        return insertObject(index, value);
    }

    @Override
    public Array insertBoolean(int index, boolean value) {
        return insertObject(index, value);
    }

    @Override
    public Array insertBlob(int index, Blob value) {
        return insertObject(index, value);
    }

    @Override
    public Array insertDate(int index, Date value) {
        return insertObject(index, value);
    }

    @Override
    public Array insertArray(int index, Array value) {
        return insertObject(index, value);
    }

    @Override
    public Array insertDictionary(int index, Dictionary value) {
        return insertObject(index, value);
    }

    /**
     * Removes the object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return this Array instance
     */
    @Override
    public Array remove(int index) {
        rangeCheck(index);
        _array.remove(index);
        return this;
    }

    /**
     * Gets a Array at the given index. Return null if the value is not an array.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Array object.
     */
    @Override
    public Array getArray(int index) {
        rangeCheck(index);
        Object obj = _get(_array, index).asNative(_array);
        return obj instanceof Array ? (Array) obj : null;
    }

    /**
     * Gets a Dictionary at the given index. Return null if the value is not an dictionary.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Dictionary object.
     */
    @Override
    public Dictionary getDictionary(int index) {
        rangeCheck(index);
        Object obj = _get(_array, index).asNative(_array);
        return obj instanceof Dictionary ? (Dictionary) obj : null;
    }
}
