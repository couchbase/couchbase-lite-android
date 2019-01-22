//
// AbstractQuery.java
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
package com.couchbase.lite;

import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.JsonUtils;
import com.couchbase.litecore.C4Query;
import com.couchbase.litecore.C4QueryEnumerator;
import com.couchbase.litecore.C4QueryOptions;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.fleece.AllocSlice;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

import static com.couchbase.lite.PropertyExpression.kCBLAllPropertiesName;

abstract class AbstractQuery implements Query {
    //---------------------------------------------
    // Load LiteCore library and its dependencies
    //---------------------------------------------
    static {
        NativeLibraryLoader.load();
    }

    //---------------------------------------------
    // static variables
    //---------------------------------------------
    private static final String TAG = Log.QUERY;

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Database database;
    private C4Query c4query;

    // NOTE:
    // https://sqlite.org/lang_select.html

    // SELECT
    private Select select;
    // FROM
    private DataSource from; // FROM table-or-subquery
    private Joins joins;     // FROM join-clause
    // WHERE
    private Expression where; // WHERE expr
    // GROUP BY
    private GroupBy groupBy; // GROUP BY expr(s)
    private Having having; // Having expr
    // ORDER BY
    private OrderBy orderBy; // ORDER BY ordering-term(s)
    // LIMIT
    private Limit limit; // LIMIT expr

    // PARAMETERS
    private Parameters parameters = null;

    // column names
    private Map<String, Integer> columnNames = null;

    // Live Query!!
    private LiveQuery liveQuery = null;

    private final Object lock = new Object(); // lock for thread-safety

