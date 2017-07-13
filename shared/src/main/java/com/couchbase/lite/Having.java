package com.couchbase.lite;

import java.util.Arrays;

/**
 * Having represents a HAVING clause of the query statement used for filtering the aggregated values
 * from the the GROUP BY clause.
 */
public class Having extends Query implements OrderByRouter, LimitRouter {
    private Expression expression;

    Having(Query query, Expression expression) {
        copy(query);
        this.expression = expression;
        setHaving(this);
    }

    //---------------------------------------------
    // implementation of OrderByRouter
    //---------------------------------------------

    /**
     * Create and chain an ORDER BY component for specifying the orderings of the query result.
     */
    @Override
    public OrderBy orderBy(Ordering... orderings) {
        return new OrderBy(this, Arrays.asList(orderings));
    }

    //---------------------------------------------
    // implementation of FromRouter
    //---------------------------------------------
    @Override
    public Limit limit(Object limit) {
        return new Limit(this, limit, null);
    }

    @Override
    public Limit limit(Object limit, Object offset) {
        return new Limit(this, limit, offset);
    }

    //---------------------------------------------
    // package level
    //---------------------------------------------

    Object asJSON() {
        return expression != null ? expression.asJSON() : null;
    }
}
