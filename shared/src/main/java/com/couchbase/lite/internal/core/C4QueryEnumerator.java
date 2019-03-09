//
// C4QueryEnumerator.java
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
package com.couchbase.lite.internal.core;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.fleece.FLArrayIterator;


public class C4QueryEnumerator {
    static native boolean next(long handle) throws LiteCoreException;

    static native long getRowCount(long handle) throws LiteCoreException;

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    static native boolean seek(long handle, long rowIndex) throws LiteCoreException;

    static native long refresh(long handle) throws LiteCoreException;

    static native void close(long handle);

    static native void free(long handle);

    // FLArrayIterator columns
    // The columns of this result, in the same order as in the query's `WHAT` clause.
    static native long getColumns(long handle);

    // uint64_t missingColumns
    // A bitmap where a 1 bit represents a column whose value is MISSING.
    // This is how you tell a missing property value from a value that's JSON 'null',
    // since the value in the `columns` array will be a Fleece `null` either way.
    static native long getMissingColumns(long handle);

    // -- Accessor methods to C4QueryEnumerator --

    // uint32_t fullTextMatchCount
    // The number of full-text matches (i.e. the number of items in `fullTextMatches`)
    static native long getFullTextMatchCount(long handle);

    // const C4FullTextMatch *fullTextMatches
    // Array with details of each full-text match
    static native long getFullTextMatch(long handle, int idx);
    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    private long handle; // hold pointer to C4QueryEnumerator

    //-------------------------------------------------------------------------
    // Constructor
    //-------------------------------------------------------------------------
    C4QueryEnumerator(long handle) {
        this.handle = handle;
    }

    public boolean next() throws LiteCoreException {
        final boolean ok = next(handle);
        // NOTE: Please keep following line of code for a while.
        //if (!ok)
        //    handle = 0;
        return ok;
    }

    //-------------------------------------------------------------------------
    // native methods
    //-------------------------------------------------------------------------

    public long getRowCount() throws LiteCoreException {
        return getRowCount(handle);
    }

    public boolean seek(long rowIndex) throws LiteCoreException {
        return seek(handle, rowIndex);
    }

    public C4QueryEnumerator refresh() throws LiteCoreException {
        // handle is closed or reached end.
        if (handle == 0) { return null; }
        final long newHandle = refresh(handle);
         return (newHandle == 0) ? null : new C4QueryEnumerator(newHandle);
    }

    public void close() {
        if (handle != 0) { close(handle); }
    }

    public void free() {
        if (handle != 0L) {
            free(handle);
            handle = 0L;
        }
    }

    // NOTE: FLArrayIterator is member variable of C4QueryEnumerator. Not necessary to release.
    public FLArrayIterator getColumns() {
        return new FLArrayIterator(getColumns(handle));
    }

    // -- Accessor methods to C4QueryEnumerator --
    // C4QueryEnumerator
    // A query result enumerator
    // Created by c4db_query. Must be freed with c4queryenum_free.
    // The fields of this struct represent the current matched index row, and are valid until the
    // next call to c4queryenum_next or c4queryenum_free.

    public long getMissingColumns() {
        return getMissingColumns(handle);
    }

    public long getFullTextMatchCount() {
        return getFullTextMatchCount(handle);
    }

    public C4FullTextMatch getFullTextMatchs(int idx) {
        return new C4FullTextMatch(getFullTextMatch(handle, idx));
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
}
