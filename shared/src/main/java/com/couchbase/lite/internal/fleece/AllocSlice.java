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
package com.couchbase.lite.internal.fleece;


public class AllocSlice {
    static native long init(byte[] bytes);

    static native void free(long slice);

    static native byte[] getBuf(long slice);

    static native long getSize(long slice);

    private boolean shouldRetain;  // true -> not release native object, false -> release by free()
    long handle;         // hold pointer to alloc_slice*

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------
    public AllocSlice(byte[] bytes) {
        this(init(bytes), false);
    }

    public AllocSlice(long handle, boolean retain) {
        if (handle == 0) { throw new IllegalArgumentException("handle is 0"); }
        this.handle = handle;
        this.shouldRetain = retain;
    }

    public long getHandle() {
        return handle;
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    public byte[] getBuf() {
        return getBuf(handle);
    }

    public long getSize() {
        return getSize(handle);
    }

    public AllocSlice retain() {
        shouldRetain = true;
        return this;
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
        if (handle != 0L && !shouldRetain) {
            free(handle);
            handle = 0L;
        }
    }
}
