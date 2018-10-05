//
// DatabaseConfiguration.java
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

import android.content.Context;
import com.couchbase.lite.internal.utils.FileUtils;
import java.io.File;

/**
 * Configuration for opening a database.
 */
public final class DatabaseConfiguration {
    private static final String TEMP_DIR_NAME = "CBLTemp";

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Context context;
    private boolean readonly;
    private String directory;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    public DatabaseConfiguration(Context context) {
        if (context == null)
            throw new IllegalArgumentException("context is null");
        this.context = context;
        this.readonly = false;
        this.directory = context.getFilesDir().getAbsolutePath();
    }

    public DatabaseConfiguration(DatabaseConfiguration config) {
        if (config == null)
            throw new IllegalArgumentException("config is null");
        this.context = config.context;
        this.readonly = false;
        this.directory = config.directory;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Set the path to the directory to store the database in. If the directory doesn't already exist it willbe created when the database is opened.
     *
     * @param directory the directory
     * @return The self object.
     */
    public DatabaseConfiguration setDirectory(String directory) {
        if (directory == null)
            throw new IllegalArgumentException("null directory is not allowed");
        if (readonly)
            throw new IllegalStateException("DatabaseConfiguration is readonly mode.");
        this.directory = directory;
        return this;
    }

    /**
     * Returns the path to the directory to store the database in.
     *
     * @return the directory
     */
    public String getDirectory() {
        return directory;
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    DatabaseConfiguration readonlyCopy() {
        DatabaseConfiguration config = new DatabaseConfiguration(this);
        config.readonly = true;
        return config;
    }

    Context getContext() {
        return context;
    }

    File getTempDirectory() {
        if (FileUtils.isSubDirectory(context.getFilesDir(), new File(getDirectory())))
            return context.getCacheDir();
        else
            return new File(getDirectory(), "TEMP_DIR_NAME");
    }
}
