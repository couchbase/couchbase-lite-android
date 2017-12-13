package com.couchbase.lite;

import com.couchbase.litecore.fleece.MArray;
import com.couchbase.litecore.fleece.MCollection;
import com.couchbase.litecore.fleece.MValue;

import java.util.Date;
import java.util.List;

/**
 * Array provides access to array data.
 */
public class MutableArray extends Array implements MutableArrayInterface {
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
     * @return this Array instance
     */
    @Override
    public MutableArray setData(List<Object> data) {
        _array.clear();
        for (Object obj : data)
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
    public MutableArray setValue(int index, Object value) {
        rangeCheck(index);
        if (CBLFleece.valueWouldChange(value, _array.get(index), _array))
            _array.set(index, CBLFleece.toCBLObject(value));
        return this;
    }

    @Override
    public MutableArray setString(int index, String value) {
        return setValue(index, value);
    }

    @Override
    public MutableArray setNumber(int index, Number value) {
        return setValue(index, value);
    }

    @Override
    public MutableArray setInt(int index, int value) {
        return setValue(index, value);
    }

    @Override
    public MutableArray setLong(int index, long value) {
        return setValue(index, value);
    }

    @Override
    public MutableArray setFloat(int index, float value) {
        return setValue(index, value);
    }

    @Override
    public MutableArray setDouble(int index, double value) {
        return setValue(index, value);
    }

    @Override
    public MutableArray setBoolean(int index, boolean value) {
        return setValue(index, value);
    }

    @Override
    public MutableArray setBlob(int index, Blob value) {
        return setValue(index, value);
    }

    @Override
    public MutableArray setDate(int index, Date value) {
        return setValue(index, value);
    }

    @Override
    public MutableArray setArray(int index, MutableArray value) {
        return setValue(index, value);
    }

    @Override
    public MutableArray setDictionary(int index, MutableDictionary value) {
        return setValue(index, value);
    }

    /**
     * Adds an object to the end of the array.
     *
     * @param value the object
     * @return this Array instance
     */
    @Override
    public MutableArray addValue(Object value) {
        _array.append(CBLFleece.toCBLObject(value));
        return this;
    }

    @Override
    public MutableArray addString(String value) {
        return addValue(value);
    }

    @Override
    public MutableArray addNumber(Number value) {
        return addValue(value);
    }

    @Override
    public MutableArray addInt(int value) {
        return addValue(value);
    }

    @Override
    public MutableArray addLong(long value) {
        return addValue(value);
    }

    @Override
    public MutableArray addFloat(float value) {
        return addValue(value);
    }

    @Override
    public MutableArray addDouble(double value) {
        return addValue(value);
    }

    @Override
    public MutableArray addBoolean(boolean value) {
        return addValue(value);
    }

    @Override
    public MutableArray addBlob(Blob value) {
        return addValue(value);
    }

    @Override
    public MutableArray addDate(Date value) {
        return addValue(value);
    }

    @Override
    public MutableArray addArray(MutableArray value) {
        return addValue(value);
    }

    @Override
    public MutableArray addDictionary(MutableDictionary value) {
        return addValue(value);
    }

    /**
     * Inserts an object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the object
     * @return this Array instance
     */
    @Override
    public MutableArray insertValue(int index, Object value) {
        rangeCheck(index);
        _array.insert(index, CBLFleece.toCBLObject(value));
        return this;
    }

    @Override
    public MutableArray insertString(int index, String value) {
        return insertValue(index, value);
    }

    @Override
    public MutableArray insertNumber(int index, Number value) {
        return insertValue(index, value);
    }

    @Override
    public MutableArray insertInt(int index, int value) {
        return insertValue(index, value);
    }

    @Override
    public MutableArray insertLong(int index, long value) {
        return insertValue(index, value);
    }

    @Override
    public MutableArray insertFloat(int index, float value) {
        return insertValue(index, value);
    }

    @Override
    public MutableArray insertDouble(int index, double value) {
        return insertValue(index, value);
    }

    @Override
    public MutableArray insertBoolean(int index, boolean value) {
        return insertValue(index, value);
    }

    @Override
    public MutableArray insertBlob(int index, Blob value) {
        return insertValue(index, value);
    }

    @Override
    public MutableArray insertDate(int index, Date value) {
        return insertValue(index, value);
    }

    @Override
    public MutableArray insertArray(int index, MutableArray value) {
        return insertValue(index, value);
    }

    @Override
    public MutableArray insertDictionary(int index, MutableDictionary value) {
        return insertValue(index, value);
    }

    /**
     * Removes the object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return this Array instance
     */
    @Override
    public MutableArray remove(int index) {
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
    public MutableArray getArray(int index) {
        rangeCheck(index);
        Object obj = _get(_array, index).asNative(_array);
        return obj instanceof MutableArray ? (MutableArray) obj : null;
    }

    /**
     * Gets a Dictionary at the given index. Return null if the value is not an dictionary.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Dictionary object.
     */
    @Override
    public MutableDictionary getDictionary(int index) {
        rangeCheck(index);
        Object obj = _get(_array, index).asNative(_array);
        return obj instanceof MutableDictionary ? (MutableDictionary) obj : null;
    }
}
