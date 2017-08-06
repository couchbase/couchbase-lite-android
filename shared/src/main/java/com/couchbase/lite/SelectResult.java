package com.couchbase.lite;


public class SelectResult {

    //---------------------------------------------
    // Inner public Class
    //---------------------------------------------
    public static class From extends SelectResult {
        // Constructor
        private From(Expression expression) {
            super(expression);
        }

        // API - public methods
        public SelectResult.From from(String alias) {
            this.expression = Expression.PropertyExpression.allFrom(alias);
            this.alias = alias;
            return this;
        }
    }

    public static class As extends SelectResult {
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
    Expression expression = null;
    String alias;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private SelectResult(Expression expression) {
        this.expression = expression;
    }

    //---------------------------------------------
    // API - public static methods
    //---------------------------------------------

    public static SelectResult.As expression(Expression expression) {
        return new SelectResult.As(expression);
    }

    public static SelectResult.From all() {
        Expression.PropertyExpression expr = Expression.PropertyExpression.allFrom(null);
        return new SelectResult.From(expr).from(null);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    String getColumnName() {
        if (alias != null)
            return alias;

        if (expression instanceof Expression.PropertyExpression)
            return ((Expression.PropertyExpression) expression).getColumnName();

        return null;
    }

    Object asJSON() {
        return expression.asJSON();
    }
}
