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

import com.couchbase.lite.internal.core.C4Log;

import java.util.EnumSet;

/**
 * A class for sending log messages to Android's system log (aka logcat).  This is useful
 * for debugging during development, but is recommended to be disabled in production (the
 * file logger is both more durable and more efficient)
 */
public final class ConsoleLogger implements Logger {
    private LogLevel _level = LogLevel.WARNING;
    private EnumSet<LogDomain> _domains = EnumSet.of(LogDomain.ALL);

    //---------------------------------------------
    // Constructor should not be exposed (singleton)
    //---------------------------------------------
    ConsoleLogger() { }

    /**
     * Gets the domains that will be considered for writing to
     * the Android system log
     *
     * @return The currently active domains
     */
    @NonNull
    public EnumSet<LogDomain> getDomains() {
        return _domains;
    }

    /**
     * Sets the domains that will be considered for writing to
     * the Android system log
     *
     * @param domains The domains to make active
     */
    public void setDomains(@NonNull EnumSet<LogDomain> domains) {
        if(domains == null)
            throw new IllegalArgumentException("domains cannot be null.");

        _domains = domains;
    }

    /**
     * Sets the overall logging level that will be written to
     * the Android system log
     *
     * @param level The maximum level to include in the logs
     */
    public void setLevel(@NonNull LogLevel level) {
        if(level == null)
            throw new IllegalArgumentException("level cannot be null.");

        if(_level == level)
            return;

        _level = level;
        setCallbackLevel(level);
    }

    private void setCallbackLevel(@NonNull LogLevel level) {
        if(level == null)
            throw new IllegalArgumentException("level cannot be null.");

        LogLevel callbackLevel = level;
        Logger custom = Database.log.getCustom();
        if(custom != null) {
            if(custom.getLevel().compareTo(callbackLevel) < 0) {
                callbackLevel = custom.getLevel();
            }
        }
        C4Log.setCallbackLevel(callbackLevel.getValue());
    }

    @NonNull
    @Override
    public LogLevel getLevel() {
        return _level;
    }

    @Override
    public void log(LogLevel level, LogDomain domain, String message) {
        if(level.compareTo(_level) < 0
                || (!_domains.contains(domain)
                && !_domains.contains(LogDomain.ALL))) {
            return;
        }

        switch(level) {
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
