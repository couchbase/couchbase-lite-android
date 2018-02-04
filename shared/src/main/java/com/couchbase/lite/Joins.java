//
// Joins.java
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A Joins component represents a collection of the joins clauses of the query statement.
 */
public final class Joins extends AbstractQuery implements WhereRouter, OrderByRouter, LimitRouter {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private List<Join> joins;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    Joins(AbstractQuery query, List<Join> joins) {
        copy(query);
        this.joins = joins;
        setJoins(this);
    }

    //---------------------------------------------
    // Implementation of WhereRouter
    //---------------------------------------------

    /**
     * Creates and chains a Where object for specifying the WHERE clause of the query.
     *
     * @param expression The where expression.
     * @return The Where object that represents the WHERE clause of the query.
     */
    @Override
    public Where where(Expression expression) {
        return new Where(this, expression);
    }

    //---------------------------------------------
    // Implementation of OrderByRouter
    //---------------------------------------------

    /**
     * Creates and chains an OrderBy object for specifying the orderings of the query result.
     *
     * @param orderings The Ordering objects.
     * @return The OrderBy object that represents the ORDER BY clause of the query.
     */
    @Override
    public OrderBy orderBy(Ordering... orderings) {
        return new OrderBy(this, Arrays.asList(orderings));
    }

    //---------------------------------------------
    // Implementation of LimitRouter
    //---------------------------------------------

    /**
     * Creates and chains a Limit object to limit the number query results.
     *
     * @param limit The limit expression.
     * @return The Limit object that represents the LIMIT clause of the query.
     */
    @Override
    public Limit limit(Expression limit) {
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
    public Limit limit(Expression limit, Expression offset) {
        return new Limit(this, limit, offset);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------
    Object asJSON() {
        List<Object> json = new ArrayList<>();
        for (Join join : joins)
            json.add(join.asJSON());
        return json;
    }
}
