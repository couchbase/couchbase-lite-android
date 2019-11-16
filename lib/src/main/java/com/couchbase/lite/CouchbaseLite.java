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
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONException;
import org.json.JSONObject;

import com.couchbase.lite.internal.AndroidExecutionService;
import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


public final class CouchbaseLite {
    // Utility class
    private CouchbaseLite() {}

    private static final String LITECORE_JNI_LIBRARY = "LiteCoreJNI";

    private static final AtomicReference<ExecutionService> EXECUTION_SERVICE = new AtomicReference<>();
    private static final AtomicReference<SoftReference<Context>> CONTEXT = new AtomicReference<>();

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    /**
     * Initialize CouchbaseLite library. This method MUST be called before using CouchbaseLite.
     */
    public static void init(@NonNull Context ctxt) {
        Preconditions.checkArgNotNull(ctxt, "context");

        if (INITIALIZED.getAndSet(true)) { return; }

        System.loadLibrary(LITECORE_JNI_LIBRARY);

        MValue.registerDelegate(new MValueDelegate());

        CONTEXT.set(new SoftReference<>(ctxt.getApplicationContext()));

        Log.initLogging(loadErrorMessages(ctxt));
        com.couchbase.lite.Database.log.getConsole().setLevel(LogLevel.WARNING);
    }

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

    static void requireInit(String message) {
        if (!INITIALIZED.get()) {
            throw new IllegalStateException(message + ".  Did you forget to call CouchbaseLite.init()?");
        }
    }

    @NonNull
    static Context getContext() {
        requireInit("Application context not initialized");
        final SoftReference<Context> contextRef = CONTEXT.get();

        final Context ctxt = contextRef.get();
        if (ctxt == null) { throw new IllegalStateException("Context is null"); }

        return ctxt;
    }

    static String getDbDirectoryPath() {
        requireInit("Database directory not initialized");
        return getContext().getFilesDir().getAbsolutePath();
    }

    static String getTmpDirectory(@NonNull String name) {
        requireInit("Temp directory not initialized");
        return verifyDir(getContext().getExternalFilesDir(name));
    }

    static String getTmpDirectory(String root, String name) {
        requireInit("Temp directory not initialized");
        return verifyDir(new File(root, name));
    }

    @VisibleForTesting
    static void reset() { INITIALIZED.set(false); }

    @VisibleForTesting
    @NonNull
    static Map<String, String> loadErrorMessages(@NonNull Context ctxt) {
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

    @Nullable
    private static String verifyDir(@Nullable File dir) {
        if (dir == null) { return null; }

        IOException err = null;
        try {
            if ((dir.exists() && dir.isDirectory()) || dir.mkdirs()) { return dir.getCanonicalPath(); }
        }
        catch (IOException e) { err = e; }

        throw new IllegalStateException("Cannot create or access directory at " + dir, err);
    }
}
