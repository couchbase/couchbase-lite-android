package com.couchbase.lite;

import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.fleece.FLEncoder;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Dictionary extends ReadOnlyDictionary implements DictionaryInterface, ObjectChangeListener, FleeceEncodable {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Map<String, Object> map = null;
    Map<ObjectChangeListener, Integer> changeListeners = new HashMap<>();
    private boolean changed = false;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    public Dictionary() {
        super(null);
    }


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

    @Override
    public Dictionary set(String key, Object value) {
        Object oldValue = getObject(key);
        if (!value.equals(oldValue)) {
            value = CBLData.convert(value, null);
            detachChangeListenerForObject(oldValue);
            set(key, value, true);
        }
        return this;
    }

    @Override
    public Dictionary remove(String key) {
        return this;
    }

    @Override
    public Array getArray(String key) {
        return (Array) getObject(key);
    }

    @Override
    public Dictionary getDictionary(String key) {
        return (Dictionary) getObject(key);
    }

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

    //---------------------------------------------
    // API - overridden from ReadOnlyDictionary
    //---------------------------------------------

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

    @Override
    public String getString(String key) {
        return (String) getObject(key);
    }

    @Override
    public Number getNumber(String key) {
        return (Number) getObject(key);
    }

    @Override
    public int getInt(String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null)
            return super.getInt(key);
        else
            return ((Number) value).intValue();
    }

    @Override
    public long getLong(String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null)
            return super.getLong(key);
        else
            return ((Number) value).longValue();
    }

    @Override
    public float getFloat(String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null)
            return super.getFloat(key);
        else
            return ((Number) value).floatValue();
    }

    @Override
    public double getDouble(String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null)
            return super.getDouble(key);
        else
            return ((Number) value).doubleValue();
    }

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

    @Override
    public Blob getBlob(String key) {
        return (Blob) getObject(key);
    }

    @Override
    public Date getDate(String key) {
        return DateUtils.fromJson(getString(key));
    }

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
        List<String> result = map != null ? new ArrayList<String>(map.keySet()) : new ArrayList<String>();
        for (String key : super.allKeys()) {
            if (!result.contains(key))
                result.add(key);
        }

        //TODO -  kCBLRemovedValue

        return result;
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
