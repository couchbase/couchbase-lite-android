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
package com.couchbase.lite.internal.fleece;

import java.util.List;
import java.util.Map;

import com.couchbase.lite.LiteCoreException;


public class FLEncoder {
    static native long init(); // FLEncoder FLEncoder_New(void);

    static native void free(long encoder);
    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

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


    private final boolean managed;
    long handle;

    public FLEncoder() {
        this(init());
    }

    public FLEncoder(long handle) {
        this(handle, false);
    }

    public FLEncoder(long handle, boolean managed) {
        this.managed = managed;
        this.handle = handle;
    }

    public void free() {
        if (handle != 0 && !managed) {
            free(handle);
            handle = 0L;
        }
    }

    public boolean writeString(String value) {
        return writeString(handle, value);
    }

    public boolean writeData(byte[] value) {
        return writeData(handle, value);
    }

    public boolean beginDict(long reserve) {
        return beginDict(handle, reserve);
    }

    public boolean endDict() {
        return endDict(handle);
    }

    public boolean beginArray(long reserve) {
        return beginArray(handle, reserve);
    }

    public boolean endArray() {
        return endArray(handle);
    }

    public boolean writeKey(String slice) {
        return writeKey(handle, slice);
    }

    // C/Fleece+CoreFoundation.mm
    // bool FLEncoder_WriteNSObject(FLEncoder encoder, id obj)
    public boolean writeValue(Object value) {
        // null
        if (value == null) { return writeNull(handle); }

        // boolean
        else if (value instanceof Boolean) { return writeBool(handle, (Boolean) value); }

        // Number
        else if (value instanceof Number) {
            // Integer
            if (value instanceof Integer) { return writeInt(handle, ((Integer) value).longValue()); }

            // Long
            else if (value instanceof Long) { return writeInt(handle, ((Long) value).longValue()); }

            // Short
            else if (value instanceof Short) { return writeInt(handle, ((Short) value).longValue()); }

            // Double
            else if (value instanceof Double) { return writeDouble(handle, ((Double) value).doubleValue()); }

            // Float
            else { return writeFloat(handle, ((Float) value).floatValue()); }
        }

        // String
        else if (value instanceof String) { return writeString(handle, (String) value); }

        // byte[]
        else if (value instanceof byte[]) { return writeData(handle, (byte[]) value); }

        // List
        else if (value instanceof List) { return write((List) value); }

        // Map
        else if (value instanceof Map) { return write((Map) value); }

        return false;
    }

    @SuppressWarnings("unchecked")
    public boolean write(Map map) {
        beginDict(map.size());
        final Map<String, Object> m = (Map<String, Object>) map;
        for (Map.Entry<String, Object> entry : m.entrySet()) {
            writeKey(entry.getKey());
            writeValue(entry.getValue());
        }
        return endDict();
    }

    public boolean write(List list) {
        beginArray(list.size());
        for (Object item : list) { writeValue(item); }
        return endArray();
    }

    public byte[] finish() throws LiteCoreException {
        return finish(handle);
    }

    public FLSliceResult finish2() throws LiteCoreException {
        return new FLSliceResult(finish2(handle));
    }

    public Object getExtraInfo() {
        return getExtraInfo(handle);
    }

    public void setExtraInfo(Object info) {
        setExtraInfo(handle, info);
    }

    public void reset() {
        reset(handle);
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------
    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }
}
