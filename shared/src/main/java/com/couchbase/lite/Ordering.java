package com.couchbase.lite;

import java.util.ArrayList;
import java.util.List;

public abstract class Ordering {
    //---------------------------------------------
    // Inner public Class
    //---------------------------------------------

    /**
     * SorderOrder represents a single ORDER BY entity. You can specify either ascending or
     * descending order. The default order is ascending.
     */
    public static class SortOrder extends Ordering {
        private Expression expression;
        private boolean isAscending;

        SortOrder(Expression expression) {
            this.expression = expression;
            this.isAscending = true;
        }

        /**
         * Set the order as ascending order.
         *
         * @return the OrderBy object.
         */
        public Ordering ascending() {
            this.isAscending = true;
            return this;
        }

        /**
         * Set the order as descending order.
         *
         * @return the OrderBy object.
         */
        public Ordering descending() {
            this.isAscending = false;
            return this;
        }

        Object asJSON() {
            if (isAscending)
                return expression.asJSON();

            List<Object> json = new ArrayList<Object>();
            json.add("DESC");
            json.add(expression.asJSON());
            return json;
        }
    }

    //---------------------------------------------
    // API - public static methods
    //---------------------------------------------

    /**
     * Create a SortOrder, inherited from the OrderBy class, object by the given
     * property name.
     *
     * @param property the property name
     * @return the SortOrder object.
     */
    public static SortOrder property(String property) {
        return expression(Expression.property(property));
    }

    /**
     * Create a SortOrder, inherited from the OrderBy class, object by the given expression.
     *
     * @param expression the expression object.
     * @return the SortOrder object.
     */
    public static SortOrder expression(Expression expression) {
        return new SortOrder(expression);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    abstract Object asJSON();
}
