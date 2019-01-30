//
// FLEncoder.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.litecore.fleece;

import com.couchbase.litecore.LiteCoreException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FLEncoder {
    //-------------------------------------------------------------------------
    // package level variable
    //-------------------------------------------------------------------------
    long _handle = 0L;
    boolean _managed = false;
    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public FLEncoder() {
        this(init());
    }

    public FLEncoder(long handle) {
        this(handle, false);
    }

    public FLEncoder(long handle, boolean managed) {
        _managed = managed;
        _handle = handle;
    }

    public void free() {
        if (_handle != 0 && !_managed) {
            free(_handle);
            _handle = 0L;
        }
    }

    public boolean writeString(String value) {
        return writeString(_handle, value);
    }

    public boolean writeData(byte[] value) {
        return writeData(_handle, value);
    }

    public boolean beginDict(long reserve) {
        return beginDict(_handle, reserve);
    }

    public boolean endDict() {
        return endDict(_handle);
    }

    public boolean beginArray(long reserve) {
        return beginArray(_handle, reserve);
    }

    public boolean endArray() {
        return endArray(_handle);
    }

    public boolean writeKey(String slice) {
        return writeKey(_handle, slice);
    }

    // C/Fleece+CoreFoundation.mm
    // bool FLEncoder_WriteNSObject(FLEncoder encoder, id obj)
    public boolean writeValue(Object value) {
        // null
        if (value == null)
            return writeNull(_handle);

            // boolean
        else if (value instanceof Boolean)
            return writeBool(_handle, (Boolean) value);

            // Number
        else if (value instanceof Number) {
            // Integer
            if (value instanceof Integer)
                return writeInt(_handle, ((Integer) value).longValue());

                // Long
            else if (value instanceof Long)
                return writeInt(_handle, ((Long) value).longValue());

                // Short
            else if (value instanceof Short)
                return writeInt(_handle, ((Short) value).longValue());

                // Double
            else if (value instanceof Double)
                return writeDouble(_handle, ((Double) value).doubleValue());

                // Float
            else
                return writeFloat(_handle, ((Float) value).floatValue());
        }

        // String
        else if (value instanceof String)
            return writeString(_handle, (String) value);

            // byte[]
        else if (value instanceof byte[])
            return writeData(_handle, (byte[]) value);

            // List
        else if (value instanceof List)
            return write((List) value);

            // Map
        else if (value instanceof Map)
            return write((Map) value);

        return false;
    }

    public boolean write(Map map) {
        beginDict(map.size());
        Iterator keys = map.keySet().iterator();
        while (keys.hasNext()) {
            String key = (String) keys.next();
            writeKey(key);
            writeValue(map.get(key));
        }
        return endDict();
    }

    public boolean write(List list) {
        beginArray(list.size());
        for (Object item : list)
            writeValue(item);
        return endArray();
    }

    public byte[] finish() throws LiteCoreException {
        return finish(_handle);
    }

    public FLSliceResult finish2() throws LiteCoreException {
        return new FLSliceResult(finish2(_handle));
    }

    public void setExtraInfo(Object info) {
        setExtraInfo(_handle, info);
    }

    public Object getExtraInfo() {
        return getExtraInfo(_handle);
    }

    public void reset() {
        reset(_handle);
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------
    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    static native long init(); // FLEncoder FLEncoder_New(void);

    static native void free(long encoder);

    static native boolean writeNull(long encoder);

    static native boolean writeBool(long encoder, boolean value);

    static native boolean writeInt(long encoder, long value); // 64bit

    static native boolean writeFloat(long encoder, float value);

    static native boolean writeDouble(long encoder, double value);

    static native boolean writeString(long encoder, String value);

    static native boolean writeData(long encoder, byte[] value);

    static native boolean beginArray(long encoder, long reserve);

    static native boolean endArray(long encoder);

    static native boolean beginDict(long encoder, long reserve);

    static native boolean endDict(long encoder);

    static native boolean writeKey(long encoder, String slice);

    static native byte[] finish(long encoder) throws LiteCoreException;

    static native long finish2(long encoder) throws LiteCoreException;

    static native void setExtraInfo(long encoder, Object info);

    static native Object getExtraInfo(long encoder);

    static native void reset(long encoder);
}
