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

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
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
    private boolean randomAccess;

    //---------------------------------------------
    // constructors
    //---------------------------------------------

    ResultSet(Query query, C4QueryEnumerator c4enum, Map<String, Integer> columnNames) {
        this.query = query;
        this.c4enum = c4enum;
        this.columnNames = columnNames;
        this.context = new ResultContext(query.getDatabase());
        this.randomAccess = false;
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

        if (randomAccess)
            return null;

        synchronized (getDatabase().getLock()) {
            try {
                if (c4enum.next()) {
                    return currentObject();
                } else
                    return null;
            } catch (LiteCoreException e) {
                Log.w(TAG, "Query enumeration error: %s", e);
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
        randomAccess = true;
        return new ResultSetList();
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

    // access from LiveQuery

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

    // access from Result

    int columnCount() {
        return columnNames.size();
    }

    Map<String, Integer> getColumnNames() {
        return columnNames;
    }

    Query getQuery() {
        return query;
    }

    //---------------------------------------------
    // Private level access
    //---------------------------------------------
    private Database getDatabase() {
        return query.getDatabase();
    }

    private Result currentObject() {
        // NOTE: C4QueryEnumerator.getColumns() is just get pointer to columns
        return new Result(this, c4enum.getColumns(), context);
    }

    private int size() {
        if (query == null)
            throw new IllegalStateException("_query variable is null");

        synchronized (getDatabase().getLock()) {
            try {
                return (int) c4enum.getRowCount();
            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertRuntimeException(e);
            }
        }
    }

    private Result get(int index) {
        if (query == null)
            throw new IllegalStateException("_query variable is null");
        synchronized (getDatabase().getLock()) {
            try {
                if (c4enum.seek(index)) {
                    return currentObject();
                } else
                    return null;

            } catch (LiteCoreException e) {
                throw LiteCoreBridge.convertRuntimeException(e);
            }
        }
    }

    private class ResultSetList implements List<Result> {
        private int size;

        ResultSetList() {
            this.size = ResultSet.this.size();
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public boolean isEmpty() {
            return size() == 0;
        }

        @Override
        public Result get(int index) {
            return ResultSet.this.get(index);
        }

        private class ResultSetListIterator implements Iterator<Result> {
            private int currentIndex = 0;

            @Override
            public boolean hasNext() {
                return currentIndex < ResultSetList.this.size;
            }

            @Override
            public Result next() {
                return get(currentIndex++);
            }
        }

        @Override
        public Iterator<Result> iterator() {
            return new ResultSetListIterator();
        }

        @Override
        public boolean contains(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object[] toArray() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T[] toArray(T[] ts) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean add(Result strings) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(Collection<?> collection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends Result> collection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(int i, Collection<? extends Result> collection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> collection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean retainAll(Collection<?> collection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result set(int i, Result strings) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void add(int i, Result strings) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result remove(int i) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int indexOf(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int lastIndexOf(Object o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ListIterator<Result> listIterator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ListIterator<Result> listIterator(int i) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Result> subList(int i, int i1) {
            throw new UnsupportedOperationException();
        }
    }
}

