/**
 *
 * Copyright (c) 2015 Couchbase, Inc. All rights reserved.
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


package com.couchbase.lite.android;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.storage.SQLiteStorageEngine;
import com.couchbase.lite.storage.SQLiteStorageEngineFactory;

public class AndroidSQLiteStorageEngineFactory implements SQLiteStorageEngineFactory {
    private android.content.Context context = null;

    public AndroidSQLiteStorageEngineFactory(android.content.Context context) {
        this.context = context;
    }

    @Override
    public SQLiteStorageEngine createStorageEngine() throws CouchbaseLiteException {
        return new AndroidSQLiteStorageEngine(context);
    }
}
