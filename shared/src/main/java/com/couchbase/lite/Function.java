package com.couchbase.lite;

import java.util.Arrays;

public class Function extends Expression {

    private String func = null;
    private Object param = null;

    private Function(String func, Object param) {
        this.func = func;
        this.param = param;
    }

    public static Function avg(Object expression) {
        return new Function("AVG()", expression);
    }

    public static Function count(Object expression) {
        return new Function("COUNT()", expression);
    } // null expression -> count *

    public static Function min(Object expression) {
        return new Function("MIN()", expression);
    }

    public static Function max(Object expression) {
        return new Function("MAX()", expression);
    }

    public static Function sum(Object expression) {
        return new Function("SUM()", expression);
    }

    @Override
    protected Object asJSON() {
        Object p = null;
        if (param != null && param instanceof Expression)
            p = ((Expression) param).asJSON();
        else
            p = param;
        return Arrays.asList(func, p);
    }
}
