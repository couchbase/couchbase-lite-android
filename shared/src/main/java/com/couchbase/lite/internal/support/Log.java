//
// Log.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
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

import com.couchbase.lite.Database;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.Logger;
import com.couchbase.litecore.C4Constants.C4LogDomain;
import com.couchbase.litecore.C4Log;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import static com.couchbase.litecore.C4Constants.C4LogLevel.kC4LogDebug;
import static com.couchbase.litecore.C4Constants.C4LogLevel.kC4LogError;
import static com.couchbase.litecore.C4Constants.C4LogLevel.kC4LogInfo;
import static com.couchbase.litecore.C4Constants.C4LogLevel.kC4LogNone;
import static com.couchbase.litecore.C4Constants.C4LogLevel.kC4LogVerbose;
import static com.couchbase.litecore.C4Constants.C4LogLevel.kC4LogWarning;

/**
 * Couchbase Lite Logging API.
 */
public final class Log {

    /**
     * Logging Tag for Database related operations
     */
    public static final String DATABASE = C4LogDomain.Database;

    /**
     * Logging Tag for Query related operations
     */
    public static final String QUERY = C4LogDomain.Query;

    /**
     * Logging Tag for Sync related operations
     */
    public static final String SYNC = C4LogDomain.Sync;

    /**
     * Logging Tag for Sync related operations
     */
    public static final String WEB_SOCKET = C4LogDomain.WebSocket;

    /**
     * Priority constant for the println method; use Log.d.
     */
    public static final int DEBUG = kC4LogDebug; // 2
    /**
     * Priority constant for the println method; use Log.v.
     */
    public static final int VERBOSE = kC4LogVerbose; // 3
    /**
     * Priority constant for the println method; use Log.i.
     */
    public static final int INFO = kC4LogInfo; // 3
    /**
     * Priority constant for the println method; use Log.w.
     */
    public static final int WARN = kC4LogWarning; // 3
    /**
     * Priority constant for the println method; use Log.e.
     */
    public static final int ERROR = kC4LogError; // 4

    public static final int NONE = kC4LogNone; // 5

    /**
     * private constructor: not allow to instanticate.
     */
    private Log() {
    }


    /**
     * Send a VERBOSE message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void v(String tag, String msg) {
        sendToLoggers(LogLevel.VERBOSE, tag, msg);
    }

    /**
     * Send a VERBOSE message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr  An exception to log
     */
    public static void v(String tag, String msg, Throwable tr) {
        v(tag, "Exception: %s", tr.toString());
    }

    /**
     * Send a VERBOSE message.
     *
     * @param tag          Used to identify the source of a log message.  It usually identifies
     *                     the class or activity where the log call occurs.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void v(String tag, String formatString, Object... args) {
        String msg;
        try {
            msg = String.format(Locale.ENGLISH, formatString, args);
        } catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.VERBOSE, tag, msg);
    }

    /**
     * Send a VERBOSE message and log the exception.
     *
     * @param tag          Used to identify the source of a log message.  It usually identifies
     *                     the class or activity where the log call occurs.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void v(String tag, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        } catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.VERBOSE, tag, msg);
    }

    /**
     * Send an INFO message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void i(String tag, String msg) {
        sendToLoggers(LogLevel.INFO, tag, msg);
    }

    /**
     * Send a INFO message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr  An exception to log
     */
    public static void i(String tag, String msg, Throwable tr) {
        i(tag, "Exception: %s", tr.toString());
    }

    public static void info(String tag, String msg) {
        i(tag, msg);
    }

    public static void info(String tag, String msg, Throwable tr) {
        i(tag, msg, tr);
    }

    /**
     * Send an INFO message.
     *
     * @param tag          Used to identify the source of a log message.  It usually identifies
     *                     the class or activity where the log call occurs.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void i(String tag, String formatString, Object... args) {
        String msg;
        try {
            msg = String.format(Locale.ENGLISH, formatString, args);
        } catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.INFO, tag, msg);
    }

    /**
     * Send a INFO message and log the exception.
     *
     * @param tag          Used to identify the source of a log message.  It usually identifies
     *                     the class or activity where the log call occurs.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void i(String tag, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        } catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.INFO, tag, msg);
    }

    /**
     * Send a WARN message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void w(String tag, String msg) {
        sendToLoggers(LogLevel.WARNING, tag, msg);
    }

    /**
     * Send a WARN message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param tr  An exception to log
     */
    public static void w(String tag, Throwable tr) {
        w(tag, "Exception: %s", tr.toString());
    }

    /**
     * Send a WARN message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr  An exception to log
     */
    public static void w(String tag, String msg, Throwable tr) {
        w(tag, "%s: %s", msg, tr.toString());
    }

