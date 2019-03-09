//
// Encoder.java
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
import java.util.Set;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;


public class Encoder {
    static native long init();

    static native long initWithFLEncoder(long enc);

    static native void free(long handle);

    static native void release(long handle);

    static native long getFLEncoder(long handle);

    static native boolean writeNull(long handle);

    static native boolean writeBool(long handle, boolean value);

    static native boolean writeInt(long handle, long value); // 64bit

    static native boolean writeFloat(long handle, float value);

    static native boolean writeDouble(long handle, double value);

    static native boolean writeString(long handle, String value);

    static native boolean writeData(long handle, byte[] value);

    static native boolean writeValue(long handle, long value);

    static native boolean beginArray(long handle, long reserve);

    static native boolean endArray(long handle);

    static native boolean beginDict(long handle, long reserve);

    static native boolean writeKey(long handle, String slice);

    static native boolean endDict(long handle);

    static native long finish(long handle);

    private long handle; // hold pointer to Encoder*
    private FLEncoder flEncoder;

    private final boolean managed;

    public Encoder() {
        this(init(), false);
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    public Encoder(FLEncoder enc) {
        this(initWithFLEncoder(enc.handle), false);
        flEncoder = enc;
    }

    Encoder(long handle, boolean managed) {
        if (handle == 0) { throw new IllegalArgumentException(); }
        this.handle = handle;
        this.managed = managed;
    }

    public void release() {
        if (handle != 0L && !managed) {
            release(handle);
            handle = 0L;
        }
    }

    public boolean writeNull() {
        return writeNull(handle);
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

    public boolean writeValue(FLValue flValue) {
        return writeValue(handle, flValue.getHandle());
    }

    public boolean writeValue(FLArray flValue) {
        return writeValue(handle, flValue.getHandle());
    }

    public boolean writeValue(FLDict flValue) {
        return writeValue(handle, flValue.getHandle());
    }

    @SuppressFBWarnings("WMI_WRONG_MAP_ITERATOR")
    @SuppressWarnings("unchecked")
    public boolean write(Map map) {
        if (map == null) { beginDict(0); }
        else {
            beginDict(map.size());
            // getting an entrySet is unsupported for FleeceDicts
            for (String key : (Set<String>) map.keySet()) {
                writeKey(key);
                writeObject(map.get(key));
            }
        }

        return endDict();
    }

    public boolean write(List list) {
        if (list == null) { beginArray(0); }
        else {
            beginArray(list.size());
            for (Object item : list) {
                writeObject(item);
            }
        }
        return endArray();
    }

    // C/Fleece+CoreFoundation.mm
    // bool FLEncoder_WriteNSObject(FLEncoder encoder, id obj)
    public boolean writeObject(Object value) {
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

        // FLEncodable
        else if (value instanceof FLEncodable) {
            ((FLEncodable) value).encodeTo(getFLEncoder());
            return true;
        }

        return false;
    }

    public AllocSlice finish() {
        return new AllocSlice(finish(handle), false);
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

    private void free() {
        if (handle != 0L && !managed) {
            free(handle);
            handle = 0L;
        }
    }

    private FLEncoder getFLEncoder() {
        if (flEncoder == null) { flEncoder = new FLEncoder(getFLEncoder(handle), true); }
        return flEncoder;
    }
}
