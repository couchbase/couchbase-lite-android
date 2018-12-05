package com.couchbase.lite;

import com.couchbase.litecore.C4Log;

public final class Log {
    private final ConsoleLogger _consoleLogger = new ConsoleLogger();
    private final FileLogger _fileLogger = new FileLogger();
    private Logger _customLogger;

    Log() {
        C4Log.setCallbackLevel(LogLevel.WARNING.getValue());
    }

    public ConsoleLogger getConsole() {
        return _consoleLogger;
    }

    public FileLogger getFile() {
        return _fileLogger;
    }

    public Logger getCustom() {
        return _customLogger;
    }

    public void setCustom(Logger customLogger) {
        _customLogger = customLogger;
    }
}
