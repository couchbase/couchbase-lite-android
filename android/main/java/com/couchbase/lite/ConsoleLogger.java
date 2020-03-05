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
 * A class that sends log messages to Android's system log, available via 'logcat'.
 */
public final class ConsoleLogger extends AbstractConsoleLogger {
    @Override
    protected void doLog(LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        final String tag = "CouchbaseLite/" + domain.toString();
        switch (level) {
            case DEBUG:
                Log.d(tag, message);
                break;
            case VERBOSE:
                Log.v(tag, message);
                break;
            case INFO:
                Log.i(tag, message);
                break;
            case WARNING:
                Log.w(tag, message);
                break;
            case ERROR:
                Log.e(tag, message);
                break;
        }
    }
}
