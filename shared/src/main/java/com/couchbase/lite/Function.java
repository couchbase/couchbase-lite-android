package com.couchbase.lite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Function extends Expression {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private String func = null;
    private List<Object> params = null;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private Function(String func, List<Object> params) {
        this.func = func;
        this.params = params;
    }

    //---------------------------------------------
    // Aggregation
    //---------------------------------------------
    public static Function avg(Object expression) {
        return new Function("AVG()", Arrays.asList(expression));
    }

    public static Function count(Object expression) {
        return new Function("COUNT()", Arrays.asList(expression));
    } // null expression -> count *

    public static Function min(Object expression) {
        return new Function("MIN()", Arrays.asList(expression));
    }

    public static Function max(Object expression) {
        return new Function("MAX()", Arrays.asList(expression));
    }

    public static Function sum(Object expression) {
        return new Function("SUM()", Arrays.asList(expression));
    }

    //---------------------------------------------
    // Array
    //---------------------------------------------
    public static Function arrayContains(Object expression, Object value) {
        return new Function("ARRAY_CONTAINS()", Arrays.asList(expression, value));
    }

    public static Function arrayLength(Object expression) {
        return new Function("ARRAY_LENGTH()", Arrays.asList(expression));
    }

    //---------------------------------------------
    // Math
    //---------------------------------------------
    public static Function abs(Object expression) {
        return new Function("ABS()", Arrays.asList(expression));
    }

    public static Function acos(Object expression) {
        return new Function("ACOS()", Arrays.asList(expression));
    }

    public static Function asin(Object expression) {
        return new Function("ASIN()", Arrays.asList(expression));
    }

    public static Function atan(Object expression) {
        return new Function("ATAN()", Arrays.asList(expression));
    }

    /**
     * Returns the angle theta from the conversion of rectangular coordinates (x, y)
     * to polar coordinates (r, theta).
     *
     * @param y the ordinate coordinate
     * @param x the abscissa coordinate
     * @return the theta component of the point (r, theta) in polar coordinates that corresponds
     * to the point (x, y) in Cartesian coordinates.
     */
    public static Function atan2(Object y, Object x) {
        return new Function("ATAN2()", Arrays.asList(x, y));
    }

    public static Function ceil(Object expression) {
        return new Function("CEIL()", Arrays.asList(expression));
    }

    public static Function cos(Object expression) {
        return new Function("COS()", Arrays.asList(expression));
    }

    public static Function degrees(Object expression) {
        return new Function("DEGREES()", Arrays.asList(expression));
    }

    public static Function e() {
        return new Function("E()", Arrays.asList(null));
    }

    public static Function exp(Object expression) {
        return new Function("EXP()", Arrays.asList(expression));
    }

    public static Function floor(Object expression) {
        return new Function("FLOOR()", Arrays.asList(expression));
    }

    public static Function ln(Object expression) {
        return new Function("LN()", Arrays.asList(expression));
    }

    public static Function log(Object expression) {
        return new Function("LOG()", Arrays.asList(expression));
    }

    public static Function pi() {
        return new Function("PI()", Arrays.asList(null));
    }

    public static Function power(Object base, Object exponent) {
        return new Function("POWER()", Arrays.asList(base, exponent));
    }

    public static Function radians(Object expression) {
        return new Function("RADIANS()", Arrays.asList(expression));
    }

    public static Function round(Object expression) {
        return new Function("ROUND()", Arrays.asList(expression));
    }

    public static Function round(Object expression, int digits) {
        return new Function("ROUND()", Arrays.asList(expression, digits));
    }

    public static Function sign(Object expression) {
        return new Function("SIGN()", Arrays.asList(expression));
    }

    public static Function sin(Object expression) {
        return new Function("SIN()", Arrays.asList(expression));
    }

    public static Function sqrt(Object expression) {
        return new Function("SQRT()", Arrays.asList(expression));
    }

    public static Function tan(Object expression) {
        return new Function("TAN()", Arrays.asList(expression));
    }

    public static Function trunc(Object expression) {
        return new Function("TRUNC()", Arrays.asList(expression));
    }

    public static Function trunc(Object expression, int digits) {
        return new Function("TRUNC()", Arrays.asList(expression, digits));
    }

    //---------------------------------------------
    // String
    //---------------------------------------------
    public static Function contains(Object expression, Object substring) {
        return new Function("CONTAINS()", Arrays.asList(expression, substring));
    }

    public static Function length(Object expression) {
        return new Function("LENGTH()", Arrays.asList(expression));
    }

    public static Function lower(Object expression) {
        return new Function("LOWER()", Arrays.asList(expression));
    }

    public static Function ltrim(Object expression) {
        return new Function("LTRIM()", Arrays.asList(expression));
    }

    public static Function rtrim(Object expression) {
        return new Function("RTRIM()", Arrays.asList(expression));
    }

    public static Function trim(Object expression) {
        return new Function("TRIM()", Arrays.asList(expression));
    }

    public static Function upper(Object expression) {
        return new Function("UPPER()", Arrays.asList(expression));
    }

    //---------------------------------------------
    // Type
    //---------------------------------------------
    public static Function isArray(Object expression) {
        return new Function("ISARRAY()", Arrays.asList(expression));
    }

    public static Function isNumber(Object expression) {
        return new Function("ISNUMBER()", Arrays.asList(expression));
    }

    public static Function isDictionary(Object expression) {
        return new Function("ISOBJECT()", Arrays.asList(expression));
    }

    public static Function isString(Object expression) {
        return new Function("ISSTRING()", Arrays.asList(expression));
    }

    // NOTE: Not in supported API list.
    // https://github.com/couchbaselabs/couchbase-lite-apiv2/blob/master/query/java/couchbase/lite/query/Function.java#L57
    /*
    public static Function isBoolean(Object expression) {
        return new Function("ISBOOLEAN()", Arrays.asList(expression));
    }
    */

    //---------------------------------------------
    // FTS
    //---------------------------------------------

    public static Function rank(Expression property) {
        return new Function("RANK()", Arrays.asList((Object) property));
    }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------
    @Override
    protected Object asJSON() {
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
