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
package com.couchbase.lite.utils;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Locale;

import com.couchbase.lite.LogLevel;


/**
 * Platform console logging utility for tests
 */
public final class Report {
    private Report() {}

    public static void log(@NonNull LogLevel level, @NonNull String message) {
        Report.log(level, message, (Throwable) null);
    }

    public static void log(@NonNull LogLevel level, @NonNull String template, Object... args) {
        Report.log(level, String.format(Locale.ENGLISH, template, args));
    }

    public static void log(@NonNull LogLevel level, @NonNull String message, @Nullable Throwable err) {
        final String domain = "CouchbaseLite/Test";
        switch (level) {
            case DEBUG:
                Log.d(domain, message);
                break;
            case VERBOSE:
                Log.v(domain, message);
                break;
            case INFO:
                Log.i(domain, message);
                break;
            case WARNING:
                Log.w(domain, message);
                break;
            case ERROR:
                Log.e(domain, message, err);
                break;
        }
    }
}