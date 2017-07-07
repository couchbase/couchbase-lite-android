package com.couchbase.lite;

public class Having extends Query implements OrderByRouter, LimitRouter {
    //---------------------------------------------
    // implementation of OrderByRouter
    //---------------------------------------------

    @Override
    public OrderBy orderBy(OrderBy... orderBy) {
        return null;
    }

    //---------------------------------------------
    // implementation of FromRouter
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
