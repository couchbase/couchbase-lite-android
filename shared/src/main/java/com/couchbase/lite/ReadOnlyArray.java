package com.couchbase.lite;

import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.SharedKeys;
import com.couchbase.litecore.fleece.FLArray;
import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.FLValue;

import java.util.Date;
import java.util.Iterator;
import java.util.List;

/**
 * ReadOnlyArray provides readonly access to array data.
 */
public class ReadOnlyArray implements ReadOnlyArrayInterface, FleeceEncodable, Iterable<Object> {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private CBLFLArray data;
    private FLArray flArray;
    private SharedKeys sharedKeys;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    ReadOnlyArray(CBLFLArray data) {
        setData(data);
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
        return flArray != null ? (int) flArray.count() : 0;
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
        return fleeceValueToObject(index);
    }

    /**
     * Gets value at the given index as a String. Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the String or null.
     */
    @Override
    public String getString(int index) {
        return (String) fleeceValueToObject(index);
    }

    /**
     * Gets value at the given index as a Number. Returns null if the value doesn't exist, or its value is not a Number.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Number or nil.
     */
    @Override
    public Number getNumber(int index) {
        return (Number) fleeceValueToObject(index);
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
        return (int) fleeceValue(index).asInt();
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
        // asLong
        return fleeceValue(index).asInt();
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
        return fleeceValue(index).asFloat();
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
        return fleeceValue(index).asDouble();
    }

    /**
     * Gets value at the given index as a boolean.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the boolean value.
     */
    @Override
    public boolean getBoolean(int index) {
        return fleeceValue(index).asBool();
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
        return (Blob) fleeceValueToObject(index);
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
    public ReadOnlyArray getArray(int index) {
        return (ReadOnlyArray) fleeceValueToObject(index);
    }

    /**
     * Gets a Dictionary at the given index. Return null if the value is not an dictionary.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return the Dictionary object.
     */
    @Override
    public ReadOnlyDictionary getDictionary(int index) {
        return (ReadOnlyDictionary) fleeceValueToObject(index);
    }

    /**
     * Gets content of the current object as an List. The values contained in the returned
     * List object are all JSON based values.
     *
     * @return the List object representing the content of the current object in the JSON format.
     */
    @Override
    public List<Object> toList() {
        //TODO
        throw new UnsupportedOperationException("Work in Progress");
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
            return getObject(index++);
        }
    }

    @Override
    public Iterator<Object> iterator() {
        return new ArrayIterator();
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    protected CBLFLArray getData() {
        return data;
    }

    protected void setData(CBLFLArray data) {
        this.data = data;
        this.flArray = null;
        this.sharedKeys = null;
        if (data != null) {
            this.flArray = data.getFLArray();
            if (data.getDatabase() != null)
                this.sharedKeys = data.getDatabase().getSharedKeys();
        }
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    // FleeceEncodable implementation
    @Override
    public void fleeceEncode(FLEncoder encoder, Database database) {
        encoder.writeValue(flArray);
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    // #pragma mark - FLEECE

    private FLValue fleeceValue(int index) {
        return flArray.get(index);
    }

    private Object fleeceValueToObject(int index) {
        FLValue value = fleeceValue(index);
        if (value != null)
            return CBLData.fleeceValueToObject(value, data.getC4doc(), data.getDatabase());
        else
            return null;
    }
}
