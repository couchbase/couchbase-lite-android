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
package com.couchbase.lite.internal;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONException;
import org.json.JSONObject;

import com.couchbase.lite.BuildConfig;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.R;
import com.couchbase.lite.internal.core.C4Base;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


public final class CouchbaseLiteInternal {
    // Utility class
    private CouchbaseLiteInternal() {}

    private static final String LITECORE_JNI_LIBRARY = "LiteCoreJNI";

    private static final String TEMP_DIR_NAME = "CouchbaseLiteTemp";

    private static final AtomicReference<ExecutionService> EXECUTION_SERVICE = new AtomicReference<>();
    private static final AtomicReference<SoftReference<Context>> CONTEXT = new AtomicReference<>();

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private static volatile boolean debugging = BuildConfig.CBL_DEBUG;

    private static volatile String dbDirPath;
    private static volatile String tmpDirPath;

    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     */
    public static void init(
        @NonNull MValue.Delegate mValueDelegate,
        @Nullable String rootDirectoryPath,
        @NonNull Context ctxt) {
        Preconditions.checkArgNotNull(ctxt, "context");

        if (INITIALIZED.getAndSet(true)) { return; }

        System.loadLibrary(LITECORE_JNI_LIBRARY);

        MValue.registerDelegate(mValueDelegate);

        CONTEXT.set(new SoftReference<>(ctxt.getApplicationContext()));

        Log.initLogging(loadErrorMessages(ctxt));
        com.couchbase.lite.Database.log.getConsole().setLevel(LogLevel.WARNING);

        setupDirectories(rootDirectoryPath);
    }

    public static boolean isDebugging() { return debugging; }

    /**
     * This method is not part of the public API.
     * It will be removed in a future release.
     */
    public static ExecutionService getExecutionService() {
        ExecutionService executionService = EXECUTION_SERVICE.get();
        if (executionService == null) {
            EXECUTION_SERVICE.compareAndSet(null, new AndroidExecutionService());
            executionService = EXECUTION_SERVICE.get();
        }
        return executionService;
    }

    public static void requireInit(String message) {
        if (!INITIALIZED.get()) {
            throw new IllegalStateException(message + ".  Did you forget to call CouchbaseLite.init()?");
        }
    }

    @NonNull
    public static Context getContext() {
        requireInit("Application context not initialized");
        final SoftReference<Context> contextRef = CONTEXT.get();

        final Context ctxt = contextRef.get();
        if (ctxt == null) { throw new IllegalStateException("Context is null"); }

        return ctxt;
    }

    @NonNull
    public static String makeDbPath(@Nullable String rootDir) {
        requireInit("Can't create DB path");
        return verifyDir((rootDir != null) ? new File(rootDir) : getContext().getFilesDir());
    }

    @NonNull
    public static String makeTmpPath(@Nullable String rootDir) {
        requireInit("Can't create tmp dir path");
        final File dir = (rootDir != null)
            ? new File(rootDir, TEMP_DIR_NAME)
            : getContext().getExternalFilesDir(TEMP_DIR_NAME);
        if (dir == null) { throw new IllegalStateException("Tmp dir root is null"); }
        return verifyDir(dir);
    }

    public static void setupDirectories(@Nullable String rootDirPath) {
        requireInit("Can't set root directory");

        final String dbPath = makeDbPath(rootDirPath);
        final String tmpPath = makeTmpPath(rootDirPath);

        synchronized (TEMP_DIR_NAME) {
            if (Objects.equals(tmpPath, tmpDirPath)) { return; }

            C4Base.setTempDir(tmpPath);
            tmpDirPath = tmpPath;
            dbDirPath = dbPath;
        }
    }

    @NonNull
    public static String getDbDirectoryPath() {
        requireInit("Database directory not initialized");
        synchronized (TEMP_DIR_NAME) { return dbDirPath; }
    }

    @NonNull
    public static String getTmpDirectoryPath() {
        requireInit("Database directory not initialized");
        synchronized (TEMP_DIR_NAME) { return tmpDirPath; }
    }

    @VisibleForTesting
    public static void reset() { INITIALIZED.set(false); }

    @VisibleForTesting
    @NonNull
    public static Map<String, String> loadErrorMessages(@NonNull Context ctxt) {
        final Map<String, String> errorMessages = new HashMap<>();

        try (InputStream is = ctxt.getResources().openRawResource(R.raw.errors)) {
            final JSONObject root = new JSONObject(new Scanner(is, "UTF-8").useDelimiter("\\A").next());

            for (Iterator<String> errors = root.keys(); errors.hasNext();) {
                final String error = errors.next();
                errorMessages.put(error, root.getString(error));
            }
        }
        catch (IOException | JSONException e) {
            Log.e(LogDomain.DATABASE, "Failed to load error messages!", e);
        }

        return errorMessages;
    }

    @NonNull
    private static String verifyDir(@NonNull File dir) {
        IOException err = null;
        try {
            if ((dir.exists() && dir.isDirectory()) || dir.mkdirs()) { return dir.getCanonicalPath(); }
        }
        catch (IOException e) { err = e; }

        throw new IllegalStateException("Cannot create or access directory at " + dir, err);
    }
}
