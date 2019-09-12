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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.couchbase.lite.internal.AndroidExecutionService;
import com.couchbase.lite.internal.ExecutionService;
import com.couchbase.lite.internal.fleece.MValue;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.Preconditions;


public final class CouchbaseLite {
    private static final String LITECORE_JNI_LIBRARY = "LiteCoreJNI";

    private static final AtomicReference<ExecutionService> EXECUTION_SERVICE = new AtomicReference<>();
    private static final AtomicReference<SoftReference<Context>> CONTEXT = new AtomicReference<>();

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    // Utility class
    private CouchbaseLite() {}

    /**
     * Initialize CouchbaseLite library. This method MUST be called before
     * using CouchbaseLite.
     */
    public static void init(@NonNull Context ctxt) {
        Preconditions.checkArgNotNull(ctxt, "context");

        if (INITIALIZED.getAndSet(true)) { return; }

        CONTEXT.set(new SoftReference<>(ctxt.getApplicationContext()));

        loadSystemLibrary(LITECORE_JNI_LIBRARY);

        loadErrorMessages(ctxt);
    }

    /**
     * This method is for internal used only and will be removed in the future release.
     */
    public static ExecutionService getExecutionService() {
        ExecutionService executionService = EXECUTION_SERVICE.get();
        if (executionService == null) {
            EXECUTION_SERVICE.compareAndSet(null, new AndroidExecutionService());
            executionService = EXECUTION_SERVICE.get();
        }
        return executionService;
    }

    @NonNull
    static Context getContext() {
        final SoftReference<Context> contextRef = CONTEXT.get();
        if (contextRef == null) {
            throw new IllegalStateException("Null context.  Did you forget to call CouchbaseLite.init()?");
        }

        final Context ctxt = contextRef.get();
        if (ctxt == null) { throw new IllegalStateException("Context is null"); }

        return ctxt;
    }

    static String getDbDirectoryPath() {
        return getContext().getFilesDir().getAbsolutePath();
    }

    static String getTmpDirectory(@NonNull String name) {
        return getTmpDirectory(getContext().getCacheDir().getAbsolutePath(), name);
    }

    static String getTmpDirectory(String root, String name) {
        final File dir = new File(root, name);

        final String path = dir.getAbsolutePath();
        if ((dir.exists() || dir.mkdirs()) && dir.isDirectory()) { return path; }

        throw new IllegalStateException("Cannot create or access temp directory at " + path);
    }

    private static void loadSystemLibrary(String libName) {
        if (!load(libName)) { Log.e(LogDomain.DATABASE, "Cannot load native library"); }
        else { Log.v(LogDomain.DATABASE, "Successfully load native library: 'LiteCoreJNI' and 'sqlite3'"); }

        initMValue();
    }

    private static void loadErrorMessages(Context ctxt) {
        final Map<String, String> errorMessages = new HashMap<>();

        final JSONArray errors;
        try (InputStream is = ctxt.getResources().openRawResource(R.raw.errors)) {
            errors = new JSONArray(new Scanner(is).useDelimiter("\\A").next());
            for (int i = 0; i < errors.length(); i++) {
                JSONObject error = errors.getJSONObject(i);
                errorMessages.put(error.getString("name"), error.getString("message"));
            }
        }
        catch (IOException | JSONException e) {
            Log.e(LogDomain.ALL, "Failed to load error messages!", e);
        }

        CBLError.setErrorMessages(errorMessages);
    }

    // TODO: Need to update for CBL Java.
    private static boolean load(String libName) {
        try {
            System.loadLibrary(libName);
            return true;
        }
        catch (UnsatisfiedLinkError ignore) { }
        return false;
    }

    private static void initMValue() {
        try {
            final Constructor c = Class.forName("com.couchbase.lite.MValueDelegate").getDeclaredConstructor();
            c.setAccessible(true);
            MValue.registerDelegate((MValue.Delegate) c.newInstance());
        }
        catch (Exception e) {
            throw new IllegalStateException("Cannot initialize MValue delegate", e);
        }
    }
}
