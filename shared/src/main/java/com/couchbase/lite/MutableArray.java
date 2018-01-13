package com.couchbase.lite;

import com.couchbase.litecore.fleece.MArray;
import com.couchbase.litecore.fleece.MCollection;
import com.couchbase.litecore.fleece.MValue;

import java.util.Date;
import java.util.List;

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
    @Override
    public MutableArray setData(List<Object> data) {
        _array.clear();
        for (Object obj : data)
            _array.append(Fleece.toCBLObject(obj));
        return this;
    }

    /**
     * Set an object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the object
     * @return The self object
     */
    @Override
    public MutableArray setValue(int index, Object value) {
        if (Fleece.valueWouldChange(value, _array.get(index), _array))
            if (!_array.set(index, Fleece.toCBLObject(value)))
                throwRangeException(index);
        return this;
    }

    /**
     * Sets an String object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the String object
     * @return The self object
     */
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
    @Override
    public MutableArray addValue(Object value) {
        _array.append(Fleece.toCBLObject(value));
        return this;
    }

    /**
     * Adds a String object to the end of the array.
     *
     * @param value the String object
     * @return The self object
     */
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
    @Override
    public MutableArray insertValue(int index, Object value) {
        if (!_array.insert(index, Fleece.toCBLObject(value)))
            throwRangeException(index);
        return this;
    }

    /**
     * Inserts a String object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the String object
     * @return The self object
     */
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
    @Override
    public MutableArray remove(int index) {
        if (!_array.remove(index))
            throwRangeException(index);
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
