package com.couchbase.lite;

import com.couchbase.lite.internal.support.DateUtils;
import com.couchbase.litecore.fleece.FLEncoder;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class Array extends ReadOnlyArray implements ArrayInterface, ObjectChangeListener, FleeceEncodable {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private List<Object> list = null;
    private boolean changed = false;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    public Array() {
        this((CBLFLArray)null);
    }

    public Array(List<Object> array) {
        this((CBLFLArray)null);
        set(array);
    }

    /* package */ Array(CBLFLArray data) {
        super(data);
        list = new ArrayList<>();
        loadBackingFleeceData();
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------
    @Override
    public Array set(List<Object> list) {
        // Detach all objects that we are listening to for changes:
        detachChildChangeListeners();

        List<Object> result = new ArrayList<>();
        for (Object value : list) {
            result.add(CBLData.convert(value, this));
        }
        this.list = result;
        return this;
    }

    @Override
    public Array set(int index, Object value) {
        Object oldValue = getObject(index);
        if (!value.equals(oldValue)) {
            value = CBLData.convert(value, this);
            detachChangeListenerForObject(oldValue);
            set(index, value, true);
        }
        return this;
    }

    @Override
    public Array add(Object value) {
        list.add(CBLData.convert(value, this));
        setChanged();
        return this;
    }

    @Override
    public Array insert(int index, Object value) {
        list.add(index, CBLData.convert(value, this));
        setChanged();
        return this;
    }

    @Override
    public Array remove(int index) {
        Object value = list.get(index);
        detachChangeListenerForObject(value);
        list.remove(index);
        setChanged();
        return this;
    }

    @Override
    public Array getArray(int index) {
        return (Array) getObject(index);
    }

    @Override
    public Dictionary getDictionary(int index) {
        return (Dictionary) getObject(index);
    }

    //---------------------------------------------
    // API - overridden from ReadOnlyArray
    //---------------------------------------------

    @Override
    public long count() {
        return list != null ? list.size() : 0;
    }

    @Override
    public Object getObject(int index) {
        return list.get(index);
    }

    @Override
    public String getString(int index) {
        return (String) getObject(index);
    }

    @Override
    public Number getNumber(int index) {
        return (Number) getObject(index);
    }

    @Override
    public int getInt(int index) {
        return getNumber(index).intValue();
    }

    @Override
    public long getLong(int index) {
        return getNumber(index).longValue();
    }

    @Override
    public float getFloat(int index) {
        return getNumber(index).floatValue();
    }

    @Override
    public double getDouble(int index) {
        return getNumber(index).doubleValue();
    }

    @Override
    public boolean getBoolean(int index) {
        return CBLData.toBoolean(getObject(index));
    }

    @Override
    public Blob getBlob(int index) {
        return (Blob) getObject(index);
    }

    @Override
    public Date getDate(int index) {
        return DateUtils.fromJson(getString(index));
    }

    @Override
    public List<Object> toList() {
        List<Object> array = new ArrayList<>();
        if (list != null) {
            for (Object value : list) {
                if (value instanceof ReadOnlyDictionary)
                    value = ((ReadOnlyDictionary) value).toMap();
                else if (value instanceof ReadOnlyArray)
                    value = ((ReadOnlyArray) value).toList();
                array.add(value);
            }
        }
        return array;
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

    //---------------------------------------------
    // Package level access
    //---------------------------------------------
    /*package*/ void addChangeListener(ObjectChangeListener listener) {
        //TODO
    }

    /*package*/ void removeChangeListener(ObjectChangeListener listener) {
        //TODO
    }

    // FleeceEncodable implementation
    @Override
    public void fleeceEncode(FLEncoder encoder, Database database) {
        encoder.beginArray(count());
        for (int i = 0; i < count(); i++) {
            Object value = getObject(i);
            if (value instanceof FleeceEncodable)
                ((FleeceEncodable) value).fleeceEncode(encoder, database);
            else
                encoder.writeValue(value);
        }
        encoder.endArray();
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------
    private void set(int index, Object value, boolean isChange) {
        if (list == null)
            list = new ArrayList<>();
        list.set(index, value);
        if (isChange)
            setChanged();
    }

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

    private void detachChildChangeListeners() {
        if (list == null) return;

        for (Object object : list) {
            detachChangeListenerForObject(object);
        }
    }

    private void loadBackingFleeceData() {
        int count = (int) super.count();
        for (int i = 0; i < count; i++) {
            Object value = super.getObject(i);
            list.add(CBLData.convert(value, this));
        }
    }
}
