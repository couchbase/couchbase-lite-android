//
// AllocSlice.java
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

public class AllocSlice {
    long _handle = 0;         // hold pointer to alloc_slice*
    boolean _retain = false;  // true -> not release native object, false -> release by free()

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------
    public AllocSlice(byte[] bytes) {
        this(init(bytes), false);
    }

    public AllocSlice(long handle, boolean retain) {
        if (handle == 0)
            throw new IllegalArgumentException("handle is 0");
        this._handle = handle;
        this._retain = retain;
    }

    public void free() {
        if (_handle != 0L && !_retain) {
            free(_handle);
            _handle = 0L;
        }
    }

    public long getHandle() {
        return _handle;
    }

    public byte[] getBuf() {
        return getBuf(_handle);
    }

    public long getSize() {
        return getSize(_handle);
    }

    public AllocSlice retain() {
        _retain = true;
        return this;
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

    static native long init(byte[] bytes);

    static native void free(long slice);

    static native byte[] getBuf(long slice);

    static native long getSize(long slice);
}
