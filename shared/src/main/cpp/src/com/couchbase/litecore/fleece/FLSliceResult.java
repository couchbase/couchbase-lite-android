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
package com.couchbase.litecore.fleece;


public class FLSliceResult {

    //-------------------------------------------------------------------------
    // Member variables
    //-------------------------------------------------------------------------

    private long handle = 0; // hold pointer to FLSliceResult

    private boolean retain = false;

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public FLSliceResult() {
        this.handle = init();
    }

    public FLSliceResult(long handle) {
        if (handle == 0)
            throw new IllegalArgumentException("handle is 0");
        this.handle = handle;
    }

    public void free() {
        if (!retain && handle != 0L) {
            free(handle);
            handle = 0L;
        }
    }

    // Use when the native data needs to be retained and will be freed later in the native code
    public FLSliceResult retain() {
        retain = true;
        return this;
    }

    public long getHandle() {
        return handle;
    }

    public byte[] getBuf() {
        return getBuf(handle);
    }

    public long getSize() {
        return getSize(handle);
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

    static native void free(long slice);

    static native byte[] getBuf(long slice);

    static native long getSize(long slice);

}
