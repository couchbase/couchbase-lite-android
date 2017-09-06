/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.lite;

import com.couchbase.lite.internal.bridge.LiteCoreBridge;
import com.couchbase.litecore.C4QueryEnumerator;
import com.couchbase.litecore.LiteCoreException;

import java.util.Iterator;
import java.util.Map;

/**
 * A result set representing the query result. The result set is an iterator of
 * the {@code Result} objects.
 */
public class ResultSet implements Iterable<Result>, CBLFLDataSource {
    //---------------------------------------------
    // static variables
    //---------------------------------------------
    private static final String TAG = Log.QUERY;

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Query query;
    private C4QueryEnumerator c4enum;
    private Map<String, Integer> columnNames;

    //---------------------------------------------
    // constructors
    //---------------------------------------------

    ResultSet(Query query, C4QueryEnumerator c4enum, Map<String, Integer> columnNames) {
        this.query = query;
        this.c4enum = c4enum;
        this.columnNames = columnNames;
    }

    // TODO: c4enum is better to be free.

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Move the cursor forward one row from its current row position.
     *
     * @return the Result after moving the cursor forward. Returns {@code null} value
     * if there are no more rows.
     */
    public Result next() {
        try {
            if (c4enum.next()) {
                return new Result(this, c4enum);
            } else
                return null;
        } catch (LiteCoreException e) {
            Log.w(TAG, "Query enumeration error: %s", e);
            return null;
        }
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------

    private class ResultSetIterator implements Iterator<Result> {
        private int index;
        private int count;

        public ResultSetIterator() {
            index = 0;
            count = getCount();
        }

        @Override
        public boolean hasNext() {
            return index < count;
        }

        @Override
        public Result next() {
            return get(index++);
        }
    }

    @Override
    public Iterator<Result> iterator() {
        return new ResultSetIterator();
    }

    //---------------------------------------------
    // protected methods
    //---------------------------------------------

    @Override
    protected void finalize() throws Throwable {
        release();
        super.finalize();
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    void release() {
        if (c4enum != null) {
            c4enum.close();
            c4enum.free();
            c4enum = null;
        }
    }

    ResultSet refresh() throws CouchbaseLiteException {
        if (query == null) return null;
        try {
            C4QueryEnumerator newEnum = c4enum.refresh();
            return newEnum != null ? new ResultSet(query, newEnum, columnNames) : null;
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    int getCount() {
        try {
            return (int) c4enum.getRowCount();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertRuntimeException(e);
        }
    }

    boolean isValidEnumerator() {
        return c4enum != null && !c4enum.isClosed();
    }

    Result get(int index) {
        try {
            if (c4enum.seek(index)) {
                return new Result(this, c4enum);
            } else
                return null;
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertRuntimeException(e);
        }
    }

    Map<String, Integer> getColumnNames() {
        return columnNames;
    }

    Query getQuery() {
        return query;
    }
}
