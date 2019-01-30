//
// C4BlobKey.java
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
package com.couchbase.litecore;

/**
 * Blob Key
 * <p>
 * A raw SHA-1 digest used as the unique identifier of a blob.
 */
public class C4BlobKey {
    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    private long handle = 0L; // hold pointer to C4BlobKey

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------

    /**
     * Decodes a string of the form "sha1-"+base64 into a raw key.
     */
    public C4BlobKey(String str) throws LiteCoreException {
        handle = fromString(str);
    }

    C4BlobKey(long handle) {
        if (handle == 0)
            throw new IllegalArgumentException("handle is 0");
        this.handle = handle;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    /**
     * Encodes a blob key to a string of the form "sha1-"+base64.
     */
    public String toString() {
        return toString(handle);
    }

    public void free() {
        if (handle != 0L) {
            free(handle);
            handle = 0L;
        }
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
    // package access
    //-------------------------------------------------------------------------

    long getHandle() {
        return handle;
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    /**
     * Decode a string of the form "shar1-"+base64 into a raw key
     */
    static native long fromString(String str) throws LiteCoreException;

    /**
     * Encodes a blob key to a string of the form "sha1-"+base64.
     */
    static native String toString(long blobKey);

    /**
     * Release C4BlobKey
     */
    static native void free(long blobKey);
}
