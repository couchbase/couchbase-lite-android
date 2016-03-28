/**
 * Created by Pasin Suriyentrakorn on 10/6/15
 *
 * Copyright (c) 2015 Couchbase, Inc. All rights reserved.
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

package com.couchbase.lite.performance;

import com.couchbase.lite.Context;
import com.couchbase.lite.Database;
import com.couchbase.lite.LiteTestCase;
import com.couchbase.lite.Manager;
import com.couchbase.lite.ManagerOptions;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.replicator.ReplicationState;
import com.couchbase.lite.storage.SQLiteNativeLibrary;
import com.couchbase.lite.support.FileDirUtils;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PerformanceTestCase extends LiteTestCase {
    public static final String TAG = "PerformanceTestCase";

    protected Manager manager = null;
    protected Database database = null;
    protected static final String DEFAULT_TEST_DB = "perftestdb";
    protected static final String DEFAULT_TEST_DIR_NAME = "perftest";

    @Override
    protected void setUp() throws Exception {
        // Load performance test properties:
        loadTestProperties();

        // Enabled logging:
        Manager.enableLogging(TAG, Log.VERBOSE);
        String testTag = getTestTag();
        if (testTag != null)
            Manager.enableLogging(testTag, Log.VERBOSE);

        // SQLite native library:
        String storageType = getStorageType();
        if (Manager.SQLITE_STORAGE.equals(storageType)) {
            setupSQLiteNativeLibrary();
        }

        // Manager:
        Context context = getDefaultTestContext(true);
        ManagerOptions options = new ManagerOptions();
        manager = new Manager(context, options);
        manager.setStorageType(getStorageType());

        // Encryption:
        if (getEncryptionEnabled()) {
            String passwd = getEncryptionPassword();
            manager.registerEncryptionKey((passwd.length() > 0 ? passwd : null), DEFAULT_TEST_DB);
        }

        // Database:
        startDatabase();
    }

    protected void setupSQLiteNativeLibrary() {
        int library = getSQLiteLibrary();
        if (library == 0)
            SQLiteNativeLibrary.TEST_NATIVE_LIBRARY_NAME = SQLiteNativeLibrary.JNI_SQLITE_DEFAULT_LIBRARY;
        else if (library == 1)
            SQLiteNativeLibrary.TEST_NATIVE_LIBRARY_NAME = SQLiteNativeLibrary.JNI_SQLITE_CUSTOM_LIBRARY;
        else if (library == 2)
            SQLiteNativeLibrary.TEST_NATIVE_LIBRARY_NAME = SQLiteNativeLibrary.JNI_SQLCIPHER_LIBRARY;
        else
            throw new IllegalArgumentException("Invalid SQLiteDatabase Library : " + library);
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        closeDatabase();
        closeManager();
    }

    protected void loadTestProperties() throws IOException {
        Properties systemProperties = System.getProperties();
        InputStream mainProperties = getAsset("perftest.properties");
        if (mainProperties != null) {
            systemProperties.load(new InputStreamReader(mainProperties, "UTF-8"));
            mainProperties.close();
        }
        try {
            InputStream localProperties = getAsset("local-perftest.properties");
            if (localProperties != null) {
                systemProperties.load(new InputStreamReader(localProperties, "UTF-8"));
                localProperties.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading local-perftest.properties", e);
            throw e;
        }
    }

    protected String getTestTag() {
        return null;
    }

    protected InputStream getAsset(String name) {
        return this.getClass().getResourceAsStream("/assets/" + name);
    }

    protected Context getDefaultTestContext(boolean deleteContent) {
        return getTestContext(DEFAULT_TEST_DIR_NAME, deleteContent);
    }

    protected Context getTestContext(String dirName, boolean deleteContent) {
        Context context = getTestContext(dirName);
        if (deleteContent)
            FileDirUtils.cleanDirectory(context.getFilesDir());
        return context;
    }

    protected static boolean performanceTestsEnabled() {
        return Boolean.parseBoolean(System.getProperty("enabled"));
    }

    protected static String getStorageType() {
        return System.getProperty("storageType");
    }

    protected static int getSQLiteLibrary() {
        return Integer.parseInt(System.getProperty("sqliteLibrary"));
    }

    protected static boolean getEncryptionEnabled() {
        return getSQLiteLibrary() == 2;
    }

    protected static String getEncryptionPassword() {
        return System.getProperty("encryptionPassword");
    }

    protected Database startDatabase() throws Exception {
        database = ensureEmptyDatabase(DEFAULT_TEST_DB);
        return database;
    }

    protected Database ensureEmptyDatabase(String dbName) throws Exception {
        Database db = manager.getExistingDatabase(dbName);
        if (db != null)
            db.delete();
        db = manager.getDatabase(dbName);
        return db;
    }

    protected void closeDatabase() {
        if (database != null) {
            database.close();
        }
    }

    protected void closeManager() {
        int DEFAULT_VALUE = Utils.DEFAULT_TIME_TO_WAIT_4_SHUTDOWN;
        Utils.DEFAULT_TIME_TO_WAIT_4_SHUTDOWN = 0;
        try {
            if (manager != null)
                manager.close();
        } finally {
            Utils.DEFAULT_TIME_TO_WAIT_4_SHUTDOWN = DEFAULT_VALUE;
        }
    }

    protected URL getReplicationUrl() throws MalformedURLException {
        return new URL(System.getProperty("replicationUrl"));
    }

    public void runReplication(Replication replication) throws Exception {
        final CountDownLatch replicationDoneSignal = new CountDownLatch(1);
        replication.addChangeListener(new ReplicationFinishedObserver(replicationDoneSignal));
        replication.start();
        boolean success = replicationDoneSignal.await(60, TimeUnit.SECONDS);
        assertTrue(success);
    }

    static class ReplicationFinishedObserver implements Replication.ChangeListener {
        private CountDownLatch doneSignal;

        public ReplicationFinishedObserver(CountDownLatch doneSignal) {
            this.doneSignal = doneSignal;
        }

        @Override
        public void changed(Replication.ChangeEvent event) {
            if (event.getTransition().getDestination() == ReplicationState.STOPPED) {
                doneSignal.countDown();
                assertEquals(event.getChangeCount(), event.getCompletedChangeCount());
            }
        }
    }

    protected void logPerformanceStats(long time, String comment) {
        String tag = getTestTag();
        Log.v((tag != null ? tag : TAG), "PerformanceStats: " + time + " msec" +
                (comment != null ? " (" + comment + ")" : ""));
    }
}
