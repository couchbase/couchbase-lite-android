package com.couchbase.lite;

import android.util.Log;

import java.util.ArrayList;

public final class ConsoleLogger implements Logger {
    private LogLevel _level;
    private ArrayList<LogDomain> _domains;

    public ArrayList<LogDomain> getDomains() {
        return _domains;
    }

    public void setDomains(ArrayList<LogDomain> domains) {
        _domains = domains;
    }

    public void setLevel(LogLevel level) {
        _level = level;
    }

    @Override
    public LogLevel getLogLevel() {
        return _level;
    }

    @Override
    public void log(LogLevel level, LogDomain domain, String message) {
        if(level.compareTo(_level) < 0 || !_domains.contains(domain)) {
            return;
        }

        switch(level) {
            case DEBUG:
                Log.d("CouchbaseLite", message);
                break;
            case VERBOSE:
                Log.v("CouchbaseLite", message);
                break;
            case INFO:
                Log.i("CouchbaseLite", message);
                break;
            case WARNING:
                Log.w("CouchbaseLite", message);
                break;
            case ERROR:
                Log.e("CouchbaseLite", message);
                break;
        }
    }
}
