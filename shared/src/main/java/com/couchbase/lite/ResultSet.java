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
public class ResultSet implements Iterable<Result> {
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
    private ResultContext _context;

    //---------------------------------------------
    // constructors
    //---------------------------------------------

    ResultSet(Query query, C4QueryEnumerator c4enum, Map<String, Integer> columnNames) {
        _query = query;
        _c4enum = c4enum;
        _columnNames = columnNames;
        _context = new ResultContext(query.getDatabase(), c4enum);
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
    public Result next() {
        if (_query == null)
            throw new IllegalStateException("_query variable is null");

        synchronized (getDatabase().getLock()) {
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
    }

    private Result currentObject() {
        // NOTE: C4QueryEnumerator.getColumns() is just get pointer to columns
        return new Result(this, _c4enum.getColumns(), _context);
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
    // public but not public API method.
    //---------------------------------------------

    public void free() {
        if (_c4enum != null) {
            synchronized (getDatabase().getLock()) {
                _c4enum.close();
            }
            _c4enum.free();
            _c4enum = null;
        }
    }

    public ResultSet refresh() throws CouchbaseLiteException {
        if (_query == null)
            throw new IllegalStateException("_query variable is null");

        synchronized (getDatabase().getLock()) {
            try {
                C4QueryEnumerator newEnum = _c4enum.refresh();
                return newEnum != null ? new ResultSet(_query, newEnum, _columnNames) : null;
            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertException(e);
            }
        }
    }

    //---------------------------------------------
    // protected methods
    //---------------------------------------------

    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    int columnCount() {
        return _columnNames.size();
    }

    int getCount() {
        if (_query == null)
            throw new IllegalStateException("_query variable is null");

        synchronized (getDatabase().getLock()) {
            try {
                return (int) _c4enum.getRowCount();
            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertRuntimeException(e);
            }
        }

    }

    Result get(int index) {
        if (_query == null)
            throw new IllegalStateException("_query variable is null");

        synchronized (getDatabase().getLock()) {
            try {
                if (_c4enum.seek(index)) {
                    return currentObject();
                } else
                    return null;
            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertRuntimeException(e);
            }
        }
    }

    Map<String, Integer> getColumnNames() {
        return _columnNames;
    }

    Query getQuery() {
        return _query;
    }

    Database getDatabase() {
        return _query.getDatabase();
    }
}

