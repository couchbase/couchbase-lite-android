package com.couchbase.lite.internal.query.expression;


import com.couchbase.lite.Expression;

import java.util.ArrayList;
import java.util.List;

public class FunctionExpresson extends Expression{
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private String func = null;
    private List<Object> params = null;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    public FunctionExpresson(String func, List<Object> params) {
        this.func = func;
        this.params = params;
    }

    //---------------------------------------------
    // public level access
    //---------------------------------------------
    @Override
    public Object asJSON() {
        List<Object> json = new ArrayList<>();
        json.add(func);
        for (Object param : params) {
            if (param != null && param instanceof Expression)
                json.add(((Expression) param).asJSON());
            else
                json.add(param);
        }
        return json;
    }
}
