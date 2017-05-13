package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLEncoder;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Dictionary extends ReadOnlyDictionary implements DictionaryInterface, ObjectChangeListener,FleeceEncodable {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Map<String, Object> map = null;
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
        //TODO:
        return this;
    }

    @Override
    public Dictionary set(String key, Object value) {
        Object oldValue = getObject(key);
        if (!value.equals(oldValue)) {
            // TODO:
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
        long count = map.size();
        if(count == 0)
            return super.count();

        for(String key : super.allKeys()){
            if(!map.containsKey(key))
                count++;
        }

        for(Object value:map.values()){
            //TODO: kCBLRemovedValue
        }

        return count;
    }

    @Override
    public String getString(String key) {
        return super.getString(key);
    }

    @Override
    public Number getNumber(String key) {
        return super.getNumber(key);
    }

    @Override
    public int getInt(String key) {
        return super.getInt(key);
    }

    @Override
    public long getLong(String key) {
        return super.getLong(key);
    }

    @Override
    public float getFloat(String key) {
        return super.getFloat(key);
    }

    @Override
    public double getDouble(String key) {
        return super.getDouble(key);
    }

    @Override
    public boolean getBoolean(String key) {
        return super.getBoolean(key);
    }

    @Override
    public Blob getBlob(String key) {
        return super.getBlob(key);
    }

    @Override
    public Date getDate(String key) {
        return super.getDate(key);
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
            //TODO
        }

        return result;
    }

    @Override
    public boolean contains(String key) {
        return super.contains(key);
    }


    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    /* package */ List<String> allKeys() {
        List<String> result = map!=null?new ArrayList<String>(map.keySet()):new ArrayList<String>();
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
            if(value instanceof FleeceEncodable)
                ((FleeceEncodable)value).fleeceEncode(encoder, database);
            else
                encoder.writeValue(value);
            //}
        }
        encoder.endDict();
    }

    /*
    boolean write(FLEncoder encoder, Map map) {
        encoder.beginDict(map.size());
        Iterator keys = map.keySet().iterator();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            Object value = map.get(key);
//            if (value instanceof Blob)
//                store((Blob) value);
            encoder.writeKey(key);
            writeValueForObject(encoder, value);
        }
        return encoder.endDict();
    }
    private boolean writeValue(FLEncoder encoder, Object value) {
        if (value == null)
            return encoder.writeNull();
        else if (value instanceof Boolean)
            return encoder.writeBool((Boolean) value);
        else if (value instanceof Number) {
            if (value instanceof Integer || value instanceof Long)
                return encoder.writeInt(((Number) value).longValue());
            else if (value instanceof Double)
                return encoder.writeDouble(((Double) value).doubleValue());
            else
                return encoder.writeFloat(((Float) value).floatValue());
        } else if (value instanceof String)
            return encoder.writeString((String) value);
        else if (value instanceof byte[])
            return encoder.writeData((byte[]) value);
        else if (value instanceof List)
            return encoder.write((List) value);
        else if (value instanceof Map)
            return write(encoder, (Map) value);
//        else if (value instanceof Blob)
//            return writeValueForObject(encoder, value);
        return false;
    }



    boolean writeValueForObject(FLEncoder encoder, Object value) {
        if (value instanceof Blob)
            value = ((Blob) value).jsonRepresentation();
        return writeValue(encoder, value);
    }
    */

    /*package*/ void addChangeListener(ObjectChangeListener listener) {
        //TODO
    }

    /*package*/ void removeChangeListener(ObjectChangeListener listener) {
        //TODO
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
        // TODO:
    }

    private void detachChangeListenerForObject(Object object) {
        if (object instanceof Dictionary) {
            ((Dictionary) object).removeChangeListener(this);
        } else if (object instanceof Array) {
            ((Array) object).removeChangeListener(this);
        }
    }
}
