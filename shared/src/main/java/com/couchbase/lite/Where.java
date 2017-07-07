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
public class Where extends Query implements GroupByRouter, OrderByRouter, LimitRouter {
    /* package */ Where(Query query, Expression where) {
        copy(query);
        setWhere(where);
    }

    //---------------------------------------------
    // implementation of GroupByRouter
    //---------------------------------------------

    @Override
    public GroupBy groupBy(GroupBy... groupBy) {
        return null;
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
        return null;
    }

    @Override
    public Limit limit(Object limit, Object offset) {
        return null;
    }
}
