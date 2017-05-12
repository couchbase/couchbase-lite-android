package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLEncoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Dictionary extends ReadOnlyDictionary implements DictionaryInterface {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private boolean changed = false;
    private Map<String, Object> map = null;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    public Dictionary() {
        super(null);
    }

    /* package */ Dictionary(CBLFLDict data) {
        super(data);
    }

    public Dictionary(Map<String,Object> dictionary) {
        super(null);
        set(dictionary);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------
    @Override
    public Dictionary set(Map<String, Object> dictionary) {
        return this;
    }

    @Override
    public Dictionary set(String key, Object value) {
        Object oldValue = getObject(key);
        if(!value.equals(oldValue)){
            // TODO:

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
        return null;
    }

    @Override
    public Dictionary getDictionary(String key) {
        return null;
    }

    public Object getObject(String key) {
        Object value = map == null ? null : map.get(key);
        if (value == null) {
            value = super.getObject(key);
            if (value instanceof ReadOnlyDictionary) {

            } else if (value instanceof ReadOnlyArray) {

            }
        }
        // TODO: kCBLRemovedValue
        return value;
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    /* package */ List<String> allKeys() {
        List<String> result = new ArrayList<>(map.keySet());
        for(String key: super.allKeys()){
            if(!result.contains(key))
                result.add(key);
        }

        //TODO -  kCBLRemovedValue

        return result;
    }
    //---------------------------------------------
    // Package level access
    //---------------------------------------------
    /* package */boolean isChanged(){
        return changed;
    }

    /**
     * Check if contains unsaved property?
     * @return
     */
    /* package */boolean isEmpty(){

        /*
         -- Remove name field: data structure --
         fleece ["name": "pasin", "address": "1 street"]
         map: ["name": kCBLRemovedValue]
         */

        // Note: RemovedValue can not be check
        if(map==null||map.size() == 0)
            return super.count() == 0;

        // something in fleece, but not in map
        for(String key:allKeys()){
            if(map!=null&&!map.containsKey(key))
                return false;
        }

        // TODO
        if(map!=null) {
            for (Object value : map.values()) {
//            if(!kCBLRemovedValue.equals(value)){
                return false;
//            }
            }
        }

        return true;
    }

    // #pragma mark - FLEECE ENCODABLE

    /* package */void fleeceEncode(FLEncoder encoder) throws CouchbaseLiteException {
        List<String> keys = allKeys();
        encoder.beginDict(keys.size());
        for (String key : keys) {
            Object value = getObject(key);
            //if(value != kCBLRemovedValue){
            encoder.writeKey(key);
            writeValue(encoder, value);
            //}
        }
        encoder.endDict();
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
        else if (value instanceof Blob)
            return writeValueForObject(encoder, value);
        return false;
    }

    /*package*/ boolean write(FLEncoder encoder, Map map) {
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

    /*package*/  boolean writeValueForObject(FLEncoder encoder, Object value) {
        if (value instanceof Blob)
            value = ((Blob) value).jsonRepresentation();
        return writeValue(encoder, value);
    }
    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------
    private void set(String key, Object value, boolean isChange){
        if(map == null)
            map = new HashMap<>();
        map.put(key, value);
        if(isChange)
            setChanged();
    }

    //  CHANGE
    private void setChanged(){
        if(!changed){
            changed = true;
            notifyChangeListeners();
        }
    }


    private void notifyChangeListeners(){
        // TODO:
    }
}
