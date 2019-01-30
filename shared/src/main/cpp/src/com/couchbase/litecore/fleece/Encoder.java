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
package com.couchbase.litecore.fleece;

import com.couchbase.litecore.LiteCoreException;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Encoder {
    long _handle = 0; // hold pointer to Encoder*
    boolean _managed;
    FLEncoder _flEncoder;

    public Encoder() {
        this(init(), false);
    }

    public Encoder(FLEncoder enc) {
        this(initWithFLEncoder(enc._handle), false);
        _flEncoder = enc;
    }

    Encoder(long handle, boolean managed) {
        if (handle == 0)
            throw new IllegalArgumentException();
        this._handle = handle;
        this._managed = managed;
    }

    public void free() {
        if (_handle != 0L && !_managed) {
            free(_handle);
            _handle = 0L;
        }
    }

    public void release() {
        if (_handle != 0L && !_managed) {
            release(_handle);
            _handle = 0L;
        }
    }

    public FLEncoder getFLEncoder() {
        if (_flEncoder == null)
            _flEncoder = new FLEncoder(getFLEncoder(_handle), true);
        return _flEncoder;
    }

    public boolean writeNull() {
        return writeNull(_handle);
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

    public boolean writeValue(FLValue flValue) {
        return writeValue(_handle, flValue.getHandle());
    }

    public boolean writeValue(FLArray flValue) {
        return writeValue(_handle, flValue.getHandle());
    }

    public boolean writeValue(FLDict flValue) {
        return writeValue(_handle, flValue.getHandle());
    }

    public boolean write(Map map) {
        if (map != null) {
            beginDict(map.size());
            Iterator keys = map.keySet().iterator();
            while (keys.hasNext()) {
                String key = (String) keys.next();
                writeKey(key);
                writeObject(map.get(key));
            }
        } else
            beginDict(0);
        return endDict();

    }

    public boolean write(List list) {
        if (list != null) {
            beginArray(list.size());
            for (Object item : list) {
                writeObject(item);
            }
        } else
            beginArray(0);
        return endArray();
    }

    // C/Fleece+CoreFoundation.mm
    // bool FLEncoder_WriteNSObject(FLEncoder encoder, id obj)
    public boolean writeObject(Object value) {
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

            // FLEncodable
        else if (value instanceof FLEncodable) {
            ((FLEncodable) value).encodeTo(getFLEncoder());
            return true;
        }

        return false;
    }

    public AllocSlice finish() throws LiteCoreException {
        return new AllocSlice(finish(_handle), false);
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
    // native methods
    //-------------------------------------------------------------------------

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
}
