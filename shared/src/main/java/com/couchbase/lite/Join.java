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

import java.util.HashMap;
import java.util.Map;

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
        public Join on(Expression on) {
            this.on = on;
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
