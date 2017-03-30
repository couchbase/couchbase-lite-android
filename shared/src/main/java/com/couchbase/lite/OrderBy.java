/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.lite;

import java.util.ArrayList;
import java.util.List;

/**
 * An OrderBy represents an ORDER BY clause of the query for specifying properties or expressions
 * that the result rows should be sorted by.
 */
public class OrderBy extends Query {
    /* package */ List<OrderBy> orders;

    /* package */ OrderBy() {
    }

    /* package */ OrderBy(Query query, List<OrderBy> orders) {
        copy(query);
        setOrderBy(this);
        this.orders = orders;
    }

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

    /* package */ Object asJSON() {
        List<Object> json = new ArrayList<Object>();
        for (OrderBy o : orders) {
            if (o instanceof SortOrder)
                json.add(o.asJSON());
            else
                json.addAll((List<Object>) o.asJSON());
        }
        return json;
    }

    /**
     * SorderOrder represents a single ORDER BY entity. You can specify either ascending or
     * descending order. The default order is ascending.
     */
    public static class SortOrder extends OrderBy {
        private Expression expression;
        private boolean isAscending;

        /* package */ SortOrder(Expression expression) {
            this.expression = expression;
            this.isAscending = false;
        }

        /**
         * Set the order as ascending order.
         *
         * @return the OrderBy object.
         */
        public OrderBy ascending() {
            this.isAscending = true;
            return this;
        }

        /**
         * Set the order as descending order.
         *
         * @return the OrderBy object.
         */
        public OrderBy descending() {
            this.isAscending = false;
            return this;
        }

        /* package */ Object asJSON() {
            if (isAscending)
                return expression.asJSON();

            List<Object> json = new ArrayList<Object>();
            json.add("DESC");
            json.add(expression.asJSON());
            return json;
        }
    }
}
