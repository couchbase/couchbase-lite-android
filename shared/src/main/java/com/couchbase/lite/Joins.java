package com.couchbase.lite;


import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Joins extends Query implements WhereRouter, OrderByRouter, LimitRouter {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private List<Join> joins;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    Joins(Query query, List<Join> joins) {
        copy(query);
        this.joins = joins;
        setJoins(this);
    }

    //---------------------------------------------
    // Implementation of WhereRouter
    //---------------------------------------------
    @Override
    public Where where(Expression expression) {
        return new Where(this, expression);
    }

    //---------------------------------------------
    // Implementation of OrderByRouter
    //---------------------------------------------
    @Override
    public OrderBy orderBy(Ordering... orderings) {
        return new OrderBy(this, Arrays.asList(orderings));
    }

    //---------------------------------------------
    // Implementation of LimitRouter
    //---------------------------------------------
    @Override
    public Limit limit(Object limit) {
        return null;
    }

    @Override
    public Limit limit(Object limit, Object offset) {
        return null;
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
