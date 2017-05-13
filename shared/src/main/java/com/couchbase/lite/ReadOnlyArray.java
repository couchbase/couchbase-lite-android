package com.couchbase.lite;


import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.fleece.FLArray;
import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.FLValue;

import java.util.Date;
import java.util.Iterator;
import java.util.List;


/* package */ class ReadOnlyArray implements ReadOnlyArrayInterface ,FleeceEncodable, Iterable<Object>{

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private CBLFLArray data;
    private FLArray flArray;
    private SharedKeys sharedKeys;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /* package */ ReadOnlyArray(CBLFLArray data) {
        setData(data);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    @Override
    public long count() {
        return flArray != null ? flArray.count() : 0;
    }

    @Override
    public Object getObject(int index) {
        return fleeceValueToObject(index);
    }

    @Override
    public String getString(int index) {
        return (String) fleeceValueToObject(index);
    }

    @Override
    public Number getNumber(int index) {
        return (Number) fleeceValueToObject(index);
    }

    @Override
    public int getInt(int index) {
        return fleeceValue(index).asInt();
    }

    @Override
    public long getLong(int index) {
        // asLong
        return fleeceValue(index).asInt();
    }

    @Override
    public float getFloat(int index) {
        return fleeceValue(index).asFloat();
    }

    @Override
    public double getDouble(int index) {
        return fleeceValue(index).asDouble();
    }

    @Override
    public boolean getBoolean(int index) {
        return fleeceValue(index).asBool();
    }

    @Override
    public Blob getBlob(int index) {
        return (Blob) fleeceValueToObject(index);
    }

    @Override
    public Date getDate(int index) {
        return DateUtils.fromJson(getString(index));
    }

    @Override
    public ReadOnlyArray getArray(int index) {
        return (ReadOnlyArray) fleeceValueToObject(index);
    }

    @Override
    public ReadOnlyDictionary getDictionary(int index) {
        return (ReadOnlyDictionary) fleeceValueToObject(index);
    }

    @Override
    public List<Object> toList() {
        //TODO
        return null;
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------

    @Override
    public Iterator<Object> iterator() {
        return null;
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    protected CBLFLArray getData() {
        return data;
    }

    protected void setData(CBLFLArray data) {
        this.data = data;
        this.flArray = null;
        this.sharedKeys = null;
        if (data != null) {
            this.flArray = data.getFLArray();
            if (data.getDatabase() != null)
                this.sharedKeys = data.getDatabase().getSharedKeys();
        }
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    // FleeceEncodable implementation
    @Override
    public void fleeceEncode(FLEncoder encoder, Database database) {
        encoder.writeValue(flArray);
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    // #pragma mark - FLEECE ENCODABLE

    // #pragma mark - FLEECE

    private FLValue fleeceValue(int index) {
        return flArray.get(index);
    }

    private Object fleeceValueToObject(int index) {
        FLValue value = fleeceValue(index);
        if (value != null)
            return CBLData.fleeceValueToObject(value, data.getC4doc(), data.getDatabase());
        else
            return null;
    }


}
