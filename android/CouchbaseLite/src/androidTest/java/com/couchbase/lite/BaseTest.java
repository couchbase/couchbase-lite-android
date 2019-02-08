//
// BaseTest.java
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
import android.support.test.InstrumentationRegistry;

import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ExecutorUtils;
import com.couchbase.lite.internal.utils.JsonUtils;
import com.couchbase.lite.utils.Config;
import com.couchbase.lite.utils.FileUtils;
import com.couchbase.litecore.C4Constants;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.couchbase.lite.utils.Config.EE_TEST_PROPERTIES_FILE;
import static com.couchbase.lite.utils.Config.TEST_PROPERTIES_FILE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class BaseTest implements C4Constants, CBLError.Domain, CBLError.Code {
    public static final String TAG = "Test";

    protected final static String kDatabaseName = "testdb";

    protected Config config;
    protected Context context;
    private File dir = null;
    protected Database db = null;
    ExecutorService executor = null;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    protected File getDir() {
        return dir;
    }

    protected void setDir(File dir) {
        assertNotNull(dir);
        this.dir = dir;
    }

    // https://stackoverflow.com/questions/2799097/how-can-i-detect-when-an-android-application-is-running-in-the-emulator?noredirect=1&lq=1
    private static String getSystemProperty(String name) throws Exception {
        Class systemPropertyClazz = Class.forName("android.os.SystemProperties");
        return (String) systemPropertyClazz.getMethod("get", new Class[]{String.class}).invoke(systemPropertyClazz, new Object[]{name});
    }

    protected static boolean isEmulator() {
        try {
            boolean goldfish = getSystemProperty("ro.hardware").contains("goldfish");
            boolean emu = getSystemProperty("ro.kernel.qemu").length() > 0;
            boolean sdk = getSystemProperty("ro.product.model").equals("sdk");
            return goldfish || emu || sdk;
        } catch (Exception e) {
            return false;
        }
    }

    protected static boolean isARM() {
        String arch = System.getProperty("os.arch").toLowerCase();
        return arch.indexOf("arm") != -1;
    }

    @Before
    public void setUp() throws Exception {
        Database.setLogLevel(LogDomain.ALL, LogLevel.INFO);
        Log.enableLogging(TAG, Log.INFO); // NOTE: Without loading Database, this fails.

        executor = Executors.newSingleThreadExecutor();

        context = InstrumentationRegistry.getTargetContext();
        try {
            config = new Config(openTestPropertiesFile());
        } catch (IOException e) {
            fail("Failed to load test.properties");
        }

        setDir(new File(context.getFilesDir(), "CouchbaseLiteTest"));

        // database exist, delete it
        deleteDatabase(kDatabaseName);

        // clean dir
        FileUtils.cleanDirectory(dir);

        openDB();
    }

    private InputStream openTestPropertiesFile() throws IOException {
        try {
            return context.getAssets().open(EE_TEST_PROPERTIES_FILE);
        } catch (IOException e) {
            return context.getAssets().open(TEST_PROPERTIES_FILE);
        }
    }

    @After
    public void tearDown() throws Exception {
        closeDB();

        // database exist, delete it
        deleteDatabase(kDatabaseName);

        // clean dir
        FileUtils.cleanDirectory(getDir());

        ExecutorUtils.shutdownAndAwaitTermination(executor, 60);
        executor = null;
    }

    protected void deleteDatabase(String dbName) throws CouchbaseLiteException {
        if (config != null && !config.deleteDatabaseInTearDown())
            return;

        // database exist, delete it
        if (Database.exists(dbName, getDir())) {
            // sometimes, db is still in used, wait for a while. Maximum 3 sec
            for (int i = 0; i < 20; i++) {
                // while(true){
                try {
                    Database.delete(dbName, getDir());
                    break;
                } catch (CouchbaseLiteException ex) {
                    if (ex.getCode() == CBLErrorBusy) {
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
        DatabaseConfiguration config = new DatabaseConfiguration(this.context);
        config.setDirectory(dir.getAbsolutePath());
        return new Database(name, config);
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

    protected void cleanDB() throws CouchbaseLiteException {
        if (db != null) {
            db.delete();
            db = null;
        }
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
        db.save(doc);
        Document savedDoc = db.getDocument(doc.getId());
        assertNotNull(savedDoc);
        assertEquals(doc.getId(), savedDoc.getId());
        return savedDoc;
    }

    interface Validator<T> {
        void validate(final T doc);
    }

    protected Document save(MutableDocument doc, Validator<Document> validator) throws CouchbaseLiteException {
        validator.validate(doc);
        db.save(doc);
        Document savedDoc = db.getDocument(doc.getId());
        validator.validate(doc);
        validator.validate(savedDoc);
        return savedDoc;
    }

    // helper method to save document
    protected Document generateDocument(String docID) throws CouchbaseLiteException {
        MutableDocument doc = createMutableDocument(docID);
        doc.setValue("key", 1);
        save(doc);
        Document savedDoc = db.getDocument(docID);
        assertEquals(1, db.getCount());
        assertEquals(1, savedDoc.getSequence());
        return savedDoc;
    }

    protected String createDocNumbered(int i, int num) throws CouchbaseLiteException {
        String docID = String.format(Locale.ENGLISH, "doc%d", i);
        MutableDocument doc = createMutableDocument(docID);
        doc.setValue("number1", i);
        doc.setValue("number2", num - i);
        save(doc);
        return docID;
    }

    protected List<Map<String, Object>> loadNumbers(final int num) throws Exception {
        return loadNumbers(1, num);
    }

    protected List<Map<String, Object>> loadNumbers(final int from, final int to) throws Exception {
        final List<Map<String, Object>> numbers = new ArrayList<Map<String, Object>>();
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                for (int i = from; i <= to; i++) {
                    String docID;
                    try {
                        docID = createDocNumbered(i, to);
                    } catch (CouchbaseLiteException e) {
                        throw new RuntimeException(e);
                    }
                    numbers.add(db.getDocument(docID).toMap());
                }
            }
        });
        return numbers;
    }

    protected interface QueryResult {
        void check(int n, Result result) throws Exception;
    }

    protected static int verifyQuery(Query query, QueryResult queryResult) throws Exception {
        int n = 0;
        Result result;
        ResultSet rs = query.execute();
        while ((result = rs.next()) != null) {
            n += 1;
            queryResult.check(n, result);
        }
        return n;
    }

    protected static int verifyQueryWithIterable(Query query, QueryResult queryResult) throws Exception {
        int n = 0;
        for (Result result : query.execute()) {
            n += 1;
            queryResult.check(n, result);
        }
        return n;
    }

    protected static int verifyQuery(Query query, QueryResult result, boolean runBoth) throws Exception {
        int counter1 = verifyQuery(query, result);
        if (runBoth) {
            int counter2 = verifyQueryWithIterable(query, result);
            assertEquals(counter1, counter2);
        }
        return counter1;
    }

    protected interface Execution {
        void run() throws CouchbaseLiteException;
    }

    protected static void expectError(String domain, int code, Execution execution) {
        try {
            execution.run();
            fail();
        } catch (CouchbaseLiteException e) {
            assertEquals(domain, e.getDomain());
            assertEquals(code, e.getCode());
        }
    }
}
