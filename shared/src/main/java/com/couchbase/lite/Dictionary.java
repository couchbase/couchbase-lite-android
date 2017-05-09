package com.couchbase.lite;

import java.util.Map;

public class Dictionary extends ReadOnlyDictionary implements DictionaryInterface {
    //---------------------------------------------
    // member variables
    //---------------------------------------------

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    public Dictionary() {
        super(null);
    }

    public Dictionary(Map<String,Object> dictionary) {
        // TODO
        super(null);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------
    @Override
    public DictionaryInterface set(Map<String, Object> dictionary) {
        return this;
    }

    @Override
    public DictionaryInterface set(String key, Object value) {
        return this;
    }

    @Override
    public DictionaryInterface remove(String key) {
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

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------
}
