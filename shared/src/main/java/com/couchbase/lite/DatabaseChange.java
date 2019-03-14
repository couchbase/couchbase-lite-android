//
// DatabaseChange.java
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

import java.util.Collections;
import java.util.List;


/**
 * Provides details about a Database change.
 */
public final class DatabaseChange {
    private final List<String> documentIDs;
    private final Database database;

    DatabaseChange(Database database, List<String> documentIDs) {
        this.database = database;
        this.documentIDs = Collections.unmodifiableList(documentIDs);
    }

    /**
     * Returns the database instance
     */
    @NonNull
    public Database getDatabase() {
        return database;
    }

    /**
     * Returns the list of the changed document IDs
     *
     * @return
     */
    @NonNull
    public List<String> getDocumentIDs() {
        return documentIDs;
    }

    @Override
    public String toString() {
        return "DatabaseChange{" +
            "database=" + database +
            ", documentIDs=" + documentIDs +
            '}';
    }
}
