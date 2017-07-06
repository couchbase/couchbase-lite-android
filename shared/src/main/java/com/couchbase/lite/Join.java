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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Join extends Query implements WhereRouter, OrderByRouter {
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
    private Expression on;

    private List<Join> joins;

    //---------------------------------------------
    // Inner public Class
    //---------------------------------------------
    public static class On extends Join {
        //---------------------------------------------
        // Constructors
        //---------------------------------------------
        private On(String type, DataSource datasource) {
            super(type, datasource);
        }

        //---------------------------------------------
        // API - public methods
        //---------------------------------------------
        public Join on(Expression on) {
            super.on = on;
            return this;
        }
    }

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    private Join(String type, DataSource dataSource) {
        this.type = type;
        this.dataSource = dataSource;
        this.on = null;
    }

    /*package*/ Join(Query query, List<Join> joins) {
        copy(query);
        setJoin(this);
        this.joins = joins;
    }

    //---------------------------------------------
    // API - public static methods
    //---------------------------------------------

    public static On join(DataSource datasource) {
        return innerJoin(datasource);
    }

    public static On leftJoin(DataSource datasource) {
        return new On(kCBLOuterJoin, datasource);
    }

    public static On leftOuterJoin(DataSource datasource) {
        return new On(kCBLLeftOuterJoin, datasource);
    }

    public static On innerJoin(DataSource datasource) {
        return new On(kCBLInnerJoin, datasource);
    }

    public static On crossJoin(DataSource datasource) {
        return new On(kCBLCrossJoin, datasource);
    }

    //---------------------------------------------
    // Implementation of WhereRouter/OrderByRouter
    //---------------------------------------------
    @Override
    public Where where(Expression expression) {
        return new Where(this, expression);
    }

    @Override
    public OrderBy orderBy(OrderBy... orderBy) {
        return new OrderBy(this, Arrays.asList(orderBy));
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    /* package */ String getType() {
        return type;
    }

    public List<Join> getJoins() {
        return joins;
    }

    /* package */ Expression getOn() {
        return on;
    }

    /* package */ Map<String, Object> asJSON() {
        Map<String, Object> json = new HashMap<>();
        json.put("JOIN", type);
        json.put("ON", on.asJSON());
        json.putAll(dataSource.asJSON());
        return json;
    }
}
