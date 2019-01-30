//
// StopWatch.java
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
package com.couchbase.litecore.utils;

import java.util.Locale;

public class StopWatch {
    private long startTime = 0; // nano seconds
    private long stopTime = 0;
    private boolean running = false;

    public StopWatch() {
        start();
    }

    public void start() {
        this.startTime = System.nanoTime();
        this.running = true;
    }

    public void stop() {
        this.stopTime = System.nanoTime();
        this.running = false;
    }

    public long getElapsedTime() {
        long elapsed;
        if (running) {
            elapsed = (System.nanoTime() - startTime);
        } else {
            elapsed = (stopTime - startTime);
        }
        return elapsed;
    }

    public double getElapsedTimeMillis() {
        double elapsed;
        if (running) {
            elapsed = ((double) (System.nanoTime() - startTime) / 1000000.0);
        } else {
            elapsed = ((double) (stopTime - startTime) / 1000000.0);
        }
        return elapsed;
    }

    public double getElapsedTimeSecs() {
        double elapsed;
        if (running) {
            elapsed = ((double) (System.nanoTime() - startTime) / 1000000000.0);
        } else {
            elapsed = ((double) (stopTime - startTime) / 1000000000.0);
        }
        return elapsed;
    }

    public String toString(String what, long count, String item) {
        return String.format(Locale.ENGLISH, "%s; %d %s (took %.3f ms)", what, count, item, getElapsedTimeMillis());
    }
}
