//
// Database.java
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

import com.couchbase.lite.internal.core.C4Constants;


/**
 * A Couchbase Lite database.
 */
public final class Database extends AbstractDatabase {
    /**
     * Construct a  AbstractDatabase with a given name and database config.
     * If the database does not yet exist, it will be created, unless the `readOnly` option is used.
     *
     * @param name   The name of the database. May NOT contain capital letters!
     * @param config The database config, Note: null config parameter is not allowed with Android platform
     * @throws CouchbaseLiteException Throws an exception if any error occurs during the open operation.
     */
    public Database(@NonNull String name, @NonNull DatabaseConfiguration config) throws CouchbaseLiteException {
        super(name, config);
    }

    //---------------------------------------------
    // Implementing abstract methods for Encryption
    //---------------------------------------------

    int getEncryptionAlgorithm() {
        return C4Constants.EncryptionAlgorithm.NONE;
    }

    byte[] getEncryptionKey() {
        return null;
    }
}
