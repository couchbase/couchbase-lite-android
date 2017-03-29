/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.lite;

/**
 * A query data source, used for specifying the source of data for a query.
 */
public class DataSource {
    private Object source;

    protected DataSource(Object source) {
        this.source = source;
    }

    /**
     * Create a database as a data source.
     * @param database the database used as a source of data for query.
     * @return {@code DataSource.Database} object.
     */
    public static Database database(com.couchbase.lite.Database database) {
        return new Database(database);
    }

    /* package */ Object getSource() {
        return this.source;
    }

    /**
     * Database as a data source for query.
     */
    public static class Database extends DataSource {
        protected Database(com.couchbase.lite.Database source) {
            super(source);
        }

        /**
         * Set an alias to the database data source.
         * @param alias the alias to set.
         * @return the data source object with the given alias set.
         */
        public DataSource as(String alias) {
            // TODO: Implement this when JOINS is ready
            return this;
        }
    }
}
