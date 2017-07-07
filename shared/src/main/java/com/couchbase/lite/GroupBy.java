package com.couchbase.lite;

public class GroupBy extends Query implements HavingRouter, OrderByRouter, LimitRouter {

    private Expression expr = null;

    private GroupBy(Expression expr) {
        this.expr = expr;
    }

    public static GroupBy expression(Expression expr) {
        return new GroupBy(expr);
    }

    //---------------------------------------------
    // implementation of HavingRouter
    //---------------------------------------------
    @Override
    public Having having(Expression expr) {
        return null;
    }

    //---------------------------------------------
    // implementation of OrderByRouter
    //---------------------------------------------
    @Override
    public OrderBy orderBy(OrderBy... orderBy) {
        return null;
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

    /*package*/ Object asJSON() {
        return expr.asJSON();
    }
}
