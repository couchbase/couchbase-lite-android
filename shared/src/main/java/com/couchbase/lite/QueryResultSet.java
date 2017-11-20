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
 * A result set representing the _query result. The result set is an iterator of
 * the {@code Result} objects.
 */
public class QueryResultSet implements Iterable<QueryResult> {
    //---------------------------------------------
    // static variables
    //---------------------------------------------
    private static final String TAG = Log.QUERY;

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Query _query;
    private C4QueryEnumerator _c4enum;
    private Map<String, Integer> _columnNames;
    private QueryResultContext _context;

    //---------------------------------------------
    // constructors
    //---------------------------------------------

    QueryResultSet(Query query, C4QueryEnumerator c4enum, Map<String, Integer> columnNames) {
        _query = query;
        _c4enum = c4enum;
        _columnNames = columnNames;
        _context = new QueryResultContext(query.getDatabase(), c4enum);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Move the cursor forward one row from its current row position.
     *
     * @return the Result after moving the cursor forward. Returns {@code null} value
     * if there are no more rows.
     */
    public QueryResult next() {
        try {
            if (_c4enum.next()) {
                return currentObject();
            } else
                return null;
        } catch (LiteCoreException e) {
            Log.w(TAG, "Query enumeration error: %s", e);
            return null;
        }
    }

    private QueryResult currentObject(){
        return new QueryResult(this, _c4enum, _context);
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------

    private class ResultSetIterator implements Iterator<QueryResult> {
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
        public QueryResult next() {
            return get(index++);
        }
    }

    @Override
    public Iterator<QueryResult> iterator() {
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
        if (_c4enum != null) {
            _c4enum.close();
            _c4enum.free();
            _c4enum = null;
        }
    }

    QueryResultSet refresh() throws CouchbaseLiteException {
        if (_query == null) return null;
        try {
            C4QueryEnumerator newEnum = _c4enum.refresh();
            return newEnum != null ? new QueryResultSet(_query, newEnum, _columnNames) : null;
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    int getCount() {
        try {
            return (int) _c4enum.getRowCount();
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertRuntimeException(e);
        }
    }

    QueryResult get(int index) {
        try {
            if (_c4enum.seek(index)) {
                return currentObject();
            } else
                return null;
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertRuntimeException(e);
        }
    }

    Map<String, Integer> getColumnNames() {
        return _columnNames;
    }

    Query getQuery() {
        return _query;
    }
}
