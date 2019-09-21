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

import java.io.IOException;
import java.io.InputStream;

import com.couchbase.lite.internal.ExecutionService;


/**
 * Platform test class for Android.
 */
public abstract class PlatformBaseTest implements PlatformTest {
    public static final String PRODUCT = "Android";
    public static final String LEGAL_FILE_NAME_CHARS = "`~@#$%^&*()_+{}|\\][=-/.,<>?\":;'ABCDEabcde";

    @Override
    public void initCouchbaseLite() { CouchbaseLite.init(InstrumentationRegistry.getTargetContext()); }

    @Override
    public String getDatabaseDirectory() { return CouchbaseLite.getDbDirectoryPath(); }

    @Override
    public String getTempDirectory(String name) { return CouchbaseLite.getTmpDirectory(name); }

    // make a half-hearted attempt to set up file logging
    public void setupFileLogging() {
        try {
            FileLogger fileLogger = Database.log.getFile();
            fileLogger.setConfig(new LogFileConfiguration(
                InstrumentationRegistry.getTargetContext().getExternalFilesDir("logs").getAbsolutePath()));
            fileLogger.setLevel(LogLevel.INFO);
        }
        catch (Exception ignore) { }
    }

    @Override
    public InputStream getAsset(String asset) {
        try { return CouchbaseLite.getContext().getAssets().open(asset); }
        catch (IOException ignore) { }
        return null;
    }

    @Override
    public void executeAsync(long delayMs, Runnable task) {
        ExecutionService executionService = CouchbaseLite.getExecutionService();
        executionService.postDelayedOnExecutor(delayMs, executionService.getMainExecutor(), task);
    }

    private static String getSystemProperty(String name) throws Exception {
        Class<?> systemPropertyClazz = Class.forName("android.os.SystemProperties");
        return (String) systemPropertyClazz
            .getMethod("get", String.class)
            .invoke(systemPropertyClazz, new Object[] {name});
    }
}
