//
// ConsoleLogger.java
//
// Copyright (c) 2018 Couchbase, Inc All rights reserved.
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

import android.support.annotation.NonNull;
import android.util.Log;

/**
 * A class for sending log messages to Android's system log (aka logcat).  This is useful
 * for debugging during development, but is recommended to be disabled in production (the
 * file logger is both more durable and more efficient)
 */
public final class ConsoleLogger extends AbstractConsoleLogger {
    @Override
    protected void doLog(LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        switch (level) {
            case DEBUG:
                Log.d("CouchbaseLite/" + domain.toString(), message);
                break;
            case VERBOSE:
                Log.v("CouchbaseLite/" + domain.toString(), message);
                break;
            case INFO:
                Log.i("CouchbaseLite/" + domain.toString(), message);
                break;
            case WARNING:
                Log.w("CouchbaseLite/" + domain.toString(), message);
                break;
            case ERROR:
                Log.e("CouchbaseLite/" + domain.toString(), message);
                break;
        }
    }
}
