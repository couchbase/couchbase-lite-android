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
 * A From represents a FROM clause for specifying the data source of the query.
 */
public final class From extends Query implements JoinRouter, WhereRouter, GroupByRouter, OrderByRouter, LimitRouter {
    //---------------------------------------------
    // Constructor
    //---------------------------------------------

    From(Query query, DataSource dataSource) {
        copy(query);
        setFrom(dataSource);
    }

    //---------------------------------------------
    // implementation of JoinRouter
    //---------------------------------------------

    /**
     * Creates and chains a Joins object for specifying the JOIN clause of the query.
     *
     * @param joins The Join objects.
     * @return The Joins object that represents the JOIN clause of the query.
     */
    @Override
    public Joins join(Join... joins) {
        return new Joins(this, Arrays.asList(joins));
    }

    //---------------------------------------------
    // implementation of WhereRouter
    //---------------------------------------------

    /**
     * Create and chain a WHERE component for specifying the WHERE clause of the query.
     *
     * @param expression the WHERE clause expression.
     * @return the WHERE component.
     */
    @Override
    public Where where(Expression expression) {
        return new Where(this, expression);
    }

    //---------------------------------------------
    // implementation of GroupByRouter
    //---------------------------------------------

    /**
     * Creates and chains a GroupBy object to group the query result.
     *
     * @param expressions The group by expression.
     * @return The GroupBy object that represents the GROUP BY clause of the query.
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
     * Creates and chains a Limit object to limit the number query results.
     *
     * @param limit The limit expression.
     * @return The Limit object that represents the LIMIT clause of the query.
     */
    @Override
    public Limit limit(Object limit) {
        return new Limit(this, limit, null);
    }

    /**
     * Creates and chains a Limit object to skip the returned results for the given offset
     * position and to limit the number of results to not more than the given limit value.
     *
     * @param limit  The limit expression.
     * @param offset The offset expression.
     * @return The Limit object that represents the LIMIT clause of the query.
     */
    @Override
    public Limit limit(Object limit, Object offset) {
        return new Limit(this, limit, offset);
    }
}
