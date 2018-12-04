package com.couchbase.lite;

public interface Logger {
    LogLevel getLogLevel();

    void log(LogLevel level, LogDomain domain, String message);
}
