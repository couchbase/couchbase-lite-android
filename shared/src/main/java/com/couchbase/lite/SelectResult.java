package com.couchbase.lite;


public class SelectResult {

    // TODO

    //---------------------------------------------
    // Inner public Class
    //---------------------------------------------
    public static class From extends SelectResult {
        // Member variables
        private String alias;

        // Constructor
        private From() {
        }

        // API - public methods
        public SelectResult from(String alias) {
            this.alias = alias;
            return this;
        }
    }

    public static class As extends SelectResult {
        // Member variables
        private String alias;

        // Constructor
        private As(Expression expr) {
            super(expr);
        }

        // API - public methods
        public SelectResult as(String alias) {
            this.alias = alias;
            return this;
        }
    }

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Expression expr = null;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private SelectResult() {
        this.expr = null;
    }

    private SelectResult(Expression expr) {
        this.expr = expr;
    }

    //---------------------------------------------
    // API - public static methods
    //---------------------------------------------

    // TODO:

//    public static SelectResult.As expression(Expression expr) {
//        return new As(expr);
//    }
//
//    public static SelectResult.From all() {
//        return new From();
//    }

    public static SelectResult expression(Expression expr) {
        return new SelectResult(expr);
    }


    Object asJSON() {
        return expr.asJSON();
    }
}
