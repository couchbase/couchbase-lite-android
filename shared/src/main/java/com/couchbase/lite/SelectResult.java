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

        String getColumnName() {
            if(alias != null)
                return alias;

            return super.getColumnName();
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

    public static SelectResult.As expression(Expression expression) {
        return new SelectResult.As(expression);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    String getColumnName() {
        if (expression instanceof Expression.PropertyExpression) {
            Expression.PropertyExpression propExpr = (Expression.PropertyExpression) expression;
            String keyPath = propExpr.getKeyPath();
            String[] pathes = keyPath.split("\\.");
            return pathes[pathes.length - 1];
        }
        return null;
    }

    Object asJSON() {
        return expression.asJSON();
    }
}
