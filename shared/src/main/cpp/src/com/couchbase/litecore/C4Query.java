//
// C4Query.java
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

import com.couchbase.litecore.fleece.AllocSlice;

public class C4Query {
    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    long _handle = 0L; // hold pointer to C4Query

    //-------------------------------------------------------------------------
    // Constructors
    //-------------------------------------------------------------------------

    C4Query(long db, String expression) throws LiteCoreException {
        _handle = init(db, expression);
    }

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    public void free() {
        if (_handle != 0L) {
            free(_handle);
            _handle = 0L;
        }
    }

    public String explain() {
        return explain(_handle);
    }

    public int columnCount() {
        return columnCount(_handle);
    }

    public C4QueryEnumerator run(C4QueryOptions options, AllocSlice parameters)
            throws LiteCoreException {
        if (parameters == null)
            parameters = new AllocSlice(null);
        return new C4QueryEnumerator(run(_handle, options.isRankFullText(), parameters.getHandle()));
    }

    public byte[] getFullTextMatched(C4FullTextMatch match) throws LiteCoreException {
        return getFullTextMatched(_handle, match._handle);
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

    //////// DATABASE QUERIES:

    /**
     * @param db
     * @param expression
     * @return C4Query*
     * @throws LiteCoreException
     */
    static native long init(long db, String expression) throws LiteCoreException;

    /**
     * Free C4Query* instance
     *
     * @param handle (C4Query*)
     */
    static native void free(long handle);

    /**
     * @param handle (C4Query*)
     * @return C4StringResult
     */
    static native String explain(long handle);

    /**
     * Returns the number of columns (the values specified in the WHAT clause) in each row.
     *
     * @param handle (C4Query*)
     * @return the number of columns
     */
    static native int columnCount(long handle);

    //////// RUNNING QUERIES:

    /**
     * @param handle
     * @param rankFullText
     * @param parameters
     * @return C4QueryEnumerator*
     * @throws LiteCoreException
     */
    static native long run(long handle, boolean rankFullText, /*AllocSlice*/ long parameters)
            throws LiteCoreException;

    /**
     * Given a docID and sequence number from the enumerator, returns the text that was emitted
     * during indexing.
     */
    static native byte[] getFullTextMatched(long handle, long fullTextMatch) throws LiteCoreException;

    //////// INDEXES:

    // - Creates a database index, to speed up subsequent queries.

    static native boolean createIndex(long db,
                                      String name,
                                      String expressionsJSON,
                                      int indexType,
                                      String language,
                                      boolean ignoreDiacritics)
            throws LiteCoreException;

    static native void deleteIndex(long db, String name) throws LiteCoreException;

    /**
     * Gets a fleece encoded array of indexes in the given database
     * that were created by `c4db_createIndex`
     *
     * @param db
     * @return pointer to FLValue
     * @throws LiteCoreException
     */
    static native long getIndexes(long db) throws LiteCoreException;
}
