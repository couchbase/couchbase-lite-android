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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.couchbase.lite.Expression.PropertyExpression.kCBLAllPropertiesName;
import static com.couchbase.lite.Status.CBLErrorDomain;
import static com.couchbase.lite.Status.InvalidQuery;

/**
 * A database query used for querying data from the database. The query statement of the Query
 * object can be fluently constructed by calling the static select methods.
 */
public class Query {
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
    private Parameters parameters;

    // column names
    private Map<String, Integer> columnNames = null;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------
    Query() {
        parameters = new Parameters();
    }

    Query(Query query) {
        copy(query);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Create a SELECT ALL (*) query. You can then call the Select object's methods such as
     * from() method to construct the complete Query object.
     *
     * @return the Select object.
     */
    public static Select select(SelectResult... results) {
        return new Select(false, results);
    }

    /**
     * Create a SELECT DISTINCT ALL (*) query. You can then call the Select object's methods such as
     * from() method to construct the complete Query object.
     *
     * @return the Select object.
     */
    public static Select selectDistinct(SelectResult... results) {
        return new Select(true, results);
    }

    public Parameters parameters() {
        return parameters;
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
            String paramJSON = parameters.encodeAsJSON();
            C4QueryEnumerator c4enum = c4query.run(options, paramJSON);
            return new ResultSet(this, c4enum, columnNames);
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

    public String profile() {
        // TODO:
        throw new UnsupportedOperationException("Not implemented yet.");
    }

    public LiveQuery toLive() {
        return new LiveQuery(new Query(this));
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "%s[json=%s]", this.getClass().getSimpleName(), asJSON());
    }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

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

    void copy(Query query) {
        this.select = query.select;
        this.from = query.from;
        this.joins = query.joins;
        this.where = query.where;
        this.groupBy = query.groupBy;
        this.having = query.having;
        this.orderBy = query.orderBy;
        this.limit = query.limit;

        this.parameters = query.parameters.copy();
    }

    Database getDatabase() {
        if (database == null)
            database = (Database) from.getSource();
        return database;
    }

    C4Query getC4Query() {
        return c4query;
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------

    private void check() throws CouchbaseLiteException {
        database = (Database) from.getSource();
        String json = encodeAsJSON();
        Log.v(TAG, "Query encoded as %s", json);
        if (json == null)
            throw new CouchbaseLiteException("Failed to generate JSON query.");

        if (columnNames == null)
            columnNames = generateColumnNames();

        C4Query query = null;
        try {
            query = database.getC4Database().createQuery(json);
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertException(e);
        } finally {
            if (c4query != null)
                c4query.free();
        }
        c4query = query;
    }

    private Map<String, Integer> generateColumnNames() throws CouchbaseLiteException {
        Map<String, Integer> map = new HashMap<>();
        int index = 0;
        int provisionKeyIndex = 0;
        for (SelectResult selectResult : this.select.getSelectResults()) {

            String name = selectResult.getColumnName();

            if (name != null && name.equals(kCBLAllPropertiesName))
                name = from.getColumnName();

            Log.e(TAG, "generateColumnNames() name -> %s", name);
            if (name == null)
                name = String.format(Locale.ENGLISH, "$%d", ++provisionKeyIndex);
            if (map.containsKey(name)) {
                String desc = String.format(Locale.ENGLISH, "Duplicate select result named %s", name);
                throw new CouchbaseLiteException(desc, CBLErrorDomain, InvalidQuery);
            }
            map.put(name, index);
            index++;
        }
        return map;
    }

    private String encodeAsJSON() {
        try {
            return JsonUtils.toJson(asJSON()).toString();
        } catch (JSONException e) {
            Log.w(TAG, "Error when encoding the query as a json string", e);
        }
        return null;
    }

    private Map<String, Object> asJSON() {
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
}
