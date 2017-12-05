package com.couchbase.lite;

import com.couchbase.lite.internal.query.expression.FunctionExpresson;

import java.util.Arrays;

public class Function {
    //---------------------------------------------
    // Aggregation
    //---------------------------------------------
    public static Expression avg(Object expression) {
        return new FunctionExpresson("AVG()", Arrays.asList(expression));
    }

    public static Expression count(Object expression) {
        return new FunctionExpresson("COUNT()", Arrays.asList(expression));
    } // null expression -> count *

    public static Expression min(Object expression) {
        return new FunctionExpresson("MIN()", Arrays.asList(expression));
    }

    public static Expression max(Object expression) {
        return new FunctionExpresson("MAX()", Arrays.asList(expression));
    }

    public static Expression sum(Object expression) {
        return new FunctionExpresson("SUM()", Arrays.asList(expression));
    }

    //---------------------------------------------
    // Math
    //---------------------------------------------
    public static Expression abs(Object expression) {
        return new FunctionExpresson("ABS()", Arrays.asList(expression));
    }

    public static Expression acos(Object expression) {
        return new FunctionExpresson("ACOS()", Arrays.asList(expression));
    }

    public static Expression asin(Object expression) {
        return new FunctionExpresson("ASIN()", Arrays.asList(expression));
    }

    public static Expression atan(Object expression) {
        return new FunctionExpresson("ATAN()", Arrays.asList(expression));
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
    public static Expression atan2(Object y, Object x) {
        return new FunctionExpresson("ATAN2()", Arrays.asList(x, y));
    }

    public static Expression ceil(Object expression) {
        return new FunctionExpresson("CEIL()", Arrays.asList(expression));
    }

    public static Expression cos(Object expression) {
        return new FunctionExpresson("COS()", Arrays.asList(expression));
    }

    public static Expression degrees(Object expression) {
        return new FunctionExpresson("DEGREES()", Arrays.asList(expression));
    }

    public static Expression e() {
        return new FunctionExpresson("E()", Arrays.asList((Object) null));
    }

    public static Expression exp(Object expression) {
        return new FunctionExpresson("EXP()", Arrays.asList(expression));
    }

    public static Expression floor(Object expression) {
        return new FunctionExpresson("FLOOR()", Arrays.asList(expression));
    }

    public static Expression ln(Object expression) {
        return new FunctionExpresson("LN()", Arrays.asList(expression));
    }

    public static Expression log(Object expression) {
        return new FunctionExpresson("LOG()", Arrays.asList(expression));
    }

    public static Expression pi() {
        return new FunctionExpresson("PI()", Arrays.asList((Object) null));
    }

    public static Expression power(Object base, Object exponent) {
        return new FunctionExpresson("POWER()", Arrays.asList(base, exponent));
    }

    public static Expression radians(Object expression) {
        return new FunctionExpresson("RADIANS()", Arrays.asList(expression));
    }

    public static Expression round(Object expression) {
        return new FunctionExpresson("ROUND()", Arrays.asList(expression));
    }

    public static Expression round(Object expression, int digits) {
        return new FunctionExpresson("ROUND()", Arrays.asList(expression, digits));
    }

    public static Expression sign(Object expression) {
        return new FunctionExpresson("SIGN()", Arrays.asList(expression));
    }

    public static Expression sin(Object expression) {
        return new FunctionExpresson("SIN()", Arrays.asList(expression));
    }

    public static Expression sqrt(Object expression) {
        return new FunctionExpresson("SQRT()", Arrays.asList(expression));
    }

    public static Expression tan(Object expression) {
        return new FunctionExpresson("TAN()", Arrays.asList(expression));
    }

    public static Expression trunc(Object expression) {
        return new FunctionExpresson("TRUNC()", Arrays.asList(expression));
    }

    public static Expression trunc(Object expression, int digits) {
        return new FunctionExpresson("TRUNC()", Arrays.asList(expression, digits));
    }

    //---------------------------------------------
    // String
    //---------------------------------------------
    public static Expression contains(Object expression, Object substring) {
        return new FunctionExpresson("CONTAINS()", Arrays.asList(expression, substring));
    }

    public static Expression length(Object expression) {
        return new FunctionExpresson("LENGTH()", Arrays.asList(expression));
    }

    public static Expression lower(Object expression) {
        return new FunctionExpresson("LOWER()", Arrays.asList(expression));
    }

    public static Expression ltrim(Object expression) {
        return new FunctionExpresson("LTRIM()", Arrays.asList(expression));
    }

    public static Expression rtrim(Object expression) {
        return new FunctionExpresson("RTRIM()", Arrays.asList(expression));
    }

    public static Expression trim(Object expression) {
        return new FunctionExpresson("TRIM()", Arrays.asList(expression));
    }

    public static Expression upper(Object expression) {
        return new FunctionExpresson("UPPER()", Arrays.asList(expression));
    }

    //---------------------------------------------
    // Type
    //---------------------------------------------
    public static Expression isArray(Object expression) {
        return new FunctionExpresson("ISARRAY()", Arrays.asList(expression));
    }

    public static Expression isNumber(Object expression) {
        return new FunctionExpresson("ISNUMBER()", Arrays.asList(expression));
    }

    public static Expression isDictionary(Object expression) {
        return new FunctionExpresson("ISOBJECT()", Arrays.asList(expression));
    }

    public static Expression isString(Object expression) {
        return new FunctionExpresson("ISSTRING()", Arrays.asList(expression));
    }
}
