package com.couchbase.lite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GroupBy extends Query implements HavingRouter, OrderByRouter, LimitRouter {
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

    @Override
    public Limit limit(Object limit) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Limit limit(Object limit, Object offset) {
        throw new UnsupportedOperationException();
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
