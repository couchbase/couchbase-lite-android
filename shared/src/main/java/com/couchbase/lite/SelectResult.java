package com.couchbase.lite;


public class SelectResult {

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
        private As(Expression expression) {
            super(expression);
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
    private Expression expression = null;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private SelectResult() {
        this.expression = null;
    }

    private SelectResult(Expression expression) {
        this.expression = expression;
    }

    //---------------------------------------------
    // API - public static methods
    //---------------------------------------------

    public static SelectResult expression(Expression expression) {
        return new SelectResult(expression);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    Object asJSON() {
        return expression.asJSON();
    }
}
