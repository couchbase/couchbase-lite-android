package com.couchbase.lite;

import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.fleece.FLEncoder;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.couchbase.lite.internal.support.ClassUtils.cast;

/**
 * Array provides access to array data.
 */
public class Array extends ReadOnlyArray implements ArrayInterface, ObjectChangeListener, FleeceEncodable {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private List<Object> list = null;
    Map<ObjectChangeListener, Integer> changeListeners = new HashMap<>();
    private boolean changed = false;

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

    /* package */ Array(CBLFLArray data) {
        super(data);
        list = new ArrayList<>();
        loadBackingFleeceData();
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
        // Detach all objects that we are listening to for changes:
        detachChildChangeListeners();

        List<Object> result = new ArrayList<>();
        for (Object value : list) {
            result.add(CBLData.convert(value, this));
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
    public Array set(int index, Object value) {
        Object oldValue = getObject(index);
        //if ((value != null && !value.equals(oldValue)) || (value == null && oldValue != null)) {
        if ((value != null && !value.equals(oldValue)) || value == null) {
            value = CBLData.convert(value, this);
            detachChangeListenerForObject(oldValue);
            set(index, value, true);
        }
        return this;
    }

    /**
     * Adds an object to the end of the array.
     *
     * @param value the object
     * @return this Array instance
     */
    @Override
    public Array add(Object value) {
        list.add(CBLData.convert(value, this));
        setChanged();
        return this;
    }

    /**
     * Inserts an object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @param value the object
     * @return this Array instance
     */
    @Override
    public Array insert(int index, Object value) {
        list.add(index, CBLData.convert(value, this));
        setChanged();
        return this;
    }

    /**
     * Removes the object at the given index.
     *
     * @param index the index. This value must not exceed the bounds of the array.
     * @return this Array instance
     */
    @Override
    public Array remove(int index) {
        Object value = list.get(index);
        detachChangeListenerForObject(value);
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
        return list != null ? list.size() : 0;
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
        return cast(getObject(index), Number.class);
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

    @Override
    public Iterator<Object> iterator() {
        return null;
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    //---------------------------------------------
    // Package level access
    //---------------------------------------------
    /*package*/ void addChangeListener(ObjectChangeListener listener) {
        if (listener == null)
            System.out.println("listenr is null");
        int count = changeListeners.containsKey(listener) ? changeListeners.get(listener) : 0;
        changeListeners.put(listener, count + 1);
    }

    /*package*/ void removeChangeListener(ObjectChangeListener listener) {
        if (listener == null)
            System.out.println("listenr is null");
        int count = changeListeners.containsKey(listener) ? changeListeners.get(listener) : 0;
        if (count > 1)
            changeListeners.put(listener, count - 1);
        else
            changeListeners.remove(listener);
    }

    // #pragma mark - CHANGE LISTENING

    @Override
    public void objectDidChange(Object object) {
        setChanged();
    }

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
            list = new ArrayList<>();
        list.set(index, value);
        if (isChange)
            setChanged();
    }

    private void setChanged() {
        if (!changed) {
            changed = true;
            notifyChangeListeners();
        }
    }

    private void notifyChangeListeners() {
        for (ObjectChangeListener listener : changeListeners.keySet())
            listener.objectDidChange(this);
    }

    private void detachChangeListenerForObject(Object object) {
        if (object instanceof Dictionary) {
            ((Dictionary) object).removeChangeListener(this);
        } else if (object instanceof Array) {
            ((Array) object).removeChangeListener(this);
        }
    }

    private void detachChildChangeListeners() {
        if (list == null) return;

        for (Object object : list) {
            detachChangeListenerForObject(object);
        }
    }

    private void loadBackingFleeceData() {
        int count = super.count();
        for (int i = 0; i < count; i++) {
            Object value = super.getObject(i);
            list.add(CBLData.convert(value, this));
        }
    }
}
