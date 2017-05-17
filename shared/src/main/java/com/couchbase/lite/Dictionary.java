package com.couchbase.lite;

import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.fleece.FLEncoder;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dictionary provides access to dictionary data.
 */
public class Dictionary extends ReadOnlyDictionary implements DictionaryInterface, ObjectChangeListener, FleeceEncodable {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Map<String, Object> map = null;
    private Map<ObjectChangeListener, Integer> changeListeners = new HashMap<>();
    private boolean changed = false;
    private List<String> keys = null; // dictionary key cache

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Initialize a new empty Dictionary object.
     */
    public Dictionary() {
        super(null);
    }

    /**
     * Initializes a new CBLDictionary object with dictionary content. Allowed value types are List,
     * Date, Map, Number, null, String, Array, Blob, and Dictionary. The List and Map must contain
     * only the above types.
     *
     * @param dictionary the dictionary object.
     */
    public Dictionary(Map<String, Object> dictionary) {
        super(null);
        set(dictionary);
    }

    /* package */ Dictionary(CBLFLDict data) {
        super(data);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Set a dictionary as a content. Allowed value types are List, Date, Map, Number, null, String,
     * Array, Blob, and Dictionary. The List and Map must contain only the above types.
     * Setting the new dictionary content will replace the current data including the existing Array
     * and Dictionary objects.
     *
     * @param dictionary the dictionary object.
     * @return this Dictionary instance
     */
    @Override
    public Dictionary set(Map<String, Object> dictionary) {
        // Detach all objects that we are listening to for changes:
        detachChildChangeListeners();

        Map<String, Object> result = new HashMap<>();
        if (dictionary != null) {
            for (Map.Entry<String, Object> entry : dictionary.entrySet()) {
                result.put(entry.getKey(), CBLData.convert(entry.getValue(), this));
            }
        }

        // Marked the key as removed by setting the value to kRemovedValue:
        // TODO

        map = result;

        setChanged();
        return this;
    }

    /**
     * Set an object value by key. Allowed value types are List, Date, Map, Number, null, String,
     * Array, Blob, and Dictionary. The List and Map must contain only the above types.
     * An Date object will be converted to an ISO-8601 format string.
     *
     * @param key   the key.
     * @param value the object value.
     * @return this Dictionary instance
     */
    @Override
    public Dictionary set(String key, Object value) {
        Object oldValue = getObject(key);
        if ((value != null && !value.equals(oldValue)) || value == null) {
            value = CBLData.convert(value, this);
            detachChangeListenerForObject(oldValue);
            set(key, value, true);
            keys = null; // Reset key cache
        }
        return this;
    }

    /**
     * Removes the mapping for a key from this Dictionary
     *
     * @param key the key.
     * @return this Dictionary instance
     */
    @Override
    public Dictionary remove(String key) {
        // TODO
        // https://github.com/couchbase/couchbase-lite-android/issues/1157
        return this;
    }

    /**
     * Get a property's value as a Array, which is a mapping object of an array value.
     * Returns null if the property doesn't exists, or its value is not an array.
     *
     * @param key the key.
     * @return the Array object.
     */
    @Override
    public Array getArray(String key) {
        try {
            return (Array) getObject(key);
        } catch (ClassCastException ex) {
            return null;
        }
    }

    /**
     * Get a property's value as a Dictionary, which is a mapping object of an dictionary value.
     * Returns null if the property doesn't exists, or its value is not an dictionary.
     *
     * @param key the key.
     * @return the Dictionary object or null if the key doesn't exist.
     */
    @Override
    public Dictionary getDictionary(String key) {
        try {
            return (Dictionary) getObject(key);
        } catch (ClassCastException ex) {
            return null;
        }
    }

    //---------------------------------------------
    // API - overridden from ReadOnlyDictionary
    //---------------------------------------------

    /**
     * Gets a number of the entries in the dictionary.
     *
     * @return
     */
    @Override
    public long count() {
        long count = map != null ? map.size() : 0;
        if (count == 0)
            return super.count();

        for (String key : super.allKeys()) {
            if (!map.containsKey(key))
                count++;
        }

        for (Object value : map.values()) {
            //TODO: kCBLRemovedValue
        }

        return count;
    }

    /**
     * Gets a property's value as an object. The object types are Blob, Array,
     * Dictionary, Number, or String based on the underlying data type; or nil if the
     * property value is null or the property doesn't exist.
     *
     * @param key the key.
     * @return the object value or nil.
     */
    @Override
    public Object getObject(String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null) {
            value = super.getObject(key);
            if (value instanceof ReadOnlyDictionary) {
                value = CBLData.convert(value, this);
                set(key, value, false);
            } else if (value instanceof ReadOnlyArray) {
                value = CBLData.convert(value, this);
                set(key, value, false);
            }
        }
        // TODO: kCBLRemovedValue
        return value;
    }

