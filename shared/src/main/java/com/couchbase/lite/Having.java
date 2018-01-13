package com.couchbase.lite;

import java.util.Arrays;

/**
 * Having represents a HAVING clause of the query statement used for filtering the aggregated values
 * from the the GROUP BY clause.
 */
public final class Having extends Query implements OrderByRouter, LimitRouter {
    //---------------------------------------------
    // Member variables
    //---------------------------------------------
    private Expression expression;

    //---------------------------------------------
    // Constructor
    //---------------------------------------------
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

    //---------------------------------------------
    // package level
    //---------------------------------------------

    Object asJSON() {
        return expression != null ? expression.asJSON() : null;
    }
}