    /**
     * Send a WARN message.
     *
     * @param tag          Used to identify the source of a log message.  It usually identifies
     *                     the class or activity where the log call occurs.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void w(String tag, String formatString, Object... args) {
        String msg;
        try {
            msg = String.format(Locale.ENGLISH, formatString, args);
        } catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.WARNING, tag, msg);
    }

    /**
     * Send a WARN message and log the exception.
     *
     * @param tag          Used to identify the source of a log message.  It usually identifies
     *                     the class or activity where the log call occurs.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void w(String tag, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        } catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
    }

    /**
     * Send an ERROR message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void e(String tag, String msg) {
        sendToLoggers(LogLevel.ERROR, tag, msg);
    }

    /**
     * Send a ERROR message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr  An exception to log
     */
    public static void e(String tag, String msg, Throwable tr) {
        e(tag, "%s: %s", msg, tr.toString());
    }

    /**
     * Send a ERROR message and log the exception.
     *
     * @param tag          Used to identify the source of a log message.  It usually identifies
     *                     the class or activity where the log call occurs.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void e(String tag, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        } catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.ERROR, tag, msg);
    }

    /**
     * Send a ERROR message.
     *
     * @param tag          Used to identify the source of a log message.  It usually identifies
     *                     the class or activity where the log call occurs.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void e(String tag, String formatString, Object... args) {
        String msg;
        try {
            msg = String.format(Locale.ENGLISH, formatString, args);
        } catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.ERROR, tag, msg);
    }

    /**
     * Send a DEBUG message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void d(String tag, String msg) {
        sendToLoggers(LogLevel.DEBUG, tag, msg);
    }

    /**
     * Send a DEBUG message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     * @param tr  An exception to log
     */
    public static void d(String tag, String msg, Throwable tr) {
        d(tag, "Exception: %s", tr.toString());
    }

    /**
     * Send a DEBUG message.
     *
     * @param tag          Used to identify the source of a log message.  It usually identifies
     *                     the class or activity where the log call occurs.
     * @param formatString The string you would like logged plus format specifiers.
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void d(String tag, String formatString, Object... args) {
        String msg;
        try {
            msg = String.format(Locale.ENGLISH, formatString, args);
        } catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.DEBUG, tag, msg);
    }

    /**
     * Send a DEBUG message and log the exception.
     *
     * @param tag          Used to identify the source of a log message.  It usually identifies
     *                     the class or activity where the log call occurs.
     * @param formatString The string you would like logged plus format specifiers.
     * @param tr           An exception to log
     * @param args         Variable number of Object args to be used as params to formatString.
     */
    public static void d(String tag, String formatString, Throwable tr, Object... args) {
        String msg;
        try {
            msg = String.format(formatString, args);
            msg = String.format("%s (%s)", msg, tr.toString());
        } catch (Exception e) {
            msg = String.format(Locale.ENGLISH, "Unable to format log: %s (%s)", formatString, e.toString());
        }
        sendToLoggers(LogLevel.DEBUG, tag, msg);
    }

    public static void setLogLevel(LogDomain domain, LogLevel level) {
        int actualLevel = level.equals(LogLevel.NONE) ? Log.NONE : Log.DEBUG;
        switch (domain) {
            case ALL:
                enableLogging(DATABASE, actualLevel);
                enableLogging(QUERY, actualLevel);
                enableLogging(SYNC, actualLevel);
                enableLogging(WEB_SOCKET, actualLevel);
                enableLogging(C4LogDomain.BLIP, actualLevel);
                enableLogging(C4LogDomain.SyncBusy, actualLevel);
                break;
            case DATABASE:
                enableLogging(DATABASE, actualLevel);
                break;

            case QUERY:
                enableLogging(QUERY, actualLevel);
                break;

            case REPLICATOR:
                enableLogging(SYNC, actualLevel);
                enableLogging(C4LogDomain.SyncBusy, actualLevel);
                break;

            case NETWORK:
                enableLogging(C4LogDomain.BLIP, actualLevel);
                enableLogging(WEB_SOCKET, actualLevel);
                break;
        }
    }

    public static void enableLogging(String tag, int logLevel) {
        // LiteCore logging
        C4Log.setLevel(tag, logLevel);
    }

    private static void sendToLoggers(LogLevel level, String tag, String msg) {
        boolean fileSucceeded = false;
        boolean consoleSucceeded = false;
        try {
            LogDomain domain = LogDomain.valueOf(tag);
            Database.log.getFile().log(level, domain, msg);
            fileSucceeded = true;
            Database.log.getConsole().log(level, domain, msg);
            consoleSucceeded = true;
            Logger custom = Database.log.getCustom();
            if(custom != null) {
                custom.log(level, domain, msg);
            }
        } catch(Exception e) {
            if(fileSucceeded) {
                Database.log.getFile().log(LogLevel.ERROR, LogDomain.DATABASE, e.toString());
            }

            if(consoleSucceeded) {
                Database.log.getConsole().log(LogLevel.ERROR, LogDomain.DATABASE, e.toString());
            }
        }
    }
}
