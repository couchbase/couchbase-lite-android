package com.couchbase.perftest;

import java.util.Locale;

public class StopWatch {
    //private long startTime = 0; // nano seconds
    //private long stopTime = 0;

    private long _total = 0L; // nano seconds
    private long _start = 0L;
    private boolean _running = false;

    public StopWatch() {
        this(true);
    }

    public StopWatch(boolean running) {
        if (running)
            start();
    }

    public void start() {
        if (!_running) {
            _running = true;
            _start = System.nanoTime();
        }
    }

    public void stop() {
        if (_running) {
            _running = false;
            _total += (System.nanoTime() - _start);
        }
    }

    public void reset() {
        _total = 0L;
        if (_running)
            _start = System.nanoTime();
    }

    // nano sec
    public double elapsed() {
        long e = _total;
        if (_running)
            e += (System.nanoTime() - _start);
        return e;
    }

    // milli sec
    public double elapsedMS() {
        return elapsed() / 1000000.0;
    }

    // sec
    public double elapsedSec() {
        return elapsed() / 1000000000.0;
    }

    public String printReport(String what, long count, String item) {
        return String.format(Locale.ENGLISH, "%s; %d %s (took %.3f ms)", what, count, item, elapsedMS());
    }
}
