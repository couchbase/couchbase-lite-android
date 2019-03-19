//
// Log.java
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
package com.couchbase.lite.internal.support;

import java.util.Locale;

import com.couchbase.lite.Database;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.Logger;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Log;


/**
 * Couchbase Lite Internal Log Utility.
 */
public final class Log {
    public static final int C4LOG_DEBUG = C4Constants.C4LogLevel.kC4LogDebug;
    public static final int C4LOG_VERBOSE = C4Constants.C4LogLevel.kC4LogVerbose;
    public static final int C4LOG_INFO = C4Constants.C4LogLevel.kC4LogInfo;
    public static final int C4LOG_WARN = C4Constants.C4LogLevel.kC4LogWarning;
    public static final int C4LOG_ERROR = C4Constants.C4LogLevel.kC4LogError;
    public static final int C4LOG_NONE = C4Constants.C4LogLevel.kC4LogNone;
    private static final String DATABASE = C4Constants.C4LogDomain.Database;
    private static final String QUERY = C4Constants.C4LogDomain.Query;
    private static final String SYNC = C4Constants.C4LogDomain.Sync;
    private static final String WEB_SOCKET = C4Constants.C4LogDomain.WebSocket;

    /**
     * Send a VERBOSE message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void v(LogDomain domain, String msg) {
        sendToLoggers(LogLevel.VERBOSE, domain, msg);
    }

    /**
     * Send a VERBOSE message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param tr     An exception to log
     */
    public static void v(LogDomain domain, String msg, Throwable tr) {
        v(domain, "Exception: %s", tr.toString());
    }

    /**
     * Send a VERBOSE message.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void v(LogDomain domain, String formatString, Object... args) {
        String msg;
        try {
            msg = String.format(Locale.ENGLISH, formatString, args);
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.VERBOSE, domain, msg);
    }

    /**
     * Send a VERBOSE message and log the exception.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void v(LogDomain domain, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.VERBOSE, domain, msg);
    }

    /**
     * Send an INFO message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void i(LogDomain domain, String msg) {
        sendToLoggers(LogLevel.INFO, domain, msg);
    }

    /**
     * Send a INFO message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param tr     An exception to log
     */
    public static void i(LogDomain domain, String msg, Throwable tr) {
        i(domain, "Exception: %s", tr.toString());
    }

    public static void info(LogDomain domain, String msg) {
        i(domain, msg);
    }

    public static void info(LogDomain domain, String msg, Throwable tr) {
        i(domain, msg, tr);
    }

    /**
     * Send an INFO message.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void i(LogDomain domain, String formatString, Object... args) {
        String msg;
        try {
            msg = String.format(Locale.ENGLISH, formatString, args);
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.INFO, domain, msg);
    }

    /**
     * Send a INFO message and log the exception.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void i(LogDomain domain, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.INFO, domain, msg);
    }

    /**
     * Send a WARN message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void w(LogDomain domain, String msg) {
        sendToLoggers(LogLevel.WARNING, domain, msg);
    }

    /**
     * Send a WARN message and log the exception.
     *
     * @param domain The log domain.
     * @param tr     An exception to log
     */
    public static void w(LogDomain domain, Throwable tr) {
        w(domain, "Exception: %s", tr.toString());
    }

    /**
     * Send a WARN message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param tr     An exception to log
     */
    public static void w(LogDomain domain, String msg, Throwable tr) {
        w(domain, "%s: %s", msg, tr.toString());
    }

    /**
     * Send a WARN message.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void w(LogDomain domain, String formatString, Object... args) {
        String msg;
        try {
            msg = String.format(Locale.ENGLISH, formatString, args);
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.WARNING, domain, msg);
    }

    /**
     * Send a WARN message and log the exception.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void w(LogDomain domain, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.WARNING, domain, msg);
    }

    /**
     * Send an ERROR message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void e(LogDomain domain, String msg) {
        sendToLoggers(LogLevel.ERROR, domain, msg);
    }

    /**
     * Send a ERROR message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param tr     An exception to log
     */
    public static void e(LogDomain domain, String msg, Throwable tr) {
        e(domain, "%s: %s", msg, tr.toString());
    }

    /**
     * Send a ERROR message and log the exception.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void e(LogDomain domain, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.ERROR, domain, msg);
    }

    /**
     * Send a ERROR message.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void e(LogDomain domain, String formatString, Object... args) {
        String msg;
        try {
            msg = String.format(Locale.ENGLISH, formatString, args);
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.ERROR, domain, msg);
    }

    /**
     * Send a DEBUG message.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     */
    public static void d(LogDomain domain, String msg) {
        sendToLoggers(LogLevel.DEBUG, domain, msg);
    }

    /**
     * Send a DEBUG message and log the exception.
     *
     * @param domain The log domain.
     * @param msg    The message you would like logged.
     * @param tr     An exception to log
     */
    public static void d(LogDomain domain, String msg, Throwable tr) {
        d(domain, "Exception: %s", tr.toString());
    }

    /**
     * Send a DEBUG message.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void d(LogDomain domain, String formatString, Object... args) {
        String msg;
        try {
            msg = String.format(Locale.ENGLISH, formatString, args);
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.DEBUG, domain, msg);
    }

    /**
     * Send a DEBUG message and log the exception.
     *
     * @param domain       The log domain.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void d(LogDomain domain, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        }
        catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.DEBUG, domain, msg);
    }

    public static void setLogLevel(LogDomain domain, LogLevel level) {
        final int actualLevel = level.equals(LogLevel.NONE) ? C4LOG_NONE : C4LOG_DEBUG;
        switch (domain) {
            case ALL:
                enableLogging(DATABASE, actualLevel);
                enableLogging(QUERY, actualLevel);
                enableLogging(SYNC, actualLevel);
                enableLogging(WEB_SOCKET, actualLevel);
                enableLogging(C4Constants.C4LogDomain.BLIP, actualLevel);
                enableLogging(C4Constants.C4LogDomain.SyncBusy, actualLevel);
                break;
            case DATABASE:
                enableLogging(DATABASE, actualLevel);
                break;

            case QUERY:
                enableLogging(QUERY, actualLevel);
                break;

            case REPLICATOR:
                enableLogging(SYNC, actualLevel);
                enableLogging(C4Constants.C4LogDomain.SyncBusy, actualLevel);
                break;

            case NETWORK:
                enableLogging(C4Constants.C4LogDomain.BLIP, actualLevel);
                enableLogging(WEB_SOCKET, actualLevel);
                break;
        }
    }

    public static void enableLogging(String tag, int logLevel) {
        // LiteCore logging
        C4Log.setLevel(tag, logLevel);
    }

    private static void sendToLoggers(LogLevel level, LogDomain domain, String msg) {
        boolean fileSucceeded = false;
        boolean consoleSucceeded = false;
        try {
            // File logging:
            Database.log.getFile().log(level, domain, msg);
            fileSucceeded = true;

            // Console logging:
            Database.log.getConsole().log(level, domain, msg);
            consoleSucceeded = true;

            // Custom logging:
            final Logger custom = Database.log.getCustom();
            if (custom != null) {
                custom.log(level, domain, msg);
            }
        }
        catch (Exception e) {
            if (fileSucceeded) {
                Database.log.getFile().log(LogLevel.ERROR, LogDomain.DATABASE, e.toString());
            }

            if (consoleSucceeded) {
                Database.log.getConsole().log(LogLevel.ERROR, LogDomain.DATABASE, e.toString());
            }
        }
    }

    /**
     * private constructor.
     */
    private Log() { }
}
