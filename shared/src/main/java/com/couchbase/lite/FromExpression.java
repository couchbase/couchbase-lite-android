package com.couchbase.lite;

import java.util.ArrayList;
import java.util.List;

public final class FromExpression extends Expression {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final String from; // Data Source Alias

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    FromExpression() {
        from = null;
    }

    FromExpression(String from) {
        this.from = from;
    }

    //---------------------------------------------
    // public level access
    //---------------------------------------------
    public FromExpression from(String alias) {
        return new com.couchbase.lite.FromExpression(alias);
    }

    //---------------------------------------------
    // package level access
    //---------------------------------------------
    @Override
    Object asJSON() {
        List<Object> json = new ArrayList<>();
        if (from != null)
            json.add("." + from + ".");
        else
            json.add(".");
        return json;
    }
}