    //---------------------------------------------
    // Constructor
    //---------------------------------------------
    AbstractQuery() {
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Returns a copies of the current parameters.
     */
    @Override
    public Parameters getParameters() {
        return parameters;
    }

    /**
     * Set parameters should copy the given parameters. Set a new parameter will
     * also re-execute the query if there is at least one listener listening for
     * changes.
     */
    @Override
    public void setParameters(Parameters parameters) {
        LiveQuery liveQuery;
        synchronized (lock) {
            this.parameters = parameters != null ? parameters.readonlyCopy() : null;
            liveQuery = this.liveQuery;
        }

        // https://github.com/couchbase/couchbase-lite-android/issues/1727
        // Shouldn't call start() method inside the lock to prevent deadlock:
        if (liveQuery != null)
            liveQuery.start();
    }

    /**
     * Executes the query. The returning a result set that enumerates result rows one at a time.
     * You can run the query any number of times, and you can even have multiple ResultSet active at
     * once.
     * <p>
     * The results come from a snapshot of the database taken at the moment the run() method
     * is called, so they will not reflect any changes made to the database afterwards.
     * </p>
     *
     * @return the ResultSet for the query result.
     * @throws CouchbaseLiteException if there is an error when running the query.
     */
    @Override
    public ResultSet execute() throws CouchbaseLiteException {
        try {
            C4QueryOptions options = new C4QueryOptions();
            if (parameters == null)
                parameters = new Parameters();

            AllocSlice params = parameters.encode();
            C4QueryEnumerator c4enum;
            synchronized (getDatabase().getLock()) {
                check();
                c4enum = c4query.run(options, params);
            }
            return new ResultSet(this, c4enum, columnNames);
        } catch (LiteCoreException e) {
            throw CBLStatus.convertException(e);
        }
    }

    /**
     * Returns a string describing the implementation of the compiled query.
     * This is intended to be read by a developer for purposes of optimizing the query, especially
     * to add database indexes. It's not machine-readable and its format may change.
     * As currently implemented, the result is two or more lines separated by newline characters:
     * * The first line is the SQLite SELECT statement.
     * * The subsequent lines are the output of SQLite's "EXPLAIN QUERY PLAN" command applied to that
     * statement; for help interpreting this, see https://www.sqlite.org/eqp.html . The most
     * important thing to know is that if you see "SCAN TABLE", it means that SQLite is doing a
     * slow linear scan of the documents instead of using an index.
     *
     * @return a string describing the implementation of the compiled query.
     * @throws CouchbaseLiteException if an error occurs
     */
    @Override
    public String explain() throws CouchbaseLiteException {
        synchronized (getDatabase().getLock()) {
            check();
            return c4query.explain();
        }
    }

    /**
     * Adds a query change listener. Changes will be posted on the main queue.
     *
     * @param listener The listener to post changes.
     * @return An opaque listener token object for removing the listener.
     */
    @Override
    public ListenerToken addChangeListener(QueryChangeListener listener) {
        return addChangeListener(null, listener);
    }

    /**
     * Adds a query change listener with the dispatch queue on which changes
     * will be posted. If the dispatch queue is not specified, the changes will be
     * posted on the main queue.
     *
     * @param executor The executor object that calls listener. If null, use default executor.
     * @param listener The listener to post changes.
     * @return An opaque listener token object for removing the listener.
     */
    @Override
    public ListenerToken addChangeListener(Executor executor, QueryChangeListener listener) {
        if (listener == null)
            throw new IllegalArgumentException("listener parameter is null.");
        return liveQuery().addChangeListener(executor, listener);
    }

    /**
     * Removes a change listener wih the given listener token.
     *
     * @param token The listener token.
     */
    @Override
    public void removeChangeListener(ListenerToken token) {
        if (token == null)
            throw new IllegalArgumentException("The given token is null");
        liveQuery().removeChangeListener(token);
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s[json=%s]", this.getClass().getSimpleName(), _asJSON());
    }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------
    Database getDatabase() {
        if (database == null)
            database = (Database) from.getSource();
        return database;
    }

    void setSelect(Select select) {
        this.select = select;
    }

    void setFrom(DataSource from) {
        this.from = from;
    }

    void setJoins(Joins joins) {
        this.joins = joins;
    }

    void setWhere(Expression where) {
        this.where = where;
    }

    void setGroupBy(GroupBy groupBy) {
        this.groupBy = groupBy;
    }

    void setHaving(Having having) {
        this.having = having;
    }

    void setOrderBy(OrderBy orderBy) {
        this.orderBy = orderBy;
    }

    void setLimit(Limit limit) {
        this.limit = limit;
    }

    void copy(AbstractQuery query) {
        this.select = query.select;
        this.from = query.from;
        this.joins = query.joins;
        this.where = query.where;
        this.groupBy = query.groupBy;
        this.having = query.having;
        this.orderBy = query.orderBy;
        this.limit = query.limit;

        this.parameters = query.parameters;
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------
    private void check() throws CouchbaseLiteException {
        synchronized (lock) {
            if (c4query != null)
                return;

            database = (Database) from.getSource();
            String json = encodeAsJSON();
            Log.v(TAG, "Query encoded as %s", json);
            if (json == null)
                throw new CouchbaseLiteException("Failed to generate JSON query.");

            if (columnNames == null)
                columnNames = generateColumnNames();

            try {
                c4query = database.getC4Database().createQuery(json);
            } catch (LiteCoreException e) {
                throw CBLStatus.convertException(e);
            }
        }
    }

    private Map<String, Integer> generateColumnNames() throws CouchbaseLiteException {
        Map<String, Integer> map = new HashMap<>();
        int index = 0;
        int provisionKeyIndex = 0;
        for (SelectResult selectResult : this.select.getSelectResults()) {

            String name = selectResult.getColumnName();

            if (name != null && name.equals(kCBLAllPropertiesName))
                name = from.getColumnName();

            if (name == null)
                name = String.format(Locale.ENGLISH, "$%d", ++provisionKeyIndex);
            if (map.containsKey(name)) {
                String desc = String.format(Locale.ENGLISH, "Duplicate select result named %s", name);
                throw new CouchbaseLiteException(desc, CBLError.Domain.CBLErrorDomain, CBLError.Code.CBLErrorInvalidQuery);
            }
            map.put(name, index);
            index++;
        }
        return map;
    }

    private String encodeAsJSON() {
        try {
            return JsonUtils.toJson(_asJSON()).toString();
        } catch (JSONException e) {
            Log.w(TAG, "Error when encoding the query as a json string", e);
        }
        return null;
    }

    private Map<String, Object> _asJSON() {
        Map<String, Object> json = new HashMap<String, Object>();

        // DISTINCT:
        if (select != null && select.isDistinct())
            json.put("DISTINCT", true);

        // result-columns / SELECT-RESULTS
        if (select != null && select.hasSelectResults())
            json.put("WHAT", select.asJSON());

        // JOIN:
        List<Object> f = new ArrayList<>();
        Map<String, Object> as = from.asJSON();
        if (as.size() > 0)
            f.add(as);
        if (joins != null) {
            f.addAll((List<Object>) joins.asJSON());
        }
        if (f.size() > 0)
            json.put("FROM", f);

        if (where != null)
            json.put("WHERE", where.asJSON());

        if (groupBy != null)
            json.put("GROUP_BY", groupBy.asJSON());

        if (having != null)
            json.put("HAVING", having.asJSON());

        if (orderBy != null)
            json.put("ORDER_BY", orderBy.asJSON());

        if (limit != null) {
            List<Object> list = (List<Object>) limit.asJSON();
            json.put("LIMIT", list.get(0));
            if (list.size() > 1)
                json.put("OFFSET", list.get(1));
        }
        return json;
    }

    private LiveQuery liveQuery() {
        synchronized (lock) {
            if (liveQuery == null)
                liveQuery = new LiveQuery(this);
            return liveQuery;
        }
    }

    private void free() {
        synchronized (lock) {
            if (c4query != null && getDatabase() != null) {
                synchronized (getDatabase().getLock()) {
                    c4query.free();
                }
                c4query = null;
            }
        }
    }
}
