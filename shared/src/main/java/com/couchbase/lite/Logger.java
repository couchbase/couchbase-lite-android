package com.couchbase.lite;

public interface Logger {
    LogLevel getLevel();

    void log(LogLevel level, LogDomain domain, String message);
}
