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

import android.content.Context;
import android.support.test.InstrumentationRegistry;


import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.JsonUtils;
import com.couchbase.lite.utils.Config;
import com.couchbase.lite.utils.FileUtils;
import com.couchbase.litecore.C4Constants;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.Map;

import static com.couchbase.lite.utils.Config.TEST_PROPERTIES_FILE;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorBusy;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class BaseTest implements C4Constants {
    public static final String TAG = "Test";

    protected final static String kDatabaseName = "testdb";

    protected Config config;
    protected Context context;
    protected File dir;
    protected Database db = null;
    protected ConflictResolver conflictResolver = null;

    @Before
    public void setUp() throws Exception {

        Database.setLogLevel(Database.LogDomain.ALL, Database.LogLevel.INFO);
        Log.enableLogging(TAG, Log.INFO, false);

        Log.i(TAG, "setUp() - BEGIN");

        context = InstrumentationRegistry.getTargetContext();
        try {
            config = new Config(context.getAssets().open(TEST_PROPERTIES_FILE));
        } catch (IOException e) {
            fail("Failed to load test.properties");
        }

        dir = new File(context.getFilesDir(), "CouchbaseLite");

        deleteDatabase(kDatabaseName);

        FileUtils.cleanDirectory(dir);

        openDB();

        Log.i(TAG, "setUp() - END");
    }

    @After
    public void tearDown() throws Exception {
        Log.i(TAG, "tearDown() - BEGIN");

        closeDB();

        // database exist, delete it
        deleteDatabase(kDatabaseName);

        // clean dir
        FileUtils.cleanDirectory(dir);

        Log.i(TAG, "tearDown() - END");
    }

    protected void deleteDatabase(String dbName) throws CouchbaseLiteException {
        // database exist, delete it
        if (Database.exists(dbName, dir)) {
            // sometimes, db is still in used, wait for a while. Maximum 3 sec
            for (int i = 0; i < 20; i++) {
                // while(true){
                try {
                    Database.delete(dbName, dir);
                    break;
                } catch (CouchbaseLiteException ex) {
                    if (ex.getCode() == kC4ErrorBusy) {
                        try {
                            Thread.sleep(500);
                        } catch (Exception e) {
                        }
                    } else {
                        throw ex;
                    }
                }
            }
        }
    }

    protected Database open(String name) throws CouchbaseLiteException {
        DatabaseConfiguration.Builder builder = new DatabaseConfiguration.Builder(this.context);
        builder.setDirectory(dir.getAbsolutePath());
        if (this.conflictResolver != null)
            builder.setConflictResolver(this.conflictResolver);
        return new Database(name, builder.build());
    }

    protected void openDB() throws CouchbaseLiteException {
        Log.i(TAG, "openDB() - BEGIN");
        assertNull(db);
        db = open(kDatabaseName);
        assertNotNull(db);
        Log.i(TAG, "openDB() - END");
    }

    protected void closeDB() throws CouchbaseLiteException {
        Log.i(TAG, "closeDB() - BEGIN");
        if (db != null) {
            db.close();
            db = null;
        }
        Log.i(TAG, "closeDB() - END");
    }

    protected void reopenDB() throws CouchbaseLiteException {
        closeDB();
        openDB();
    }

    protected InputStream getAsset(String name) {
        return this.getClass().getResourceAsStream("/assets/" + name);
    }

    protected void loadJSONResource(String name) throws Exception {
        InputStream is = getAsset(name);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        String line;
        int n = 0;
        while ((line = reader.readLine()) != null) {
            if (line.trim().isEmpty()) continue;
            n += 1;
            JSONObject json = new JSONObject(line);
            Map<String, Object> props = JsonUtils.fromJson(json);
            String docId = String.format(Locale.ENGLISH, "doc-%03d", n);
            MutableDocument doc = createMutableDocument(docId);
            doc.setData(props);
            save(doc);
        }
    }

    protected MutableDocument createMutableDocument() {
        return new MutableDocument();
    }

    protected MutableDocument createMutableDocument(String id) {
        return new MutableDocument(id);
    }

    protected MutableDocument createMutableDocument(String id, Map<String, Object> map) {
        return new MutableDocument(id, map);
    }

    protected Document save(MutableDocument doc) throws CouchbaseLiteException {
        Document newDoc = db.save(doc);
        assertNotNull(newDoc);
        return newDoc;
    }

    interface Validator<T> {
        void validate(final T doc);
    }

    protected Document save(MutableDocument mDoc, Validator<Document> validator) throws CouchbaseLiteException {
        validator.validate(mDoc);
        Document doc = db.save(mDoc);
        validator.validate(doc);
        return doc;
    }

    // helper method to save document
    protected Document generateDocument(String docID) throws CouchbaseLiteException {
        MutableDocument doc = createMutableDocument(docID);
        doc.setValue("key", 1);
        Document savedDoc = save(doc);
        assertEquals(1, db.getCount());
        assertEquals(1, savedDoc.getSequence());
        return savedDoc;
    }
}
