/**
 * Copyright (c) 2015 Couchbase, Inc. All rights reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */


package com.couchbase.lite.android;

import android.os.Build;

import com.couchbase.lite.Context;
import com.couchbase.lite.NetworkReachabilityManager;
import com.couchbase.lite.storage.SQLiteStorageEngineFactory;
import com.couchbase.lite.support.Version;

import java.io.File;

public class AndroidContext implements Context {
    private android.content.Context wrappedContext;
    private NetworkReachabilityManager networkReachabilityManager;

    public AndroidContext(android.content.Context wrappedContext) {
        this.wrappedContext = wrappedContext;
    }

    public android.content.Context getWrappedContext() {
        return wrappedContext;
    }

    @Override
    public File getFilesDir() {
        return wrappedContext.getFilesDir();
    }

    @Override
    public File getTempDir() {
        return wrappedContext.getCacheDir();
    }

    @Override
    public void setNetworkReachabilityManager(NetworkReachabilityManager reachabilityManager) {
        this.networkReachabilityManager = reachabilityManager;
    }

    @Override
    public NetworkReachabilityManager getNetworkReachabilityManager() {
        if (networkReachabilityManager == null) {
            networkReachabilityManager = new AndroidNetworkReachabilityManager(this);
        }
        return networkReachabilityManager;
    }

    @Override
    public SQLiteStorageEngineFactory getSQLiteStorageEngineFactory() {
        return new AndroidSQLiteStorageEngineFactory(wrappedContext);
    }

    @Override
    public String getUserAgent() {
        return String.format("CouchbaseLite/%s (Android %s/%s %s/%s)",
                Version.SYNC_PROTOCOL_VERSION,
                Build.VERSION.RELEASE,
                Build.CPU_ABI,
                Version.getVersionName(),
                Version.getCommitHash());
    }
}
