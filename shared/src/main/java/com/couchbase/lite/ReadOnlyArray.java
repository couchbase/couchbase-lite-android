package com.couchbase.lite;

import java.util.Date;
import java.util.List;

/* package */ class ReadOnlyArray implements ReadOnlyArrayInterface {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final ReadOnlyArrayInterface data;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /* package */ ReadOnlyArray() {
        this((ReadOnlyArrayInterface) null);
    }

//    protected ReadOnlyArray(List<Object> list) {
//        // TODO
//        //this(new ListData(list));
//        this((ReadOnlyArrayInterface) null);
//    }

    /* package */ ReadOnlyArray(ReadOnlyArrayInterface data) {
        //this.data = (data != null ? data : ReadOnlyArrayInterface.EMPTY);
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
    public Object getObject(int index) {
        return null;
    }

    @Override
    public String getString(int index) {
        return null;
    }

    @Override
    public Number getNumber(int index) {
        return null;
    }

    @Override
    public int getInt(int index) {
        return 0;
    }

    @Override
    public long getLong(int index) {
        return 0;
    }

    @Override
    public float getFloat(int index) {
        return 0;
    }

    @Override
    public double getDouble(int index) {
        return 0;
    }

    @Override
    public boolean getBoolean(int index) {
        return false;
    }

    @Override
    public Blob getBlob(int index) {
        return null;
    }

    @Override
    public Date getDate(int index) {
        return null;
    }

    @Override
    public ReadOnlyArray getArray(int index) {
        return null;
    }

    @Override
    public ReadOnlyDictionary getDictionary(int index) {
        return null;
    }

    @Override
    public List<Object> toList() {
        return null;
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    //---------------------------------------------
    // Package level access
    //---------------------------------------------
    /* package */ ReadOnlyArrayInterface getData() {
        return data;
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------
}
