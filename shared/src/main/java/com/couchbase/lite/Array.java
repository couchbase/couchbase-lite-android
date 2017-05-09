package com.couchbase.lite;

import java.util.List;

public class Array extends ReadOnlyArray implements ArrayInterface {
    //---------------------------------------------
    // member variables
    //---------------------------------------------

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    public Array() {
        super(null);
    }

    public Array(List<Object> array) {
        //TODO
        super(null);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------
    @Override
    public ArrayInterface set(List<Object> list) {
        return this;
    }

    @Override
    public ArrayInterface set(int index, Object value) {
        return this;
    }

    @Override
    public ArrayInterface add(Object value) {
        return this;
    }

    @Override
    public ArrayInterface insert(int index, Object value) {
        return this;
    }

    @Override
    public ArrayInterface remove(int value) {
        return this;
    }

    @Override
    public Array getArray(int index) {
        return null;
    }

    @Override
    public Dictionary getDictionary(int index) {
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
