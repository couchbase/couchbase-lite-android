//
// PlatformBaseTest.java
//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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

import android.support.test.InstrumentationRegistry;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import org.junit.After;
import org.junit.Before;

import com.couchbase.lite.internal.CouchbaseLiteInternal;
import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.support.Log;


/**
 * Platform test class for Android.
 */
public abstract class PlatformBaseTest implements PlatformTest {
    public static final String PRODUCT = "Android";
    public static final String LEGAL_FILE_NAME_CHARS = "`~@#$%^&*()_+{}|\\][=-/.,<>?\":;'ABCDEabcde";
    public static final String DB_EXTENSION = AbstractDatabase.DB_EXTENSION;

    public static void initCouchbase() { CouchbaseLite.init(InstrumentationRegistry.getTargetContext()); }

    public static void deinitCouchbase() { CouchbaseLiteInternal.reset(); }

    // this should probably go in the BaseTest but
    // there are several tests (C4 tests) that are not subclasses
    static { initCouchbase(); }


    private String tmpDirPath;

    // Before and After naming conventions
    @Before
    public void setUp() throws CouchbaseLiteException { }

    @After
    public void tearDown() { }

    // make a half-hearted attempt to set up file logging
    @Override
    public void setupFileLogging() {
        try {
            final FileLogger fileLogger = Database.log.getFile();
            final File logDir = InstrumentationRegistry.getTargetContext().getExternalFilesDir("logs");

            if (logDir == null) { throw new IllegalStateException("Cannot find external files directory"); }

            fileLogger.setConfig(new LogFileConfiguration(logDir.getCanonicalPath()));

            fileLogger.setLevel(LogLevel.INFO);
        }
        catch (IOException ignore) { }
    }

    @Override
    public String getDatabaseDirectoryPath() { return CouchbaseLiteInternal.getDbDirectoryPath(); }

    @Override
    public String getScratchDirectoryPath(String name) {
        if (tmpDirPath == null) { tmpDirPath = CouchbaseLiteInternal.getTmpDirectoryPath(); }

        try { return new File(tmpDirPath, name).getCanonicalPath(); }
        catch (IOException e) { throw new RuntimeException("Could not open tmp directory: " + name, e); }
    }

    @Override
    public InputStream getAsset(String asset) {
        try { return CouchbaseLiteInternal.getContext().getAssets().open(asset); }
        catch (IOException ignore) { }
        return null;
    }

    @Override
    public void executeAsync(long delayMs, Runnable task) {
        ExecutionService executionService = CouchbaseLiteInternal.getExecutionService();
        executionService.postDelayedOnExecutor(delayMs, executionService.getMainExecutor(), task);
    }

    @Override
    public void reloadStandardErrorMessages() {
        Log.initLogging(CouchbaseLiteInternal.loadErrorMessages(InstrumentationRegistry.getTargetContext()));

    }

    private static String getSystemProperty(String name) throws Exception {
        Class<?> systemPropertyClazz = Class.forName("android.os.SystemProperties");
        return (String) systemPropertyClazz
            .getMethod("get", String.class)
            .invoke(systemPropertyClazz, new Object[] {name});
    }
}
