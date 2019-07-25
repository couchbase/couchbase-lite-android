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

import android.content.Context;
import android.os.Build;
import android.support.test.InstrumentationRegistry;

import java.io.IOException;
import java.io.InputStream;

import com.couchbase.lite.internal.ExecutionService;

/**
 * Platform test class for Android.
 */
public abstract class PlatformBaseTest implements PlatformTest {

    public static final String TAG = "Test";

    @Override
    public void initCouchbaseLite() {
        final Context c = InstrumentationRegistry.getTargetContext();
        CouchbaseLite.init(c);
    }

    @Override
    public String getDatabaseDirectory() {
        return CouchbaseLite.getDbDirectoryPath();
    }

    @Override
    public String getTempDirectory(String name) {
        return CouchbaseLite.getTmpDirectory(name);
    }

    @Override
    public boolean isAndroid() {
        return false;
    }

    @Override
    public boolean isAndroidEmulator() {
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

    @Override
    public boolean isDebugBuild() {
        return BuildConfig.DEBUG;
    }

    @Override
    public int getSystemVersion() {
        return Build.VERSION.SDK_INT;
    }

    @Override
    public InputStream getAsset(String asset) {
        try {
            return CouchbaseLite.getContext().getAssets().open(asset);
        }
        catch (IOException e) {
            return null;
        }
    }

    private static String getSystemProperty(String name) throws Exception {
        Class<?> systemPropertyClazz = Class.forName("android.os.SystemProperties");
        return (String) systemPropertyClazz
                .getMethod("get", new Class[] {String.class})
                .invoke(systemPropertyClazz, new Object[] {name});
    }

    public void log(LogLevel level, String domain, String message) {
        switch (level) {
            case DEBUG:
                android.util.Log.d("CouchbaseLite/" + domain, message);
                break;
            case VERBOSE:
                android.util.Log.v("CouchbaseLite/" + domain, message);
                break;
            case INFO:
                android.util.Log.i("CouchbaseLite/" + domain, message);
                break;
            case WARNING:
                android.util.Log.w("CouchbaseLite/" + domain, message);
                break;
            case ERROR:
                android.util.Log.e("CouchbaseLite/" + domain, message);
                break;
        }
    }

    @Override
    public void executeAsync(long delayMs, Runnable task) {
        ExecutionService executionService = CouchbaseLite.getExecutionService();
        executionService.postDelayedOnExecutor(delayMs, executionService.getMainExecutor(), task);
    }

}
