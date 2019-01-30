//
// FLArrayIterator.java
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

public class FLArrayIterator {
    private long handle = 0L; // hold pointer to FLArrayIterator
    private boolean managed = false;

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------
    public FLArrayIterator() {
        this.managed = false;
        this.handle = init();
    }

    public FLArrayIterator(long handle) {
        this.managed = true;
        this.handle = handle;
    }

    public void begin(FLArray array) {
        begin(array.getHandle(), handle);
    }

    public FLValue getValue() {
        long hValue = getValue(handle);
        return hValue != 0L ? new FLValue(hValue) : null;
    }

    public FLValue getValueAt(int index) {
        long hValue = getValueAt(handle, index);
        return hValue != 0L ? new FLValue(hValue) : null;
    }

    public boolean next() {
        return next(handle);
    }

    public void free() {
        if (handle != 0L && !managed) {
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
    // native methods
    //-------------------------------------------------------------------------

    // TODO: init() and begin() could be combined.

    /**
     * Create FLArrayIterator instance
     *
     * @return long (FLArrayIterator *)
     */
    static native long init();

    /**
     * Initializes a FLArrayIterator struct to iterate over an array.
     *
     * @param array (FLArray)
     * @param itr   (FLArrayIterator *)
     */
    static native void begin(long array, long itr);

    /**
     * Returns the current value being iterated over.
     *
     * @param itr (FLArrayIterator *)
     * @return long (FLValue)
     */
    static native long getValue(final long itr);

    /**
     * @param itr    (FLArrayIterator *)
     * @param offset
     * @return long (FLValue)
     */
    static native long getValueAt(final long itr, int offset);

    /**
     * Advances the iterator to the next value, or returns false if at the end.
     *
     * @param itr (FLArrayIterator *)
     */
    static native boolean next(long itr);

    /**
     * Free FLArrayIterator instance
     *
     * @param itr (FLArrayIterator *)
     */
    static native void free(long itr);
}
