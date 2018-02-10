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

    private static Logger logger = new Logger();

    /**
     * A map of tags and their enabled log level
     */
    private static ConcurrentHashMap<String, Integer> enabledTags;

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

    static {
        enabledTags = new ConcurrentHashMap<>();
    }

    /**
     * private constructor: not allow to instanticate.
     */
    private Log() {
    }


    /**
     * Is logging enabled for given tag / loglevel combo?
     *
     * @param tag      Used to identify the source of a log message.  It usually identifies
     *                 the class or activity where the log call occurs.
     * @param logLevel The loglevel to check whether it's enabled.  Will match this loglevel
     *                 or a more urgent loglevel.  Eg, if Log.ERROR is enabled and Log.VERBOSE
     *                 is passed as a paremeter, it will return true.
     * @return boolean indicating whether logging is enabled.
     */
    static boolean isLoggingEnabled(String tag, int logLevel) {

        // this hashmap lookup might be a little expensive, and so it might make
        // sense to convert this over to a CopyOnWriteArrayList
        Integer logLevelForTag = enabledTags.get(tag);
        return logLevel >= (logLevelForTag == null ? WARN : logLevelForTag);
    }

    /**
     * Send a VERBOSE message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void v(String tag, String msg) {
        if (logger != null && isLoggingEnabled(tag, VERBOSE)) {
            logger.v(tag, msg);
        }
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
        if (logger != null && isLoggingEnabled(tag, VERBOSE)) {
            logger.v(tag, msg, tr);
        }
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
        if (logger != null && isLoggingEnabled(tag, VERBOSE)) {
            try {
                logger.v(tag, String.format(Locale.ENGLISH, formatString, args));
            } catch (Exception e) {
                logger.v(tag, String.format(Locale.ENGLISH, "Unable to format log: %s", formatString), e);
            }
        }

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
        if (logger != null && isLoggingEnabled(tag, VERBOSE)) {
            try {
                logger.v(tag, String.format(Locale.ENGLISH, formatString, args), tr);
            } catch (Exception e) {
                logger.v(tag, String.format(Locale.ENGLISH, "Unable to format log: %s", formatString), e);
            }
        }
    }

    /**
     * Send an INFO message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void i(String tag, String msg) {
        if (logger != null && isLoggingEnabled(tag, INFO)) {
            logger.i(tag, msg);
        }
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
        if (logger != null && isLoggingEnabled(tag, INFO)) {
            logger.i(tag, msg, tr);
        }
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
        if (logger != null && isLoggingEnabled(tag, INFO)) {
            try {
                logger.i(tag, String.format(Locale.ENGLISH, formatString, args));
            } catch (Exception e) {
                logger.i(tag, String.format(Locale.ENGLISH, "Unable to format log: %s", formatString), e);
            }
        }
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
        if (logger != null && isLoggingEnabled(tag, INFO)) {
            try {
                logger.i(tag, String.format(Locale.ENGLISH, formatString, args, tr));
            } catch (Exception e) {
                logger.i(tag, String.format(Locale.ENGLISH, "Unable to format log: %s", formatString), e);
            }
        }
    }

    /**
     * Send a WARN message.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param msg The message you would like logged.
     */
    public static void w(String tag, String msg) {
        if (logger != null && isLoggingEnabled(tag, WARN)) {
            logger.w(tag, msg);
        }
    }

    /**
     * Send a WARN message and log the exception.
     *
     * @param tag Used to identify the source of a log message.  It usually identifies
     *            the class or activity where the log call occurs.
     * @param tr  An exception to log
     */
    public static void w(String tag, Throwable tr) {
        if (logger != null && isLoggingEnabled(tag, WARN)) {
            logger.w(tag, tr);
        }
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
        if (logger != null && isLoggingEnabled(tag, WARN)) {
            logger.w(tag, msg, tr);
        }
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
        if (logger != null && isLoggingEnabled(tag, WARN)) {
            try {
                logger.w(tag, String.format(Locale.ENGLISH, formatString, args));
            } catch (Exception e) {
                logger.w(tag, String.format(Locale.ENGLISH, "Unable to format log: %s", formatString), e);
            }
        }
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
        if (logger != null && isLoggingEnabled(tag, WARN)) {
            try {
                logger.w(tag, String.format(Locale.ENGLISH, formatString, args), tr);
            } catch (Exception e) {
                logger.w(tag, String.format(Locale.ENGLISH, "Unable to format log: %s", formatString), e);
            }
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
        if (logger != null && isLoggingEnabled(tag, ERROR)) {
            logger.e(tag, msg);
        }
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
        if (logger != null && isLoggingEnabled(tag, ERROR)) {
            logger.e(tag, msg, tr);
        }
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
        if (logger != null && isLoggingEnabled(tag, ERROR)) {
            try {
                logger.e(tag, String.format(Locale.ENGLISH, formatString, args), tr);
            } catch (Exception e) {
                logger.e(tag, String.format(Locale.ENGLISH, "Unable to format log: %s", formatString), e);
            }
        }
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
        if (logger != null && isLoggingEnabled(tag, ERROR)) {
            try {
                logger.e(tag, String.format(Locale.ENGLISH, formatString, args));
            } catch (Exception e) {
                logger.e(tag, String.format(Locale.ENGLISH, "Unable to format log: %s", formatString), e);
            }
        }
    }

    public static void init() {
    }

    public static void setLogLevel(Database.LogDomain domain, Database.LogLevel level) {
        switch (domain) {
            case ALL:
                enableLogging(DATABASE, level.getValue());
                enableLogging(QUERY, level.getValue());
                enableLogging(SYNC, level.getValue());
                enableLogging(C4LogDomain.BLIP, level.getValue());
                enableLogging(WEB_SOCKET, level.getValue());
                break;
            case DATABASE:
                enableLogging(DATABASE, level.getValue());
                break;

            case QUERY:
                enableLogging(QUERY, level.getValue());
                break;

            case REPLICATOR:
                enableLogging(SYNC, level.getValue());
                break;

            case NETWORK:
                enableLogging(C4LogDomain.BLIP, level.getValue());
                enableLogging(WEB_SOCKET, level.getValue());
                break;
        }
    }

    public static void enableLogging(String tag, int logLevel) {
        // CBL logging
        enabledTags.put(tag, logLevel);
        // LiteCore logging
        C4Log.setLevel(tag, logLevel);
    }
}
