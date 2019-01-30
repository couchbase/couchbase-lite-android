//
// C4BlobWriteStream.java
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
 * An open stream for writing data to a blob.
 */
public class C4BlobWriteStream {
    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    private long handle = 0L; // hold pointer to C4BlobWriteStream

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------
    C4BlobWriteStream(long handle) {
        if (handle == 0)
            throw new IllegalArgumentException("handle is 0");
        this.handle = handle;
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    /**
     * Writes data to a stream.
     */
    public void write(byte[] bytes) throws LiteCoreException {
        write(handle, bytes);
    }

    /**
     * Computes the blob-key (digest) of the data written to the stream. This should only be
     * called after writing the entire data. No more data can be written after this call.
     */
    public C4BlobKey computeBlobKey() throws LiteCoreException {
        return new C4BlobKey(computeBlobKey(handle));
    }

    /**
     * Adds the data written to the stream as a finished blob to the store, and returns its key.
     * If you skip this call, the blob will not be added to the store. (You might do this if you
     * were unable to receive all of the data from the network, or if you've called
     * c4stream_computeBlobKey and found that the data does not match the expected digest/key.)
     */
    public void install() throws LiteCoreException {
        install(handle);
    }

    /**
     * Closes a blob write-stream. If c4stream_install was not already called, the temporary file
     * will be deleted without adding the blob to the store.
     */
    public void close() {
        if (handle != 0L) {
            close(handle);
            handle = 0L;
        }
    }

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------
    @Override
    protected void finalize() throws Throwable {
        close();
        super.finalize();
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------
    static native void write(long writeStream, byte[] bytes) throws LiteCoreException;

    static native long computeBlobKey(long writeStream) throws LiteCoreException;

    static native void install(long writeStream) throws LiteCoreException;

    static native void close(long writeStream);
}
