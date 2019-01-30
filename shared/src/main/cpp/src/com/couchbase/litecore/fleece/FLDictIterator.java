//
// FLDictIterator.java
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


public class FLDictIterator {
    private long handle = 0; // hold pointer to FLDictIterator

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------
    public FLDictIterator() {
        handle = init();
    }

    public void begin(FLDict dict) {
        begin(dict.getHandle(), handle);
    }

    public String getKeyString() {
        return getKeyString(handle);
    }

    public FLValue getValue() {
        long hValue = getValue(handle);
        return hValue != 0L ? new FLValue(hValue) : null;
    }

    public boolean next() {
        return next(handle);
    }

    public long getCount() {
        return getCount(handle);
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
    // native methods
    //-------------------------------------------------------------------------


    /**
     * Create FLDictIterator instance
     *
     * @return long (FLDictIterator *)
     */
    static native long init();

    /**
     * Initializes a FLDictIterator struct to iterate over a dictionary.
     *
     * @param dict (FLDict)
     * @param itr  (FLDictIterator *)
     */
    static native void begin(long dict, long itr);

    /**
     * Returns the key's string value.
     *
     * @param itr (FLDictIterator *)
     * @return key string
     */
    static native String getKeyString(final long itr);

    /**
     * Returns the current value being iterated over.
     *
     * @param itr (FLDictIterator *)
     * @return long (FLValue)
     */
    static native long getValue(final long itr);

    /**
     * Advances the iterator to the next value, or returns false if at the end.
     *
     * @param itr (FLDictIterator *)
     */
    static native boolean next(long itr);

    /**
     * Returns the number of items remaining to be iterated, including the current one.
     *
     * @param itr (FLDictIterator *)
     */
    static native long getCount(long itr);

    /**
     * Free FLDictIterator instance
     *
     * @param itr (FLDictIterator *)
     */
    static native void free(long itr);
}
