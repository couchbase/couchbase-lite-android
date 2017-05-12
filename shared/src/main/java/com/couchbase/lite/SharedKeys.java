package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLDictIterator;
import com.couchbase.litecore.fleece.FLSharedKeys;
import com.couchbase.litecore.fleece.FLValue;

import java.util.HashMap;
import java.util.Map;

public class SharedKeys implements FLValue.ISharedKeys {

    // Notes:
    //  CBLSharedKeys.hh

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    /*package*/ FLSharedKeys flSharedKeys;
    /*package*/ Map<Integer, String> documentStrings = new HashMap<>();
    /*package*/ FLDict root;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    SharedKeys() {
    }

    SharedKeys(com.couchbase.litecore.Database c4db) {
        flSharedKeys = c4db.getFLSharedKeys();
    }

    SharedKeys(final SharedKeys sk) {
        this.flSharedKeys = sk.flSharedKeys;
    }

    SharedKeys(final SharedKeys sk, FLDict root) {
        this(sk);
        useDocumentRoot(root);
    }

    //---------------------------------------------
    // Package level methods
    //---------------------------------------------

    // void useDocumentRoot(FLDict);
    void useDocumentRoot(FLDict root) {
        if (this.root != root) {
            this.root = root;
            documentStrings.clear();
        }
    }

    // id __nullable valueToObject(FLValue __nullable value)
    Object valueToObject(FLValue value) {
        //return getObject(value, this, documentStrings);
        return value == null ? null : value.toObject(documentStrings, this);
    }

    // FLValue getDictValue(FLDict __nullable dict, FLSlice key)
    FLValue getValue(FLDict dict, String key) {
        return dict.getSharedKey(key, flSharedKeys);
    }

    // NSString* getDictIterKey(FLDictIterator* iter)
    String getDictIterKey(FLDictIterator itr) {
        FLValue key = itr.getKey();
        if (key == null)
            return null;

        if (key.isInteger())
            return getKey(key.asInt());

        return (String) valueToObject(key);
    }

    // public string GetKey(int index)
    public String getKey(int index) {
        String retVal = documentStrings.get(index);
        if (retVal != null)
            return retVal;

        retVal = FLDict.getKeyString(flSharedKeys, index);
        if (retVal != null)
            documentStrings.put(index, retVal);

        return retVal;
    }

    //---------------------------------------------
    // static methods
    //---------------------------------------------

    // static inline id FLValue_GetNSObject(FLValue __nullable value, cbl::SharedKeys *sk)
    static Object valueToObject(FLValue value, SharedKeys sk) {
        return sk != null ? sk.valueToObject(value) : null;
    }

    // static inline id FLValue_GetNSObject(FLValue __nullable value, cbl::SharedKeys *sk)
    static FLValue getValue(FLDict dict, String key, SharedKeys sk) {
        return sk != null ? sk.getValue(dict, key) : null;
    }

    // static inline NSString* FLDictIterator_GetKey(FLDictIterator *iter, cbl::SharedKeys *sk)
    public static String getKey(FLDictIterator itr, SharedKeys sk) {
        return sk != null ? sk.getDictIterKey(itr) : null;
    }
}

