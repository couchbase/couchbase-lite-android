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
import android.util.Log;

import com.couchbase.lite.internal.support.JsonUtils;
import com.couchbase.lite.utils.Config;
import com.couchbase.lite.utils.FileUtils;

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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class BaseTest {
    public static final String TAG = BaseTest.class.getSimpleName();

    protected final static String kDatabaseName = "testdb";

    protected Config config;
    protected Context context;
    protected File dir;
    protected Database db = null;

    @Before
    public void setUp() throws Exception {
        Log.e("BaseTest", "setUp");
        context = InstrumentationRegistry.getContext();
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
        Log.e("BaseTest", "tearDown");
        if (db != null) {
            db.close();
            db = null;
        }
        if (Database.exists(kDatabaseName, dir))
            Database.delete(kDatabaseName, dir);
        FileUtils.cleanDirectory(dir);
    }

    protected Database open(String name){
        DatabaseConfiguration options = new DatabaseConfiguration(this.context);
        options.setDirectory(dir);
        return new Database(name, options);
    }

    protected void openDB() {
        assertNull(db);
        db = open(kDatabaseName);
        assertNotNull(db);
    }

    protected void closeDB() {
        if (db != null) {
            db.close();
            db = null;
        }
    }

    protected void reopenDB() {
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
            Document doc = createDocument(docId);
            doc.set(props);
            save(doc);
        }
    }

    protected Document createDocument(String id) {
        return new Document(id);
    }

    protected Document createDocument(String id, Map<String, Object> map) {
        return new Document(id, map);
    }

    protected Document save(Document doc) {
        db.save(doc);
        return db.getDocument(doc.getId());
    }

    interface Validator<T> {
        void validate(final T doc);
    }

    protected Document save(Document doc, Validator<Document> validator) {
        validator.validate(doc);
        db.save(doc);
        doc = db.getDocument(doc.getId());
        validator.validate(doc);
        return doc;
    }
}

