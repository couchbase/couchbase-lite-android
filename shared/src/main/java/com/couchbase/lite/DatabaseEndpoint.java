//
// DatabaseEndpoint.java
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

/**
 * Database based replication target endpoint.
 */
public final class DatabaseEndpoint implements Endpoint {
    private final Database database;

    /**
     * Constructor with the database instance
     *
     * @param database
     */
    public DatabaseEndpoint(Database database) {
        if (database == null)
            throw new IllegalArgumentException("the database parameter is null.");
        this.database = database;
    }

    /**
     * Return the Database instance
     */
    public Database getDatabase() {
        return database;
    }

    @Override
    public String toString() {
        return "DatabaseEndpoint{" +
                "database=" + database +
                '}';
    }
}
