package com.couchbase.lite;

public final class Log {
    private final ConsoleLogger _consoleLogger = new ConsoleLogger();
    private final FileLogger _fileLogger = new FileLogger();
    private Logger _customLogger;

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
