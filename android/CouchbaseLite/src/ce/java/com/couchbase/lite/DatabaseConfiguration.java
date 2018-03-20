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

import java.io.File;

/**
 * Configuration for opening a database.
 */
public final class DatabaseConfiguration {
    private static final String TEMP_DIR_NAME = "tmp";

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private boolean readonly = false;
    private Context context = null;
    private String directory = null;
    private boolean customDir = false;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    public DatabaseConfiguration(Context context) {
        if (context == null)
            throw new IllegalArgumentException("context is null");
        this.readonly = false;
        this.context = context;
        this.directory = context.getFilesDir().getAbsolutePath();
        this.customDir = false;
    }

    public DatabaseConfiguration(DatabaseConfiguration config) {
        if (config == null)
            throw new IllegalArgumentException("config is null");
        this.readonly = false;
        this.context = config.context;
        this.directory = config.directory;
        this.customDir = config.customDir;
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
        this.customDir = true;
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

    String getTempDir() {
        if (customDir) {
            File cache = new File(directory, TEMP_DIR_NAME);
            if (!cache.exists())
                if (!cache.mkdirs())
                    return context.getCacheDir().getAbsolutePath();
                else if (!cache.isDirectory())
                    return context.getCacheDir().getAbsolutePath();
            return cache.getAbsolutePath();
        } else {
            return context.getCacheDir().getAbsolutePath();
        }
    }
}
