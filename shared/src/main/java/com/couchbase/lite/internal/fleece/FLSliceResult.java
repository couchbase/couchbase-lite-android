//
// FLSliceResult.java
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


public class FLSliceResult {

    //-------------------------------------------------------------------------
    // Member variables
    //-------------------------------------------------------------------------

    static native long init();

    static native void free(long slice);

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    static native byte[] getBuf(long slice);

    static native long getSize(long slice);
    private long handle; // hold pointer to FLSliceResult
    private boolean shouldRetain;

    public FLSliceResult() {
        this.handle = init();
    }

    public FLSliceResult(long handle) {
        if (handle == 0) { throw new IllegalArgumentException("handle is 0"); }
        this.handle = handle;
    }

    public void free() {
        if (!shouldRetain && handle != 0L) {
            free(handle);
            handle = 0L;
        }
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    // Use when the native data needs to be retained and will be freed later in the native code
    public FLSliceResult retain() {
        shouldRetain = true;
        return this;
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    public long getHandle() {
        return handle;
    }

    public byte[] getBuf() {
        return getBuf(handle);
    }

    public long getSize() {
        return getSize(handle);
    }

    @SuppressWarnings("NoFinalizer")
    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }
}
