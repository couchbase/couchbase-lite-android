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
    private Query query;
    private C4QueryEnumerator c4enum;
    private Map<String, Integer> columnNames;
    private ResultContext context;
    private int count = -1;
    private int currentPosition = 0; // used for Iterator.hasNext();

    //---------------------------------------------
    // constructors
    //---------------------------------------------

    ResultSet(Query query, C4QueryEnumerator c4enum, Map<String, Integer> columnNames) {
        this.query = query;
        this.c4enum = c4enum;
        this.columnNames = columnNames;
        this.context = new ResultContext(query.getDatabase());
        this.count = -1;
        this.currentPosition = 0;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Move the cursor forward one row from its current row position.
     * <p>
     * Caution: next() method and iterator() method share same data structure. Please don't use them together.
     *
     * @return the Result after moving the cursor forward. Returns {@code null} value
     * if there are no more rows.
     */
    public Result next() {
        if (query == null)
            throw new IllegalStateException("_query variable is null");

        synchronized (getDatabase().getLock()) {
            try {
                if (c4enum.next()) {
                    currentPosition++;
                    return currentObject();
                } else
                    return null;
            } catch (LiteCoreException e) {
                Log.w(TAG, "Query enumeration error: %s", e);
                return null;
            }
        }
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------

    /**
     * Caution: next() method and iterator() method share same data structure. Please don't use them together.
     */
    @Override
    public Iterator<Result> iterator() {
        return new Iterator<Result>() {
            @Override
            public boolean hasNext() {
                return currentPosition < getCount();
            }

            @Override
            public Result next() {
                return ResultSet.this.next();
            }
        };
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
        if (c4enum != null) {
            synchronized (getDatabase().getLock()) {
                c4enum.close();
            }
            c4enum.free();
            c4enum = null;
        }
    }

    ResultSet refresh() throws CouchbaseLiteException {
        if (query == null)
            throw new IllegalStateException("_query variable is null");

        synchronized (getDatabase().getLock()) {
            try {
                C4QueryEnumerator newEnum = c4enum.refresh();
                return newEnum != null ? new ResultSet(query, newEnum, columnNames) : null;
            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertException(e);
            }
        }
    }

    int columnCount() {
        return columnNames.size();
    }

    int getCount() {
        if (count == -1) {
            if (query == null)
                throw new IllegalStateException("_query variable is null");

            synchronized (getDatabase().getLock()) {
                try {
                    count = (int) c4enum.getRowCount();
                } catch (LiteCoreException e) {
                    throw LiteCoreBridge.convertRuntimeException(e);
                }
            }
        }
        return count;
    }

    Map<String, Integer> getColumnNames() {
        return columnNames;
    }

    Query getQuery() {
        return query;
    }

    Database getDatabase() {
        return query.getDatabase();
    }

    Result currentObject() {
        // NOTE: C4QueryEnumerator.getColumns() is just get pointer to columns
        return new Result(this, c4enum.getColumns(), context);
    }
}

