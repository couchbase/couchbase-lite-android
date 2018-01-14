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

import java.util.Arrays;

/**
 * A Where represents the WHERE clause of the query for filtering the query result.
 */
public final class Where extends Query implements GroupByRouter, OrderByRouter, LimitRouter {

    //---------------------------------------------
    // Constructor
    //---------------------------------------------

    Where(Query query, Expression where) {
        copy(query);
        setWhere(where);
    }

    //---------------------------------------------
    // implementation of GroupByRouter
    //---------------------------------------------

    /**
     * Create and chain a GROUP BY component to group the query result.
     *
     * @param expressions The expression objects.
     * @return The GroupBy object.
     */
    @Override
    public GroupBy groupBy(Expression... expressions) {
        return new GroupBy(this, Arrays.asList(expressions));
    }

    //---------------------------------------------
    // implementation of OrderByRouter
    //---------------------------------------------

    /**
     * Create and chain an ORDER BY component for specifying the ORDER BY clause of the query.
     *
     * @param orderings an array of the ORDER BY expressions.
     * @return the ORDER BY component.
     */
    @Override
    public OrderBy orderBy(Ordering... orderings) {
        return new OrderBy(this, Arrays.asList(orderings));
    }

    //---------------------------------------------
    // implementation of LimitRouter
    //---------------------------------------------

    /**
     * Create and chain a LIMIT component to limit the number query results.
     *
     * @param limit The limit Expression object
     * @return The Limit object.
     */
    @Override
    public Limit limit(Expression limit) {
        return new Limit(this, limit, null);
    }

    /**
     * Create and chain a LIMIT component to skip the returned results for the given offset
     * position and to limit the number of results to not more than the given limit value.
     *
     * @param limit  The limit Expression object
     * @param offset The offset Expression object
     * @return The Limit object.
     */
    @Override
    public Limit limit(Expression limit, Expression offset) {
        return new Limit(this, limit, offset);
    }
}
