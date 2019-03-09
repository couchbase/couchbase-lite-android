//
// DocumentChange.java
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
    @NonNull
    public Database getDatabase() {
        return database;
    }

    /**
     * Returns the changed document ID
     */
    @NonNull
    public String getDocumentID() {
        return documentID;
    }

    @NonNull
    @Override
    public String toString() {
        return "DocumentChange{" +
            "database='" + database.getName() + '\'' +
            "documentID='" + documentID + '\'' +
            '}';
    }
}
