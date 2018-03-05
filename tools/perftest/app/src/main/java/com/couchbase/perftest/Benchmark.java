package com.couchbase.perftest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Benchmark {
    private StopWatch _st;
    private List<Double> _times;

    public Benchmark() {
        _st = new StopWatch();
        _times = new ArrayList<>();
    }

    public void start() {
        _st.reset();
    }

    // sec
    public double elapsed() {
        return _st.elapsedSec();
    }

    // sec
    public double stop() {
        double t = _st.elapsedSec();
        _times.add(t);
        return t;
    }

    public void empty() {
        _times.clear();
    }

    public void sort() {
        Collections.sort(_times);
    }

    public double median() {
        sort();
        if(_times.isEmpty()) return 0.0;
        return _times.get(_times.size() / 2);
    }

    public double average() {
        sort();
        if(_times.isEmpty()) return 0.0;
        int n = _times.size();
        double total = 0;
        for (double t : _times)
            total += t;
        return total / n;
    }

    public double stddev() {
        if(_times.isEmpty()) return 0.0;
        double avg = average();
        int n = _times.size();
        double total = 0;
        for (double t : _times)
            total += Math.pow(t - avg, 2);
        return Math.sqrt(total / n);
    }

    public double[] range() {
        sort();
        if(_times.isEmpty())
            return new double[]{0.0,0.0};
        else
            return new double[]{_times.get(0), _times.get(_times.size() - 1)};
    }

    public void printReport(String items) {
        printReport(1.0, items);
    }

    public void printReport(double scale, String items) {
        double[] r = range();
        final String[] kTimeScales = {"sec", "ms", "µs", "ns"};
        double avg = average();
        String scaleName = "";
        for (int i = 0; i < kTimeScales.length; i++) {
            scaleName = kTimeScales[i];
            if (avg * scale >= 1.0)
                break;
            scale *= 1000;
        }
        if (items != null)
            scaleName += "/" + items;
        System.err.print(String.format("Range: %7.3f ... %7.3f %s, Average: %7.3f, median: %7.3f, std dev: %5.3g\n",
                r[0] * scale, r[1] * scale, scaleName, avg * scale, median() * scale, stddev() * scale));
    }
}
