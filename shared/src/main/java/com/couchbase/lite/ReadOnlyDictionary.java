package com.couchbase.lite;


import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLDictIterator;
import com.couchbase.litecore.fleece.FLValue;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* package */ class ReadOnlyDictionary implements ReadOnlyDictionaryInterface {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private CBLFLDict data;
    private FLDict flDict; // NOTE: We might need to implment like CBLFLDict
    private SharedKeys sharedKeys;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /* package */ ReadOnlyDictionary(CBLFLDict data) {
        setData(data);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    @Override
    public long count() {
        return flDict!=null?flDict.count():0;
    }

    @Override
    public Object getObject(String key) {
        return fleeceValueToObject(key);
    }

    @Override
    public String getString(String key) {
        return (String)fleeceValueToObject(key);
    }

    @Override
    public Number getNumber(String key) {
        return (Number)fleeceValueToObject(key);
    }

    @Override
    public int getInt(String key) {
        return fleeceValue(key).asInt();
    }

    @Override
    public long getLong(String key) {
        // TODO asLong()?
        return fleeceValue(key).asInt();
    }

    @Override
    public float getFloat(String key) {
        return fleeceValue(key).asFloat();
    }

    @Override
    public double getDouble(String key) {
        return fleeceValue(key).asDouble();
    }

    @Override
    public boolean getBoolean(String key) {
        return fleeceValue(key).asBool();
    }

    @Override
    public Blob getBlob(String key) {
        return (Blob) fleeceValueToObject(key);
    }

    @Override
    public Date getDate(String key) {
        return DateUtils.fromJson(getString(key));
    }

    @Override
    public ReadOnlyArray getArray(String key) {
        return (ReadOnlyArray) fleeceValueToObject(key);
    }

    @Override
    public ReadOnlyDictionary getDictionary(String key) {
        return (ReadOnlyDictionary) fleeceValueToObject(key);
    }

    @Override
    public Map<String, Object> toMap() {
        return fleeceRootToDictionary(flDict);
    }

    @Override
    public boolean contains(String key) {
        return fleeceValue(key) != null;
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    protected CBLFLDict getData() {
        return data;
    }

    protected void setData(CBLFLDict data) {
        this.data = data;
        this.flDict = null;
        this.sharedKeys = null;
        if (data != null) {
            this.flDict = data.getFlDict();
            if (data.getDatabase() != null)
                this.sharedKeys = data.getDatabase().getSharedKeys();
        }
    }


    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    /* package */ List<String> allKeys() {
        List<String> keys = new ArrayList<>();
        if (flDict != null) {
            FLDictIterator itr = new FLDictIterator();
            itr.begin(flDict);
            String key;
            while((key = itr.getKey().asString())!=null){
                keys.add(key);
                itr.next();
            }
            itr.free();
        }
        return keys;
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------




    // #pragma mark - FLEECE

    private FLValue fleeceValue(String key){
        return SharedKeys.getValue(flDict, key, sharedKeys);
    }

    private Object fleeceValueToObject(String key) {
        FLValue value = fleeceValue(key);
        if(value != null)
                return Data.fleeceValueToObject(value, sharedKeys);
            else
                return null;

    }

    private Map<String, Object> fleeceRootToDictionary(FLDict flDict) {
        if (flDict == null) return null;

        Map<String, Object> dict = new HashMap<>();
        FLDictIterator itr = new FLDictIterator();
        itr.begin(flDict);
        String key;
        while ((key = SharedKeys.getKey(itr, sharedKeys)) != null) {
            //dict.put(key, fleeceValueToObject(itr.getValue()));
            dict.put(key, Data.fleeceValueToObject(itr.getValue(), sharedKeys));
            itr.next();
        }
        itr.free();

        return dict;
    }
}
