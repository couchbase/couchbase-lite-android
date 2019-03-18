//
// DataSource.java
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

import android.support.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;


/**
 * A query data source, used for specifying the source of data for a query.
 */
public class DataSource {
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
        @NonNull
        public DataSource as(@NonNull String alias) {
            if (alias == null) {
                throw new IllegalArgumentException("alias cannot be null.");
            }
            super.alias = alias;
            return this;
        }
    }

    /**
     * Create a database as a data source.
     *
     * @param database the database used as a source of data for query.
     * @return {@code DataSource.Database} object.
     */
    @NonNull
    public static As database(@NonNull com.couchbase.lite.Database database) {
        if (database == null) {
            throw new IllegalArgumentException("database cannot be null.");
        }
        return new As(database);
    }


    private final Object source;
    private String alias;

    private DataSource(Object source) {
        this.source = source;
        this.alias = null;
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    Object getSource() {
        return this.source;
    }

    String getAlias() {
        return alias;
    }

    String getColumnName() {
        if (alias != null) { return alias; }
        else {
            if (source instanceof Database) { return ((Database) source).getName(); }
        }
        return null;
    }

    Map<String, Object> asJSON() {
        final Map<String, Object> json = new HashMap<>();
        if (alias != null) { json.put("AS", alias); }
        return json;
    }
}
