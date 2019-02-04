//
// AbstractDatabaseConfiguration.java
//
// Copyright (c) 2018 Couchbase, Inc.  All rights reserved.
//
// Licensed under the Couchbase License Agreement (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// https://info.couchbase.com/rs/302-GJY-034/images/2017-10-30_License_Agreement.pdf
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package com.couchbase.lite;

import android.content.Context;
import android.support.annotation.NonNull;

import com.couchbase.lite.internal.C4Base;

import java.io.File;

abstract class AbstractDatabaseConfiguration {
    private static final String TEMP_DIR_NAME = "CouchbaseLiteTemp";

    private static String TEMP_DIR = null;

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

    protected AbstractDatabaseConfiguration(@NonNull Context context) {
        if (context == null)
            throw new IllegalArgumentException("context cannot be null.");
        this.readonly = false;
        this.context = context;
        this.directory = context.getFilesDir().getAbsolutePath();
        this.customDir = false;
    }

    protected AbstractDatabaseConfiguration(@NonNull AbstractDatabaseConfiguration config) {
        if (config == null)
            throw new IllegalArgumentException("config cannot be null.");
        this.readonly = false;
        this.context = config.context;
        this.directory = config.directory;
        this.customDir = config.customDir;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Returns the path to the directory to store the database in.
     *
     * @return the directory
     */
    @NonNull
    public String getDirectory() {
        return directory;
    }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    protected AbstractDatabaseConfiguration setDirectory(@NonNull String directory) {
        if (directory == null)
            throw new IllegalArgumentException("directory cannot be null.");
        if (readonly)
            throw new IllegalStateException("DatabaseConfiguration is readonly mode.");
        this.directory = directory;
        this.customDir = true;
        return this;
    }

    protected boolean isReadonly() {
        return readonly;
    }

    protected void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    Context getContext() {
        return context;
    }

    /**
     * Set the temp directory based on Database Configuration.
     * The default temp directory is APP_CACHE_DIR/Couchbase/tmp.
     * If a custom database directory is set, the temp directory will be
     * CUSTOM_DATABASE_DIR/Couchbase/tmp.
     */
    void setTempDir() {
        synchronized (AbstractDatabaseConfiguration.class) {
            String tempDir = tempDir();
            if (!tempDir.equals(TEMP_DIR)) {
                TEMP_DIR = tempDir;
                C4Base.setTempDir(TEMP_DIR);
            }
        }
    }

    //---------------------------------------------
    // Private level access
    //---------------------------------------------

    /**
     * Returns the temp directory. The default temp directory is APP_CACHE_DIR/Couchbase/tmp.
     * If a custom database directory is set, the temp directory will be
     * CUSTOM_DATABASE_DIR/Couchbase/tmp.
     */
    private String tempDir() {
        File temp = null;
        if (customDir) {
            temp = new File(directory, TEMP_DIR_NAME);
        } else
            temp = new File(context.getCacheDir(), TEMP_DIR_NAME);

        if (temp.exists() || temp.mkdirs()) {
            if (temp.isDirectory())
                return temp.getAbsolutePath();
        }
        throw new IllegalStateException("Cannot create or access temp directory at " +
                temp.getAbsolutePath());
    }
}
