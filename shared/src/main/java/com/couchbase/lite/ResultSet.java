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

import com.couchbase.lite.internal.support.Log;
import com.couchbase.litecore.C4QueryEnumerator;
import com.couchbase.litecore.LiteCoreException;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

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
        _context = new ResultContext(query.getDatabase());
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

    public List<Result> toList() {
        int n = getCount();
        List<Result> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++)
            result.add(get(i));
        return result;
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------

    @Override
    public Iterator<Result> iterator() {
        return new Itr();
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
    void free() {
        if (_c4enum != null) {
            synchronized (getDatabase().getLock()) {
                _c4enum.close();
            }
            _c4enum.free();
            _c4enum = null;
        }
    }

    ResultSet refresh() throws CouchbaseLiteException {
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

    Map<String, Integer> getColumnNames() {
        return _columnNames;
    }

    Query getQuery() {
        return _query;
    }

    Database getDatabase() {
        return _query.getDatabase();
    }

    //---------------------------------------------
    // Private level access
    //---------------------------------------------

    private Result currentObject() {
        // NOTE: C4QueryEnumerator.getColumns() is just get pointer to columns
        return new Result(this, _c4enum.getColumns(), _context);
    }

    private Result get(int index) {
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

    //---------------------------------------------
    // Inner class
    //---------------------------------------------
    private class Itr implements Iterator<Result> {
        int cursor = 0; // index of next element to return
        int size = 0;

        private Itr() {
            cursor = 0;
            size = getCount();
        }

        private Itr(int index) {
            cursor = index;
            size = getCount();
        }

        @Override
        public boolean hasNext() {
            return cursor < size;
        }

        @Override
        public Result next() {
            int i = cursor;
            if (i >= size)
                throw new NoSuchElementException();
            cursor = i + 1;
            return get(i);
        }
    }
}

