//
// DataManager.java
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
package com.couchbase.lite.api;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseConfiguration;

public class DataManager {
    private static DataManager sharedInstance;
    private Database database;

    public static DataManager instance(DatabaseConfiguration config) {
        if (sharedInstance == null && config != null)
            sharedInstance = new DataManager(config);
        return sharedInstance;
    }

    private DataManager(DatabaseConfiguration config) {
        try {
            database = new Database("dbname", config);
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            database.close();
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
    }

    public void delete() {
        try {
            database.delete();
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
    }
}
