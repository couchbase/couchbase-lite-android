package com.couchbase.lite;


import java.util.Date;
import java.util.Map;

/* package */ class ReadOnlyDictionary implements ReadOnlyDictionaryInterface {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final ReadOnlyDictionaryInterface data;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /* package */ ReadOnlyDictionary() {
        this((ReadOnlyDictionaryInterface) null);
    }

    /* package */ ReadOnlyDictionary(ReadOnlyDictionaryInterface data) {
        //this.data = data != null ? data : ReadOnlyDictionaryInterface.EMPTY;
        this.data = data;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    @Override
    public int count() {
        return 0;
    }

    @Override
    public Object getObject(String key) {
        return null;
    }

    @Override
    public String getString(String key) {
        return null;
    }

    @Override
    public Number getNumber(String key) {
        return null;
    }

    @Override
    public int getInt(String key) {
        return 0;
    }

    @Override
    public long getLong(String key) {
        return 0;
    }

    @Override
    public float getFloat(String key) {
        return 0;
    }

    @Override
    public double getDouble(String key) {
        return 0;
    }

    @Override
    public boolean getBoolean(String key) {
        return false;
    }

    @Override
    public Blob getBlob(String key) {
        return null;
    }

    @Override
    public Date getDate(String key) {
        return null;
    }

    @Override
    public ReadOnlyArray getArray(String key) {
        return null;
    }

    @Override
    public ReadOnlyDictionary getDictionary(String key) {
        return null;
    }

    @Override
    public Map<String, Object> toMap() {
        return null;
    }

    @Override
    public boolean contains(String key) {
        return false;
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    //---------------------------------------------
    // Package level access
    //---------------------------------------------
    /* package */ ReadOnlyDictionaryInterface getData() {
        return data;
    }
    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------
}
