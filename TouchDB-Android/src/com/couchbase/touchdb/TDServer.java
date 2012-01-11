/**
 * Original iOS version by  Jens Alfke
 * Ported to Android by Marty Schoch
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
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

package com.couchbase.touchdb;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class TDServer {

    public static final String LEGAL_CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789-";
    public static final String DATABASE_SUFFIX = ".toydb";

    private File directory;
    private Map<String, TDDatabase> databases;

    public TDServer(String directoryName) throws IOException {
        this.directory = new File(directoryName);
        this.databases = new HashMap<String, TDDatabase>();

        //create the directory, but don't fail if it already exists
        if(!directory.exists()) {
            boolean result = directory.mkdir();
            if(!result) {
                throw new IOException("Unable to create directory " + directory);
            }
        }
    }

    private String pathForName(String name) {
        if((name == null) || (name.length() == 0) || Pattern.matches("^" + LEGAL_CHARACTERS, name)) {
            return null;
        }
        String result = directory.getPath() + File.separator + name + DATABASE_SUFFIX;
        return result;
    }

    public TDDatabase getDatabaseNamed(String name) {
        TDDatabase result = databases.get(name);
        if(result == null) {
            String path = pathForName(name);
            if(path == null) {
                return null;
            }
            result = new TDDatabase(path);
            databases.put(name, result);
        }
        return result;
    }

    public boolean deleteDatabaseNamed(String name) {
        TDDatabase result = databases.get(name);
        if(result != null) {
            result.close();
            databases.remove(name);
        }
        String path = pathForName(name);
        if(path == null) {
            return false;
        }
        File databaseFile = new File(path);
        return databaseFile.delete();
    }

    public List<String> allDatabaseNames() {
        String[] databaseFiles = directory.list(new FilenameFilter() {

            @Override
            public boolean accept(File dir, String filename) {
                if(filename.endsWith(DATABASE_SUFFIX)) {
                    return true;
                }
                return false;
            }
        });
        List<String> result = Arrays.asList(databaseFiles);
        return result;
    }

    public void close() {
        for (TDDatabase database : databases.values()) {
            database.close();
        }
        databases.clear();
    }

}
