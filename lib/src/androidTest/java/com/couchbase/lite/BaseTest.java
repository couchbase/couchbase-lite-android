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
import android.content.res.AssetManager;
import android.support.test.InstrumentationRegistry;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ExpectedException;

import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.utils.JsonUtils;
import com.couchbase.lite.utils.Config;
import com.couchbase.lite.utils.FileUtils;
import com.couchbase.lite.utils.Report;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class BaseTest {
    public static final String TAG = "Test";
    public final static String TEST_DB = "testdb";

    protected interface Execution {
        void run() throws CouchbaseLiteException;
    }

    interface Validator<T> {
        void validate(final T doc);
    }

    protected Config config;
    protected Database db;
    protected ExecutionService.CloseableExecutor executor;

    private AtomicReference<AssertionError> testFailure;

    private File dir;

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        final Context ctxt = InstrumentationRegistry.getTargetContext();
        CouchbaseLite.init(ctxt);

        executor = CouchbaseLite.getExecutionService().getSerialExecutor();
        testFailure = new AtomicReference<>();

        try { config = new Config(openTestPropertiesFile()); }
        catch (IOException e) { fail("Failed to load test.properties"); }

        setDir(new File(ctxt.getFilesDir(), "CouchbaseLiteTest"));

        // database exist, delete it
        deleteDatabase(TEST_DB);

        // clean dir
        FileUtils.cleanDirectory(dir);

        openDB();
    }

    @After
    public void tearDown() {
        try {
            closeDB();
            // database exist, delete it
            deleteDatabase(TEST_DB);
        }
        catch (CouchbaseLiteException ignore) {
            Report.log("Failed closing DB: " + TEST_DB, ignore);
        }

        // clean dir
        FileUtils.cleanDirectory(getDir());

        executor.stop(60, TimeUnit.SECONDS);
        executor = null;
    }

    protected File getDir() { return dir; }

    protected void setDir(File dir) {
        assertNotNull(dir);
        this.dir = dir;
    }

    protected static boolean isEmulator() {
        try {
            boolean goldfish = getSystemProperty("ro.hardware").contains("goldfish");
            boolean emu = getSystemProperty("ro.kernel.qemu").length() > 0;
            boolean sdk = getSystemProperty("ro.product.model").equals("sdk");
            return goldfish || emu || sdk;
        }
        catch (Exception e) {
            return false;
        }
    }

    protected static boolean isARM() {
        String arch = System.getProperty("os.arch").toLowerCase();
        return arch.indexOf("arm") != -1;
    }

    protected void deleteDatabase(String dbName) throws CouchbaseLiteException {
        if (config != null && !config.deleteDatabaseInTearDown()) { return; }

        // database exist, delete it
        if (Database.exists(dbName, getDir())) {
            // sometimes, db is still in used, wait for a while. Maximum 3 sec
            for (int i = 0; i < 20; i++) {
                // while(true){
                try {
                    Database.delete(dbName, getDir());
                    break;
                }
                catch (CouchbaseLiteException ex) {
                    if (ex.getCode() == CBLError.Code.BUSY) {
                        try {
                            Thread.sleep(500);
                        }
                        catch (Exception e) {
                        }
                    }
                    else {
                        throw ex;
                    }
                }
            }
        }
    }

    protected Database openDB(String name) throws CouchbaseLiteException {
        DatabaseConfiguration config = new DatabaseConfiguration();
        config.setDirectory(dir.getAbsolutePath());
        return new Database(name, config);
    }

    protected void openDB() throws CouchbaseLiteException {
        assertNull(db);
        db = openDB(TEST_DB);
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
            if (line.trim().isEmpty()) { continue; }
            n += 1;
            JSONObject json = new JSONObject(line);
            Map<String, Object> props = JsonUtils.fromJson(json);
            String docId = String.format(Locale.ENGLISH, "doc-%03d", n);
            MutableDocument doc = new MutableDocument(docId);
            doc.setData(props);
            save(doc);
        }
    }

    protected Document save(MutableDocument doc) throws CouchbaseLiteException {
        db.save(doc);
        Document savedDoc = db.getDocument(doc.getId());
        assertNotNull(savedDoc);
        assertEquals(doc.getId(), savedDoc.getId());
        return savedDoc;
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
        MutableDocument doc = new MutableDocument(docID);
        doc.setValue("key", 1);
        save(doc);
        Document savedDoc = db.getDocument(docID);
        assertTrue(db.getCount() > 0);
        assertEquals(1, savedDoc.getSequence());
        return savedDoc;
    }

    protected String createDocNumbered(int i, int num) throws CouchbaseLiteException {
        String docID = String.format(Locale.ENGLISH, "doc%d", i);
        MutableDocument doc = new MutableDocument(docID);
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
                    }
                    catch (CouchbaseLiteException e) {
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

    protected void runSafely(Runnable test) {
        try { test.run(); }
        catch (AssertionError failure) {
            log(LogLevel.DEBUG, "Test failed: " + failure);
            testFailure.compareAndSet(null, failure);
        }
    }

    protected void runSafelyInThread(CountDownLatch latch, Runnable test) {
        new Thread(() -> {
            try { test.run(); }
            catch (AssertionError failure) {
                log(LogLevel.DEBUG, "Test failed: " + failure);
                testFailure.compareAndSet(null, failure);
            }
            finally { latch.countDown(); }
        }).start();
    }

    protected void checkForFailure() {
        AssertionError failure = testFailure.get();
        if (failure != null) { throw new AssertionError(failure); }
    }

    protected static void expectError(String domain, int code, Execution execution) {
        try {
            execution.run();
            fail();
        }
        catch (CouchbaseLiteException e) {
            assertEquals(domain, e.getDomain());
            assertEquals(code, e.getCode());
        }
    }

    protected static void log(LogLevel level, String message) {
        switch (level) {
            case DEBUG:
                android.util.Log.d("CouchbaseLite/" + TAG, message);
                break;
            case VERBOSE:
                android.util.Log.v("CouchbaseLite/" + TAG, message);
                break;
            case INFO:
                android.util.Log.i("CouchbaseLite/" + TAG, message);
                break;
            case WARNING:
                android.util.Log.w("CouchbaseLite/" + TAG, message);
                break;
            case ERROR:
                android.util.Log.e("CouchbaseLite/" + TAG, message);
                break;
        }
    }

    private InputStream openTestPropertiesFile() throws IOException {
        return CouchbaseLite.getContext().getAssets().open(Config.TEST_PROPERTIES_FILE);
    }

    // https://stackoverflow.com/questions/2799097/how-can-i-detect-when-an-android-application-is-running-in-the
    // -emulator?noredirect=1&lq=1
    private static String getSystemProperty(String name) throws Exception {
        Class<?> systemPropertyClazz = Class.forName("android.os.SystemProperties");
        return (String) systemPropertyClazz
            .getMethod("get", new Class[] {String.class}).invoke(systemPropertyClazz, new Object[] {name});
    }
}
