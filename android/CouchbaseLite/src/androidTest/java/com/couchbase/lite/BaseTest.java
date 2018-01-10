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
    public static final String TAG = BaseTest.class.getSimpleName();

    protected final static String kDatabaseName = "testdb";

    protected Config config;
    protected Context context;
    protected File dir;
    protected Database db = null;
    protected ConflictResolver conflictResolver = null;

    @Before
    public void setUp() throws Exception {

        Database.setLogLevel(Database.LogDomain.ALL, Database.LogLevel.INFO);

        Log.i(TAG, "setUp");

        context = InstrumentationRegistry.getTargetContext();
        try {
            config = new Config(context.getAssets().open(TEST_PROPERTIES_FILE));
        } catch (IOException e) {
            fail("Failed to load test.properties");
        }
        dir = new File(context.getFilesDir(), "CouchbaseLite");
        FileUtils.cleanDirectory(dir);
        openDB();
    }

    @After
    public void tearDown() throws Exception {
        Log.i(TAG, "tearDown");
        if (db != null) {
            db.close();
            db = null;
        }

        // database exist, delete it
        deleteDatabase(kDatabaseName);

        // clean dir
        FileUtils.cleanDirectory(dir);
    }

    protected void deleteDatabase(String dbName) throws CouchbaseLiteException {
        // database exist, delete it
        if (Database.exists(dbName, dir)) {
            // sometimes, db is still in used, wait for a while. Maximum 3 sec
            for (int i = 0; i < 10; i++) {
                try {
                    Database.delete(dbName, dir);
                    break;
                } catch (CouchbaseLiteException ex) {
                    if (ex.getCode() == kC4ErrorBusy) {
                        try {
                            Thread.sleep(300);
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
        assertNull(db);
        db = open(kDatabaseName);
        assertNotNull(db);
    }

    protected void closeDB() throws CouchbaseLiteException {
        if (db != null) {
            db.close();
            db = null;
        }
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
            MutableDocument doc = createDocument(docId);
            doc.setData(props);
            save(doc);
        }
    }

    protected MutableDocument createDocument() {
        return new MutableDocument();
    }

    protected MutableDocument createDocument(String id) {
        return new MutableDocument(id);
    }

    protected MutableDocument createDocument(String id, Map<String, Object> map) {
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
        MutableDocument doc = createDocument(docID);
        doc.setValue("key", 1);
        Document savedDoc = save(doc);
        assertEquals(1, db.getCount());
        assertEquals(1, savedDoc.getSequence());
        return savedDoc;
    }
}
