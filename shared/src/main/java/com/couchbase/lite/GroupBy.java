package com.couchbase.lite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A GroupBy represents the GROUP BY clause to group the query result.
 * The GROUP BY clause is normally used with aggregate functions (AVG, COUNT, MAX, MIN, SUM)
 * to aggregate the group of the values.
 */
public final class GroupBy extends Query implements HavingRouter, OrderByRouter, LimitRouter {
    //---------------------------------------------
    // Member variables
    //---------------------------------------------
    private List<Expression> expressions;

    //---------------------------------------------
    // Constructor
    //--------------------------------------------
    GroupBy(Query query, List<Expression> expressions) {
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
    @Override
    public Having having(Expression expression) {
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
        List<Object> groupBy = new ArrayList<>();
        for (Expression expression : expressions)
            groupBy.add(expression.asJSON());
        return groupBy;
    }
}
