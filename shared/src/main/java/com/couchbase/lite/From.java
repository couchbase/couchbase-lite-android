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
public class From extends Query implements JoinRouter, WhereRouter, GroupByRouter, OrderByRouter, LimitRouter {
    /* package */ From(Query query, DataSource dataSource) {
        copy(query);
        setFrom(dataSource);
    }

    //---------------------------------------------
    // implementation of JoinRouter
    //---------------------------------------------
    @Override
    public Join join(Join... join) {
        return new Join(this, Arrays.asList(join));
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
    @Override
    public GroupBy groupBy(GroupBy... groupBy) {
        return new GroupBy(this, Arrays.asList(groupBy));
    }

    //---------------------------------------------
    // implementation of OrderByRouter
    //---------------------------------------------

    /**
     * Create and chain an ORDER BY component for specifying the ORDER BY clause of the query.
     *
     * @param orderBy an array of the ORDER BY expressions.
     * @return the ORDER BY component.
     */
    @Override
    public OrderBy orderBy(OrderBy... orderBy) {
        return new OrderBy(this, Arrays.asList(orderBy));
    }

    //---------------------------------------------
    // implementation of LimitRouter
    //---------------------------------------------

    @Override
    public Limit limit(Object limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Limit limit(Object limit, Object offset) {
        throw new UnsupportedOperationException();
    }
}
