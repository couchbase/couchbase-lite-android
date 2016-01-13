/**
 * Created by Wayne Carter.
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

package com.couchbase.lite.android;

import android.os.Looper;

import com.couchbase.lite.internal.database.DatabasePlatformSupport;
import com.couchbase.lite.storage.SQLiteStorageEngineBase;
import com.couchbase.lite.util.ICUUtils;

public class AndroidSQLiteStorageEngine extends SQLiteStorageEngineBase {
    private android.content.Context context;
    private final AndroidPlatformSupport platformSupport;

    public AndroidSQLiteStorageEngine(android.content.Context context) {
        this.context = context;
        this.platformSupport = new AndroidPlatformSupport();
    }

    @Override
    protected DatabasePlatformSupport getDatabasePlatformSupport() {
        return platformSupport;
    }

    private class AndroidPlatformSupport implements DatabasePlatformSupport {
        @Override
        public boolean isMainThread() {
            return Looper.myLooper() == Looper.getMainLooper();
        }
    }

    @Override
    protected String getICUDatabasePath() {
        return ICUUtils.getICUDatabasePath(context);
    }
}
