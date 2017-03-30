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

import java.io.File;

/**
 * Options for opening a database. All properties default to false or null.
 */
public final class DatabaseOptions {
    private File directory = null;
    private Object encryptionKey = null;
    private boolean readOnly = false;

    /**
     * Constructs a new DatabaseOptions with default values.
     */
    public DatabaseOptions() {
    }

    /**
     * Returns the path to the directory to store the database in.
     *
     * @return the directory
     */
    public File getDirectory() {
        return directory;
    }

    /**
     * Set the path to the directory to store the database in. If the directory doesn't already exist it willbe created when the database is opened.
     *
     * @param directory the directory
     */
    public void setDirectory(File directory) {
        this.directory = directory;
    }

    /**
     * Returns a key to encrypt the database with.
     *
     * @return the key
     */
    public Object getEncryptionKey() {
        return encryptionKey;
    }

    /**
     * Set a key to encrypt the database with. If the database does not exist and is being created,
     * it will use this key, and the same key must be given every time it's opened
     *
     * @param encryptionKey the key
     */
    public void setEncryptionKey(Object encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    /**
     * Whether the database will be opened read-only.
     *
     * @return true if the database is opened with read-only mode.
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Set true to open the database with read-only mode
     *
     * @param readOnly true to open the database with read-only mode
     */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
}
