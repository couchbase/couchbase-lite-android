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


/**
 * Provides details about a Document change.
 */
public final class DocumentChange {
    private final Database database;

    private final String documentID;

    DocumentChange(Database database, String documentID) {
        this.database = database;
        this.documentID = documentID;
    }

    /**
     * Return the Database instance
     */
    public Database getDatabase() {
        return database;
    }

    /**
     * Returns the changed document ID
     */
    public String getDocumentID() {
        return documentID;
    }

    @Override
    public String toString() {
        return "DocumentChange{" +
                "database='" + database.getName() + '\'' +
                "documentID='" + documentID + '\'' +
                '}';
    }
}
