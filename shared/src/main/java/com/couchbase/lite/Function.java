package com.couchbase.lite;

import java.util.Arrays;

public class Function extends Expression {

    private String func = null;
    private Object param = null;

    private Function(String func, Object param) {
        this.func = func;
        this.param = param;
    }

    public static Function avg(Object expr) {
        return new Function("AVG()", expr);
    }

    public static Function count(Object expr) {
        return new Function("COUNT()", expr);
    } // null expression -> count *

    public static Function min(Object expr) {
        return new Function("MIN()", expr);
    }

    public static Function max(Object expr) {
        return new Function("MAX()", expr);
    }

    public static Function sum(Object expr) {
        return new Function("SUM()", expr);
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
