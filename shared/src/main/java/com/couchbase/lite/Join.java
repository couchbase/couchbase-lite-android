//
// Join.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import java.util.HashMap;
import java.util.Map;

/**
 * A Join component representing a single JOIN clause in the query statement.
 */
public class Join {
    //---------------------------------------------
    // static variables
    //---------------------------------------------
    static final String kCBLInnerJoin = "INNER";
    static final String kCBLOuterJoin = "OUTER";
    static final String kCBLLeftOuterJoin = "LEFT OUTER";
    static final String kCBLCrossJoin = "CROSS";

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private String type;
    private DataSource dataSource;

    //---------------------------------------------
    // Inner public Class
    //---------------------------------------------

    /**
     * On component used for specifying join conditions.
     */
    public final static class On extends Join {
        //---------------------------------------------
        // Member variables
        //---------------------------------------------
        private Expression on;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------
        private On(String type, DataSource datasource) {
            super(type, datasource);
        }

        //---------------------------------------------
        // API - public methods
        //---------------------------------------------

        /**
         * Specify join conditions from the given expression.
         *
         * @param expression The Expression object specifying the join conditions.
         * @return The Join object that represents a single JOIN clause of the query.
         */
        public Join on(Expression expression) {
            this.on = expression;
            return this;
        }

        //---------------------------------------------
        // Package level access
        //---------------------------------------------
        @Override
        Object asJSON() {
            Map<String, Object> json = new HashMap<>();
            json.put("JOIN", super.type);
            json.put("ON", on.asJSON());
            json.putAll(super.dataSource.asJSON());
            return json;
        }
    }

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private Join(String type, DataSource dataSource) {
        this.type = type;
        this.dataSource = dataSource;
    }

    //---------------------------------------------
    // API - public static methods
    //---------------------------------------------

    /**
     * Create a JOIN (same as INNER JOIN) component with the given data source.
     * Use the returned On component to specify join conditions.
     *
     * @param datasource The DataSource object of the JOIN clause.
     * @return The On object used for specifying join conditions.
     */
    public static On join(DataSource datasource) {
        return innerJoin(datasource);
    }

    /**
     * Create a LEFT JOIN (same as LEFT OUTER JOIN) component with the given data source.
     * Use the returned On component to specify join conditions.
     *
     * @param datasource The DataSource object of the JOIN clause.
     * @return The On object used for specifying join conditions.
     */
    public static On leftJoin(DataSource datasource) {
        return new On(kCBLLeftOuterJoin, datasource);
    }

    /**
     * Create a LEFT OUTER JOIN component with the given data source.
     * Use the returned On component to specify join conditions.
     *
     * @param datasource The DataSource object of the JOIN clause.
     * @return The On object used for specifying join conditions.
     */
    public static On leftOuterJoin(DataSource datasource) {
        return new On(kCBLLeftOuterJoin, datasource);
    }

    /**
     * Create an INNER JOIN component with the given data source.
     * Use the returned On component to specify join conditions.
     *
     * @param datasource The DataSource object of the JOIN clause.
     * @return The On object used for specifying join conditions.
     */
    public static On innerJoin(DataSource datasource) {
        return new On(kCBLInnerJoin, datasource);
    }

    /**
     * Create an CROSS JOIN component with the given data source.
     * Use the returned On component to specify join conditions.
     *
     * @param datasource The DataSource object of the JOIN clause.
     * @return The Join object used for specifying join conditions.
     */
    public static Join crossJoin(DataSource datasource) {
        return new Join(kCBLCrossJoin, datasource);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------
    Object asJSON() {
        Map<String, Object> json = new HashMap<>();
        json.put("JOIN", type);
        json.putAll(dataSource.asJSON());
        return json;
    }
}
