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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.codehaus.jackson.map.ObjectMapper;

import com.couchbase.touchdb.support.HttpClientFactory;

/**
 * Manages a directory containing TDDatabases.
 */
public class TDServer {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static final String LEGAL_CHARACTERS = "abcdefghijklmnopqrstuvwxyz0123456789_$()+-/";
    public static final String DATABASE_SUFFIX = ".touchdb";

    private File directory;
    private Map<String, TDDatabase> databases;

    private HttpClientFactory defaultHttpClientFactory;

    public static ObjectMapper getObjectMapper() {
        return mapper;
    }

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
        if((name == null) || (name.length() == 0) || Pattern.matches("^" + LEGAL_CHARACTERS, name) || !Character.isLowerCase(name.charAt(0))) {
            return null;
        }
        name = name.replace('/', ':');
        String result = directory.getPath() + File.separator + name + DATABASE_SUFFIX;
        return result;
    }

    public TDDatabase getDatabaseNamed(String name, boolean create) {
        TDDatabase db = databases.get(name);
        if(db == null) {
            String path = pathForName(name);
            if(path == null) {
                return null;
            }
            db = new TDDatabase(path);
            if(!create && !db.exists()) {
                return null;
            }
            db.setName(name);
            databases.put(name, db);
        }
        return db;
    }

    public TDDatabase getDatabaseNamed(String name) {
        return getDatabaseNamed(name, true);
    }

    public TDDatabase getExistingDatabaseNamed(String name) {
        TDDatabase db = getDatabaseNamed(name, false);
        if((db != null) && !db.open()) {
            return null;
        }
        return db;
    }

    public boolean deleteDatabaseNamed(String name) {
        TDDatabase db = databases.get(name);
        if(db == null) {
            return false;
        }
        db.deleteDatabase();
        databases.remove(name);
        return true;
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
        List<String> result = new ArrayList<String>();
        for (String databaseFile : databaseFiles) {
            String trimmed = databaseFile.substring(0, databaseFile.length() - DATABASE_SUFFIX.length());
            String replaced = trimmed.replace(':', '/');
            result.add(replaced);
        }
        Collections.sort(result);
        return result;
    }

    public Collection<TDDatabase> allOpenDatabases() {
        return databases.values();
    }

    public void close() {
        for (TDDatabase database : databases.values()) {
            database.close();
        }
        databases.clear();
    }

    public HttpClientFactory getDefaultHttpClientFactory() {
        return defaultHttpClientFactory;
    }

    public void setDefaultHttpClientFactory(
            HttpClientFactory defaultHttpClientFactory) {
        this.defaultHttpClientFactory = defaultHttpClientFactory;
    }

}
