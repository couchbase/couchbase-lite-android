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

import java.util.EnumSet;

import com.couchbase.lite.internal.core.C4Log;
import com.couchbase.lite.internal.utils.Preconditions;


/**
 * A class for sending log messages to Android's system log (aka logcat).  This is useful
 * for debugging during development, but is recommended to be disabled in production (the
 * file logger is both more durable and more efficient)
 */
public final class ConsoleLogger implements Logger {
    private LogLevel logLevel = LogLevel.WARNING;
    private EnumSet<LogDomain> logDomains = EnumSet.of(LogDomain.ALL);

    // Singleton instance accessible from Log.getConsole()
    ConsoleLogger() { }

    /**
     * Gets the domains that will be considered for writing to
     * the Android system log
     *
     * @return The currently active domains
     */
    @NonNull
    public EnumSet<LogDomain> getDomains() {
        return logDomains;
    }

    /**
     * Sets the domains that will be considered for writing to
     * the Android system log
     *
     * @param domains The domains to make active
     */
    public void setDomains(@NonNull EnumSet<LogDomain> domains) {
        Preconditions.checkArgNotNull(domains, "domains");
        logDomains = domains;
    }

    private void setCallbackLevel(@NonNull LogLevel level) {
        Preconditions.checkArgNotNull(level, "level");

        LogLevel callbackLevel = level;
        final Logger custom = Database.log.getCustom();
        if ((custom != null) && (custom.getLevel().compareTo(callbackLevel) < 0)) {
            callbackLevel = custom.getLevel();
        }

        C4Log.setCallbackLevel(callbackLevel.getValue());
    }

    @NonNull
    @Override
    public LogLevel getLevel() { return logLevel; }

    /**
     * Sets the overall logging level that will be written to
     * the Android system log
     *
     * @param level The maximum level to include in the logs
     */
    public void setLevel(@NonNull LogLevel level) {
        Preconditions.checkArgNotNull(level, "level");

        if (logLevel == level) { return; }

        logLevel = level;
        setCallbackLevel(level);
    }

    @Override
    public void log(LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        if (level.compareTo(logLevel) < 0
            || (!logDomains.contains(domain)
            && !logDomains.contains(LogDomain.ALL))) {
            return;
        }

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
