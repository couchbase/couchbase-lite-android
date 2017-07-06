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

/**
 * A query data source, used for specifying the source of data for a query.
 */
public class DataSource {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Object source = null;
    private String alias = null;

    //---------------------------------------------
    // Inner public Class
    //---------------------------------------------

    /**
     * Database as a data source for query.
     */
    public static class As extends DataSource {
        //---------------------------------------------
        // Constructors
        //---------------------------------------------
        private As(com.couchbase.lite.Database source) {
            super(source);
        }

        //---------------------------------------------
        // API - public methods
        //---------------------------------------------

        /**
         * Set an alias to the database data source.
         *
         * @param alias the alias to set.
         * @return the data source object with the given alias set.
         */
        public DataSource as(String alias) {
            super.alias = alias;
            return this;
        }
    }

    //---------------------------------------------
    // Constructors
    //---------------------------------------------
    private DataSource(Object source) {
        this.source = source;
        this.alias = null;
    }

    //---------------------------------------------
    // API - public static methods
    //---------------------------------------------

    /**
     * Create a database as a data source.
     *
     * @param database the database used as a source of data for query.
     * @return {@code DataSource.Database} object.
     */
    public static As database(com.couchbase.lite.Database database) {
        return new As(database);
    }

    public static As query(Query query) {
        //TODO:
        return null;
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    /* package */ Object getSource() {
        return this.source;
    }

    /* package */  String getAlias() {
        return alias;
    }

    /* package */ Map<String, Object> asJSON() {
        Map<String, Object> json = new HashMap<>();
        if (alias != null)
            json.put("AS", alias);
        return json;
    }
}
