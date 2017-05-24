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
import com.couchbase.lite.internal.support.JsonUtils;
import com.couchbase.litecore.C4Query;
import com.couchbase.litecore.C4QueryEnumerator;
import com.couchbase.litecore.C4QueryOptions;
import com.couchbase.litecore.LiteCoreException;

import org.json.JSONException;

import java.util.HashMap;
import java.util.Map;

/**
 * A database query used for querying data from the database. The query statement of the Query
 * object can be fluently constructed by calling the static select methods.
 */
public class Query {
    private static final String LOG_TAG = Log.QUERY;

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    private Database database;
    private C4Query c4query;
    private Select select;
    private DataSource from;
    private Expression where;
    private OrderBy orderBy;
    private boolean distinct;

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Create a SELECT ALL (*) query. You can then call the Select object's methods such as
     * from() method to construct the complete Query object.
     *
     * @return the Select object.
     */
    public static Select select() {
        return new Select(false);
    }

    /**
     * Create a SELECT DISTINCT ALL (*) query. You can then call the Select object's methods such as
     * from() method to construct the complete Query object.
     *
     * @return the Select object.
     */
    public static Select selectDistinct() {
        return new Select(true);
    }

    /**
     * Runs the query. The returning a result set that enumerates result rows one at a time.
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
    public ResultSet run() throws CouchbaseLiteException {
        if (c4query == null)
            check();
        try {
            C4QueryOptions options = new C4QueryOptions();
            C4QueryEnumerator c4enum = c4query.run(options, null);
            return new ResultSet(this, c4enum);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
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
    public String explain() throws CouchbaseLiteException {
        if (c4query == null)
            check();
        return c4query.explain();
    }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    protected Select getSelect() {
        return select;
    }

    protected void setSelect(Select select) {
        this.select = select;
    }

    protected DataSource getFrom() {
        return from;
    }

    protected void setFrom(DataSource from) {
        this.from = from;
    }

    protected Expression getWhere() {
        return where;
    }

    protected void setWhere(Expression where) {
        this.where = where;
    }

    protected OrderBy getOrderBy() {
        return orderBy;
    }

    protected void setOrderBy(OrderBy orderBy) {
        this.orderBy = orderBy;
    }

    protected boolean isDistinct() {
        return distinct;
    }

    protected void setDistinct(boolean distinct) {
        this.distinct = distinct;
    }

    protected void copy(Query query) {
        this.select = query.select;
        this.where = query.where;
        this.from = query.from;
        this.orderBy = query.orderBy;
        this.distinct = query.distinct;
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    /* package */ Database getDatabase() {
        return database;
    }

    /* package */ C4Query getC4Query() {
        return c4query;
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    private void check() throws CouchbaseLiteException {
        database = (Database) from.getSource();
        String json = encodeAsJSON();
        Log.v(LOG_TAG, "Query encoded as %s", json);
        try {
            c4query = new C4Query(database.internal(), json);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        }
    }

    private String encodeAsJSON() {
        try {
            return JsonUtils.toJson(asJSON()).toString();
        } catch (JSONException e) {
            Log.w(LOG_TAG, "Error when encoding the query as a json string", e);
        }
        return null;
    }

    private Map<String, Object> asJSON() {
        Map<String, Object> json = new HashMap<String, Object>();
        if (distinct)
            json.put("DISTINCT", true);

        if (where != null)
            json.put("WHERE", where.asJSON());

        if (orderBy != null)
            json.put("ORDER_BY", orderBy.asJSON());
        return json;
    }
}
