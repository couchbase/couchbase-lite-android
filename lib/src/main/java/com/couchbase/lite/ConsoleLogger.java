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


/**
 * A class for sending log messages to Android's system log (aka logcat).  This is useful
 * for debugging during development, but is recommended to be disabled in production (the
 * file logger is both more durable and more efficient)
 */
public final class ConsoleLogger implements Logger {
    private EnumSet<LogDomain> logDomains = LogDomain.ALL_DOMAINS;
    private LogLevel logLevel = LogLevel.WARNING;

    // Singleton instance accessible from Log.getConsole()
    ConsoleLogger() { }

    @Override
    public void log(@NonNull LogLevel level, @NonNull LogDomain domain, @NonNull String message) {
        if ((level.compareTo(logLevel) < 0) || (!logDomains.contains(domain))) { return; }

        final String tag = "CouchbaseLite/" + domain;
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
        if (level == null) { throw new IllegalArgumentException("level cannot be null."); }

        if (logLevel == level) { return; }

        logLevel = level;
        C4Log.setCallbackLevel(logLevel);
    }

    /**
     * Gets the domains that will be considered for writing to
     * the Android system log
     *
     * @return The currently active domains
     */
    @NonNull
    public EnumSet<LogDomain> getDomains() { return logDomains; }

    /**
     * Sets the domains that will be considered for writing to
     * the Android system log
     *
     * @param domains The domains to make active
     */
    public void setDomains(@NonNull EnumSet<LogDomain> domains) {
        if (domains == null) { throw new IllegalArgumentException("domains cannot be null."); }

        logDomains = (!domains.contains(LogDomain.ALL))
            ? domains
            : LogDomain.ALL_DOMAINS;
    }
}
