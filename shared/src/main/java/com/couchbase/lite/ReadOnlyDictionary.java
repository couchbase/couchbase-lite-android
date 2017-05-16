package com.couchbase.lite;


import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLDictIterator;
import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.FLValue;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/* package */ class ReadOnlyDictionary implements ReadOnlyDictionaryInterface, FleeceEncodable {

    //-------------------------------------------------------------------------
    // member variables
    //-------------------------------------------------------------------------
    private CBLFLDict data;
    private FLDict flDict;
    private SharedKeys sharedKeys;
    List<String> keys = null; // dictionary key cache

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    /* package */ ReadOnlyDictionary(CBLFLDict data) {
        setData(data);
    }

    //-------------------------------------------------------------------------
    // API - public methods
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    // Implementation of ReadOnlyDictionaryInterface
    //-------------------------------------------------------------------------

    @Override
    public long count() {
        return flDict != null ? flDict.count() : 0;
    }

    @Override
    public Object getObject(String key) {
        return fleeceValueToObject(key);
    }

    @Override
    public String getString(String key) {
        return (String) fleeceValueToObject(key);
    }

    @Override
    public Number getNumber(String key) {
        return (Number) fleeceValueToObject(key);
    }

    @Override
    public int getInt(String key) {
        FLValue value = fleeceValue(key);
        return value != null ? value.asInt() : 0;
    }

    @Override
    public long getLong(String key) {
        // TODO asLong()?
        FLValue value = fleeceValue(key);
        return value != null ? value.asInt() : 0;
    }

    @Override
    public float getFloat(String key) {
        FLValue value = fleeceValue(key);
        return value != null ? value.asFloat() : 0.0f;
    }

    @Override
    public double getDouble(String key) {
        FLValue value = fleeceValue(key);
        return value != null ? value.asDouble() : 0.0;
    }

    @Override
    public boolean getBoolean(String key) {
        FLValue value = fleeceValue(key);
        return value != null ? value.asBool() : false;
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
        if (flDict != null)
            // TODO: Need to review!
            return flDictToMap(flDict);
        else
            return new HashMap<>();
    }

    @Override
    public boolean contains(String key) {
        // TODO: Need to review!
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

    // TODO: Once Iterable is implemented. This method should be changed to package level access
    public List<String> allKeys() {
        if (keys == null) {
            List<String> results = new ArrayList<>();
            if (flDict != null) {
                FLDictIterator itr = new FLDictIterator();
                itr.begin(flDict);
                String key;
                while ((key = SharedKeys.getKey(itr, this.sharedKeys)) != null) {
                //while ((key = itr.getKey().asString()) != null) {
                    results.add(key);
                    itr.next();
                }
                itr.free();
            }

            keys = results;
        }
        return keys;
    }

    // FleeceEncodable implementation
    @Override
    public void fleeceEncode(FLEncoder encoder, Database database) throws CouchbaseLiteException {
        encoder.writeValue(flDict);
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    // #pragma mark - FLEECE

    private FLValue fleeceValue(String key) {
        return SharedKeys.getValue(flDict, key, sharedKeys);
    }

    private Object fleeceValueToObject(String key) {
        FLValue value = fleeceValue(key);
        if (value != null)
            return CBLData.fleeceValueToObject(value, data.getC4doc(), data.getDatabase());
        else
            return null;
    }

    private Map<String, Object> flDictToMap(FLDict flDict) {
        if (flDict == null) return null;

        Map<String, Object> dict = new HashMap<>();
        FLDictIterator itr = new FLDictIterator();
        itr.begin(flDict);
        String key;
        while ((key = SharedKeys.getKey(itr, sharedKeys)) != null) {
            dict.put(key, CBLData.fleeceValueToObject(itr.getValue(), data.getC4doc(), data.getDatabase()));
            itr.next();
        }
        itr.free();

        return dict;
    }
}
