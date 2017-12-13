package com.couchbase.lite;

import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.fleece.Encoder;
import com.couchbase.litecore.fleece.FLEncodable;
import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.MArray;
import com.couchbase.litecore.fleece.MCollection;
import com.couchbase.litecore.fleece.MValue;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * ReadOnlyArray provides readonly access to array data.
 */
public class Array implements ArrayInterface, FLEncodable, Iterable<Object> {

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    protected MArray _array; // pointer to MArray<JNIRef> in native

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    Array() {
        _array = new MArray();
    }

    Array(MValue mv, MCollection parent) {
        this();
        _array.initInSlot(mv, parent);
    }

    // to crete mutable copy
    Array(MArray mArray, boolean isMutable) {
        this();
        _array.initAsCopyOf(mArray, isMutable);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Gets a number of the items in the array.
     *
     * @return
     */
    @Override
    public int count() {
        return (int) _array.count();
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
    public Object getValue(int index) {
        rangeCheck(index);
        return _get(_array, index).asNative(_array);
    }

    /**
     * Gets value at the given index as a String. Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the String or null.
     */
    @Override
    public String getString(int index) {
        Object obj = _get(_array, index).asNative(_array);
        return obj instanceof String ? (String) obj : null;
    }

    /**
     * Gets value at the given index as a Number. Returns null if the value doesn't exist, or its value is not a Number.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Number or nil.
     */
    @Override
    public Number getNumber(int index) {
        return CBLConverter.getNumber(_get(_array, index).asNative(_array));
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
        return CBLConverter.asInteger(_get(_array, index), _array);
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
        return CBLConverter.asLong(_get(_array, index), _array);
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
        return CBLConverter.asFloat(_get(_array, index), _array);
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
        return CBLConverter.asDouble(_get(_array, index), _array);
    }

    /**
     * Gets value at the given index as a boolean.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the boolean value.
     */
    @Override
    public boolean getBoolean(int index) {
        //return CBLConverter.asBool(_get(_array, index), _array);
        Object value = _get(_array, index).asNative(_array);
        if (value == null) return false;
        else if (value instanceof Boolean) return ((Boolean) value).booleanValue();
        else if (value instanceof Number) return ((Number) value).intValue() != 0;
        else return true;
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
        return (Blob) _get(_array, index).asNative(_array);
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
     * Gets a Array at the given index. Return null if the value is not an array.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Array object.
     */
    @Override
    public Array getArray(int index) {
        //return (Array) _get(_array, index).asNative(_array);

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
        //return (Dictionary) _get(_array, index).asNative(_array);

        rangeCheck(index);
        Object obj = _get(_array, index).asNative(_array);
        return obj instanceof Dictionary ? (Dictionary) obj : null;
    }

    /**
     * Gets content of the current object as an List. The values contained in the returned
     * List object are all JSON based values.
     *
     * @return the List object representing the content of the current object in the JSON format.
     */
    @Override
    public List<Object> toList() {
        int count = (int) _array.count();
        List<Object> result = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
            result.add(CBLFleece.toObject(_get(_array, index).asNative(_array)));
        return result;
    }

    /**
     * Return a mutable copy of the array
     *
     * @return the MutableArray instance
     */
    public MutableArray toMutable() {
        return new MutableArray(_array, true);
    }

    //-------------------------------------------------------------------------
    // Implementation of FLEncodable
    //-------------------------------------------------------------------------

    @Override
    public void encodeTo(FLEncoder enc) {
        Encoder encoder = new Encoder(enc);
        _array.encodeTo(encoder);
        encoder.release();
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------

    private class ArrayIterator implements Iterator<Object> {
        private int index = 0;
        private int count = count();

        @Override
        public boolean hasNext() {
            return index < count;
        }

        @Override
        public Object next() {
            return getValue(index++);
        }
    }

    @Override
    public Iterator<Object> iterator() {
        return new ArrayIterator();
    }

    //---------------------------------------------
    // Override
    //---------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Array)) return false;

        Array a = (Array) o;
        Iterator<Object> itr1 = iterator();
        Iterator<Object> itr2 = a.iterator();
        while (itr1.hasNext() && itr2.hasNext()) {
            Object o1 = itr1.next();
            Object o2 = itr2.next();
            if (!(o1 == null ? o2 == null : o1.equals(o2)))
                return false;
        }
        return !(itr1.hasNext() || itr2.hasNext());
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (Object o : this)
            h = 31 * h + (o == null ? 0 : o.hashCode());
        return h;
    }


    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    protected static MValue _get(MArray array, int index) {
        return array.get(index);
    }


    protected void rangeCheck(int index) {
        if (index > count() || index < 0)
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }


    protected String outOfBoundsMsg(int index) {
        return "Index: " + index + ", Size: " + count();
    }
}