    /**
     * Gets a property's value as a String. Returns null if the value doesn't exist, or its value is not a String.
     *
     * @param key the key
     * @return the String or null.
     */
    @Override
    public String getString(String key) {
        try {
            return (String) getObject(key);
        } catch (ClassCastException ex) {
            return null;
        }
    }

    /**
     * Gets a property's value as a Number. Returns null if the value doesn't exist, or its value is not a Number.
     *
     * @param key the key
     * @return the Number or nil.
     */
    @Override
    public Number getNumber(String key) {
        try {
            return (Number) getObject(key);
        } catch (ClassCastException ex) {
            return null;
        }
    }

    /**
     * Gets a property's value as an int.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the int value.
     */
    @Override
    public int getInt(String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null)
            return super.getInt(key);
        else {
            try {
                return ((Number) value).intValue();
            } catch (ClassCastException ex) {
                return 0;
            }
        }
    }

    /**
     * Gets a property's value as an long.
     * Floating point values will be rounded. The value `true` is returned as 1, `false` as 0.
     * Returns 0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the long value.
     */
    @Override
    public long getLong(String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null)
            return super.getLong(key);
        else {
            try {
                return ((Number) value).longValue();
            } catch (ClassCastException ex) {
                return 0L;
            }
        }
    }

    /**
     * Gets a property's value as an float.
     * Integers will be converted to float. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the value doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the float value.
     */
    @Override
    public float getFloat(String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null)
            return super.getFloat(key);
        else {
            try {
                return ((Number) value).floatValue();
            } catch (ClassCastException ex) {
                return 0.0F;
            }
        }
    }

    /**
     * Gets a property's value as an double.
     * Integers will be converted to double. The value `true` is returned as 1.0, `false` as 0.0.
     * Returns 0.0 if the property doesn't exist or does not have a numeric value.
     *
     * @param key the key
     * @return the double value.
     */
    @Override
    public double getDouble(String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null)
            return super.getDouble(key);
        else {
            try {
                return ((Number) value).doubleValue();
            } catch (ClassCastException ex) {
                return 0.0;
            }
        }
    }

    /**
     * Gets a property's value as a boolean. Returns true if the value exists, and is either `true`
     * or a nonzero number.
     *
     * @param key the key
     * @return the boolean value.
     */
    @Override
    public boolean getBoolean(String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null)
            return super.getBoolean(key);
        else {
            // TODO: kCBLRemovedValue
            return CBLData.toBoolean(value);

        }
    }

    /**
     * Gets a property's value as a Blob.
     * Returns null if the value doesn't exist, or its value is not a Blob.
     *
     * @param key the key
     * @return the Blob value or null.
     */
    @Override
    public Blob getBlob(String key) {
        try {
            return (Blob) getObject(key);
        } catch (ClassCastException ex) {
            return null;
        }
    }

    /**
     * Gets a property's value as a Date.
     * JSON does not directly support dates, so the actual property value must be a string, which is
     * then parsed according to the ISO-8601 date format (the default used in JSON.)
     * Returns null if the value doesn't exist, is not a string, or is not parseable as a date.
     * NOTE: This is not a generic date parser! It only recognizes the ISO-8601 format, with or
     * without milliseconds.
     *
     * @param key the key
     * @return the Date value or null.
     */
    @Override
    public Date getDate(String key) {
        return DateUtils.fromJson(getString(key));
    }

    /**
     * Gets content of the current object as an Map. The values contained in the returned
     * Map object are all JSON based values.
     *
     * @return the Map object representing the content of the current object in the JSON format.
     */
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> result = map != null ? new HashMap<>(map) : new HashMap<String, Object>();

        // Backing data:
        Map<String, Object> backingData = super.toMap();
        if (backingData != null) {
            for (Map.Entry<String, Object> entry : backingData.entrySet()) {
                if (!result.containsKey(entry.getKey()))
                    result.put(entry.getKey(), entry.getValue());
            }
        }

        for (String key : result.keySet()) {
            Object value = result.get(key);
            //TODO: kCBLRemovedValue
            if (value instanceof ReadOnlyDictionary)
                result.put(key, ((ReadOnlyDictionary) value).toMap());
            else if (value instanceof ReadOnlyArray)
                result.put(key, ((ReadOnlyArray) value).toList());

        }

        return result;
    }

    /**
     * Tests whether a property exists or not.
     * This can be less expensive than getObject(String), because it does not have to allocate an Object for the property value.
     *
     * @param key the key
     * @return the boolean value representing whether a property exists or not.
     */
    @Override
    public boolean contains(String key) {
        Object value = map.get(key);
        if (value == null)
            return super.contains(key);
            //TODO
        else
            return true;
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    // TODO: Once Iterable is implemented, change back to package level accessor
    public List<String> allKeys() {
        if (keys == null) {
            List<String> result = map != null ? new ArrayList<>(map.keySet()) : new ArrayList<String>();
            for (String key : super.allKeys()) {
                if (!result.contains(key))
                    result.add(key);
            }

            //TODO -  kCBLRemovedValue

            keys = result;
        }
        return keys;
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------
    /* package */boolean isChanged() {
        return changed;
    }

    /**
     * Check if contains unsaved property?
     *
     * @return
     */
    /* package */boolean isEmpty() {

        /*
         -- Remove name field: data structure --
         fleece ["name": "pasin", "address": "1 street"]
         map: ["name": kCBLRemovedValue]
         */

        // Note: RemovedValue can not be check
        if (map == null || map.size() == 0)
            return super.count() == 0;

        // something in fleece, but not in map
        for (String key : allKeys()) {
            if (map != null && !map.containsKey(key))
                return false;
        }

        // TODO
        if (map != null) {
            for (Object value : map.values()) {
//            if(!kCBLRemovedValue.equals(value)){
                return false;
//            }
            }
        }

        return true;
    }

    // #pragma mark - CHANGE LISTENING

    @Override
    public void objectDidChange(Object object) {
        setChanged();
    }

    // #pragma mark - FLEECE ENCODABLE

    // FleeceEncodable implementation
    /* package */
    public void fleeceEncode(FLEncoder encoder, Database database) throws CouchbaseLiteException {
        List<String> keys = allKeys();
        encoder.beginDict(keys.size());
        for (String key : keys) {
            Object value = getObject(key);
            //if(value != kCBLRemovedValue){
            encoder.writeKey(key);
            if (value instanceof FleeceEncodable)
                ((FleeceEncodable) value).fleeceEncode(encoder, database);
            else
                encoder.writeValue(value);
            //}
        }
        encoder.endDict();
    }

    /*package*/ void addChangeListener(ObjectChangeListener listener) {
        int count = changeListeners.containsKey(listener) ? changeListeners.get(listener) : 0;
        changeListeners.put(listener, count + 1);
    }

    /*package*/ void removeChangeListener(ObjectChangeListener listener) {
        int count = changeListeners.containsKey(listener) ? changeListeners.get(listener) : 0;
        if (count > 1)
            changeListeners.put(listener, count - 1);
        else
            changeListeners.remove(listener);
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------
    private void set(String key, Object value, boolean isChange) {
        if (map == null)
            map = new HashMap<>();
        map.put(key, value);
        if (isChange)
            setChanged();
    }

    //  CHANGE
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
        if (map == null) return;

        for (String key : map.keySet()) {
            detachChangeListenerForObject(map.get(key));
        }
    }
}
