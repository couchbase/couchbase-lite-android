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
    private AbstractQuery query;
    private C4QueryEnumerator c4enum;
    private Map<String, Integer> columnNames;
    private ResultContext context;

    //---------------------------------------------
    // constructors
    //---------------------------------------------

    ResultSet(AbstractQuery query, C4QueryEnumerator c4enum, Map<String, Integer> columnNames) {
        this.query = query;
        this.c4enum = c4enum;
        this.columnNames = columnNames;
        this.context = new ResultContext(query.getDatabase());
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Move the cursor forward one row from its current row position.
     * Caution: next() method and iterator() method share same data structure.
     * Please don't use them together.
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
                    return currentObject();
                } else
                    return null;
            } catch (LiteCoreException e) {
                Log.w(TAG, "Query enumeration error: %s", e.toString());
                return null;
            }
        }
    }

    /**
     * Return List of Results. List is unmodifiable and only supports
     * int get(int index), int size(), boolean isEmpty() and Iterator<Result> iterator() methods.
     * Once called allResults(), next() method return null. Don't call next() and allResults()
     * together.
     *
     * @return List of Results
     */
    public List<Result> allResults() {
        List<Result> results = new ArrayList<>();
        Result result;
        while ((result = next()) != null)
            results.add(result);
        return results;
    }

    //---------------------------------------------
    // Iterable implementation
    //---------------------------------------------

    /**
     * Return Iterator of Results.
     * Once called iterator(), next() method return null. Don't call next() and iterator()
     * together.
     *
     * @return an iterator over the elements in this list in proper sequence
     */
    @Override
    public Iterator<Result> iterator() {
        return allResults().iterator();
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

    Map<String, Integer> getColumnNames() {
        return columnNames;
    }

    AbstractQuery getQuery() {
        return query;
    }

    //---------------------------------------------
    // Private level access
    //---------------------------------------------
    private Database getDatabase() {
        return query.getDatabase();
    }

    private Result currentObject() {
        return new Result(this, c4enum, context);
    }
}

