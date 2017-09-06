package com.couchbase.lite;

import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.fleece.FLEncoder;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import static com.couchbase.lite.internal.support.ClassUtils.cast;

/**
 * Array provides access to array data.
 */
public class Array extends ReadOnlyArray implements ArrayInterface, FleeceEncodable {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private List<Object> list = null;
    private boolean changed = false;
    private int changedCount = 0;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Constructs a new empty Array object.
     */
    public Array() {
        this((CBLFLArray) null);
    }

    /**
     * Constructs a new Array object with an array content. Allowed value types are List, Date,
     * Map, Number, null, String, Array, Blob, and Dictionary. The List and Map must contain
     * only the above types.
     *
     * @param array the array object.
     */
    public Array(List<Object> array) {
        this((CBLFLArray) null);
        set(array);
    }

    Array(CBLFLArray data) {
        super(data);
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
     * @param list the array
     * @return this Array instance
     */
    @Override
    public Array set(List<Object> list) {
        List<Object> result = new ArrayList<>();
        for (Object value : list) {
            result.add(CBLData.convert(value));
        }
        this.list = result;
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
        Object oldValue = getObject(index);
        if ((value != null && !value.equals(oldValue)) || value == null) {
            value = CBLData.convert(value);
            set(index, value, true);
        }
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
        if (list == null)
            copyFleeceData();

        list.add(CBLData.convert(value));
        setChanged();
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
        if (list == null)
            copyFleeceData();

        list.add(index, CBLData.convert(value));
        setChanged();
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
        if (list == null)
            copyFleeceData();

        list.remove(index);
        setChanged();
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
        Object value = getObject(index);
        return (value instanceof Array) ? (Array) value : null;
    }

    /**
     * Gets a Dictionary at the given index. Return null if the value is not an dictionary.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Dictionary object.
     */
    @Override
    public Dictionary getDictionary(int index) {
        Object value = getObject(index);
        return (value instanceof Dictionary) ? (Dictionary) value : null;
    }

    //---------------------------------------------
    // API - overridden from ReadOnlyArray
    //---------------------------------------------

    /**
     * Gets a number of the items in the array.
     *
     * @return
     */
    @Override
    public int count() {
        return list == null ? super.count() : list.size();
    }

    /**
     * Gets value at the given index as an object. The object types are Blob,
     * Array, Dictionary, Number, or String based on the underlying
     * data type; or nil if the value is nil.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Object or null.
     */
    @Override
    public Object getObject(int index) {
        if (list == null) {
            Object value = super.getObject(index);
            if (value instanceof ReadOnlyArray || value instanceof ReadOnlyDictionary)
                copyFleeceData();
            else
                return value;
        }
        return list.get(index);
    }

    /**
     * Gets value at the given index as a String. Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the String or null.
     */
    @Override
    public String getString(int index) {
        return cast(getObject(index), String.class);
    }

    /**
     * Gets value at the given index as a Number. Returns null if the value doesn't exist, or its value is not a Number.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Number or nil.
     */
    @Override
    public Number getNumber(int index) {
        Object value = getObject(index);
        // special handling for Boolean
        if (value != null && value instanceof Boolean)
            return value == Boolean.TRUE ? Integer.valueOf(1) : Integer.valueOf(0);
        else
            return cast(value, Number.class);
    }

    /**
     * Gets value at the given index as an int.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the int value.
     */
    @Override
    public int getInt(int index) {
        Number num = getNumber(index);
        return num != null ? num.intValue() : 0;
    }

    /**
     * Gets value at the given index as an long.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the long value.
     */
    @Override
    public long getLong(int index) {
        Number num = getNumber(index);
        return num != null ? num.longValue() : 0;
    }

    /**
     * Gets value at the given index as an float.
     * Integers will be converted to float. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the value doesn't exist or does not have a numeric value.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the float value.
     */
    @Override
    public float getFloat(int index) {
        Number num = getNumber(index);
        return num != null ? num.floatValue() : 0;
    }

    /**
     * Gets value at the given index as an double.
     * Integers will be converted to double. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the property doesn't exist or does not have a numeric value.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the double value.
     */
    @Override
    public double getDouble(int index) {
        Number num = getNumber(index);
        return num != null ? num.doubleValue() : 0;
    }

    /**
     * Gets value at the given index as a boolean.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the boolean value.
     */
    @Override
    public boolean getBoolean(int index) {
        return CBLData.toBoolean(getObject(index));
    }

    /**
     * Gets value at the given index as a Blob.
     * Returns null if the value doesn't exist, or its value is not a Blob.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Blob value or null.
     */
    @Override
    public Blob getBlob(int index) {
        return (Blob) getObject(index);
    }

    /**
     * Gets value at the given index as a Date.
     * JSON does not directly support dates, so the actual property value must be a string, which is
     * then parsed according to the ISO-8601 date format (the default used in JSON.)
     * Returns null if the value doesn't exist, is not a string, or is not parseable as a date.
     * NOTE: This is not a generic date parser! It only recognizes the ISO-8601 format, with or
     * without milliseconds.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Date value or null.
     */
    @Override
    public Date getDate(int index) {
        return DateUtils.fromJson(getString(index));
    }

    /**
     * Gets content of the current object as an List. The values contained in the returned
     * List object are all JSON based values.
     *
     * @return the List object representing the content of the current object in the JSON format.
     */
    @Override
    public List<Object> toList() {
        if (list == null)
            copyFleeceData();

        List<Object> array = new ArrayList<>();
        if (list != null) {
            for (Object value : list) {
                if (value instanceof ReadOnlyDictionary)
                    value = ((ReadOnlyDictionary) value).toMap();
                else if (value instanceof ReadOnlyArray)
                    value = ((ReadOnlyArray) value).toList();
                array.add(value);
            }
        }
        return array;
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------
    class ArrayIterator implements Iterator<Object> {
        private Iterator<Object> internal;
        private int expectedChangedCount;

        public ArrayIterator() {
            this.internal = list == null ? Array.super.iterator() : list.iterator();
            this.expectedChangedCount = Array.this.changedCount;
        }

        @Override
        public boolean hasNext() {
            return internal.hasNext();
        }

        @Override
        public Object next() {
            if (expectedChangedCount != Array.this.changedCount)
                throw new ConcurrentModificationException();
            return internal.next();
        }
    }

    @Override
    public Iterator<Object> iterator() {
        return new ArrayIterator();
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    // FleeceEncodable implementation
    @Override
    public void fleeceEncode(FLEncoder encoder, Database database) {
        encoder.beginArray(count());
        for (int i = 0; i < count(); i++) {
            Object value = getObject(i);
            if (value instanceof FleeceEncodable)
                ((FleeceEncodable) value).fleeceEncode(encoder, database);
            else
                encoder.writeValue(value);
        }
        encoder.endArray();
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------
    private void set(int index, Object value, boolean isChange) {
        if (list == null)
            copyFleeceData();

        list.set(index, value);
        if (isChange)
            setChanged();
    }

    private void setChanged() {
        if (!changed)
            changed = true;
        changedCount++;
    }

    private void copyFleeceData() {
        int count = super.count();
        list = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            list.add(CBLData.convert(super.getObject(i)));
    }
}
