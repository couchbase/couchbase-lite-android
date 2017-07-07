package com.couchbase.lite;

import java.util.Arrays;
import java.util.List;

public class GroupBy extends Query implements HavingRouter, OrderByRouter, LimitRouter {

    private Expression expr = null;

    private List<GroupBy> groupBies;

    private GroupBy(Expression expr) {
        this.expr = expr;
    }

    GroupBy(Query query, List<GroupBy> groupBies) {
        copy(query);
        setGroupBy(this);
        this.groupBies = groupBies;
    }

    public static GroupBy expression(Expression expr) {
        return new GroupBy(expr);
    }

    //---------------------------------------------
    // implementation of HavingRouter
    //---------------------------------------------
    @Override
    public Having having(Expression expr) {
        return new Having(this, expr);
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

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    List<GroupBy> getGroupBies() {
        return groupBies;
    }

    Object asJSON() {
        return expr.asJSON();
    }
}
