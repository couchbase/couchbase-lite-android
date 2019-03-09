//
// GroupBy.java
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

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * A GroupBy represents the GROUP BY clause to group the query result.
 * The GROUP BY clause is normally used with aggregate functions (AVG, COUNT, MAX, MIN, SUM)
 * to aggregate the group of the values.
 */
public final class GroupBy extends AbstractQuery implements HavingRouter, OrderByRouter, LimitRouter {
    //---------------------------------------------
    // Member variables
    //---------------------------------------------
    private final List<Expression> expressions;

    //---------------------------------------------
    // Constructor
    //--------------------------------------------
    GroupBy(AbstractQuery query, List<Expression> expressions) {
        copy(query);
        this.expressions = expressions;
        setGroupBy(this);
    }

    //---------------------------------------------
    // implementation of HavingRouter
    //---------------------------------------------

    /**
     * Creates and chain a Having object for filtering the aggregated values
     * from the the GROUP BY clause.
     *
     * @param expression The expression
     * @return The Having object that represents the HAVING clause of the query.
     */
    @NonNull
    @Override
    public Having having(@NonNull Expression expression) {
        if (expression == null) {
            throw new IllegalArgumentException("expression cannot be null.");
        }
        return new Having(this, expression);
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
    @NonNull
    @Override
    public OrderBy orderBy(@NonNull Ordering... orderings) {
        if (orderings == null) {
            throw new IllegalArgumentException("orderings cannot be null.");
        }
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
    @NonNull
    @Override
    public Limit limit(@NonNull Expression limit) {
        if (limit == null) {
            throw new IllegalArgumentException("limit cannot be null.");
        }
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
    @NonNull
    @Override
    public Limit limit(@NonNull Expression limit, Expression offset) {
        if (limit == null) {
            throw new IllegalArgumentException("limit cannot be null.");
        }
        return new Limit(this, limit, offset);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------
    Object asJSON() {
        final List<Object> groupBy = new ArrayList<>();
        for (Expression expression : expressions) { groupBy.add(expression.asJSON()); }
        return groupBy;
    }
}
