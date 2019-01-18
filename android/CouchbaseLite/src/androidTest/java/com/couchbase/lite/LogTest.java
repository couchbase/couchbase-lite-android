package com.couchbase.lite;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import com.couchbase.lite.internal.support.Log;

import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class LogTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testCustomLoggingLevels() {
        LogTestLogger customLogger = new LogTestLogger();
        Log.i("IGNORE", "IGNORE");
        Database.getLog().setCustom(customLogger);

        for(int i = 5; i >= 1; i--) {
            customLogger.getLines().clear();
            customLogger.setLevel(LogLevel.values()[i]);
            Log.v(LogDomain.DATABASE.toString(), "TEST VERBOSE");
            Log.i(LogDomain.DATABASE.toString(), "TEST INFO");
            Log.w(LogDomain.DATABASE.toString(), "TEST WARNING");
            Log.e(LogDomain.DATABASE.toString(), "TEST ERROR");
            assertEquals(5 - i, customLogger.getLines().size());
        }
    }

    @Test
    public void testPlaintextLoggingLevels() throws IOException {
        final File path = new File(context.getCacheDir().getAbsolutePath(), "testPlaintextLoggingLevels");
        final String logDirectory = emptyDirectory(path.getAbsolutePath());
        LogFileConfiguration config = new LogFileConfiguration(logDirectory)
                .setUsePlaintext(true)
                .setMaxRotateCount(0);

        testWithConfiguration(LogLevel.INFO, config, new Runnable() {
            @Override
            public void run() {
                LogLevel levels[] = { LogLevel.NONE, LogLevel.ERROR, LogLevel.WARNING, LogLevel.INFO, LogLevel.VERBOSE };
                for (LogLevel level : levels) {
                    Database.getLog().getFile().setLevel(level);
                    Log.v("DATABASE", "TEST VERBOSE");
                    Log.i("DATABASE", "TEST INFO");
                    Log.w("DATABASE", "TEST WARNING");
                    Log.e("DATABASE", "TEST ERROR");
                }

                try {
                    for (File log : path.listFiles()) {
                        BufferedReader fin = new BufferedReader(new FileReader(log));
                        int lineCount = 0;
                        while (fin.readLine() != null) {
                            lineCount++;
                        }

                        // One meta line per log, so the actual logging lines is X - 1
                        if (log.getAbsolutePath().contains("verbose")) {
                            assertEquals(2, lineCount);
                        } else if (log.getAbsolutePath().contains("info")) {
                            assertEquals(3, lineCount);
                        } else if (log.getAbsolutePath().contains("warning")) {
                            assertEquals(4, lineCount);
                        } else if (log.getAbsolutePath().contains("error")) {
                            assertEquals(5, lineCount);
                        }
                    }
                } catch(Exception e) {
                    fail("Exception during test callback " + e.toString());
                }
            }
        });


    }

    @Test
    public void testEnableAndDisableCustomLogging() {
        LogTestLogger customLogger = new LogTestLogger();
        Log.i("IGNORE", "IGNORE");
        Database.getLog().setCustom(customLogger);

        customLogger.setLevel(LogLevel.NONE);
        Log.v(LogDomain.DATABASE.toString(), "TEST VERBOSE");
        Log.i(LogDomain.DATABASE.toString(), "TEST INFO");
        Log.w(LogDomain.DATABASE.toString(), "TEST WARNING");
        Log.e(LogDomain.DATABASE.toString(), "TEST ERROR");
        assertEquals(0, customLogger.getLines().size());

        customLogger.setLevel(LogLevel.VERBOSE);
        Log.v(LogDomain.DATABASE.toString(), "TEST VERBOSE");
        Log.i(LogDomain.DATABASE.toString(), "TEST INFO");
        Log.w(LogDomain.DATABASE.toString(), "TEST WARNING");
        Log.e(LogDomain.DATABASE.toString(), "TEST ERROR");
        assertEquals(4, customLogger.getLines().size());
    }

    private void testWithConfiguration(LogLevel level, LogFileConfiguration config, Runnable r) {
        LogFileConfiguration old = Database.getLog().getFile().getConfig();
        Database.getLog().getFile().setConfig(config);
        Database.getLog().getFile().setLevel(level);
        try {
            r.run();
        } finally {
            Database.getLog().getFile().setLevel(LogLevel.INFO);
            Database.getLog().getFile().setConfig(old);
        }
    }

    private static String emptyDirectory(String path){
        File f = new File(path);
        if(f.exists()) {
            for (File log : f.listFiles()) {
                log.delete();
            }
        }

        return path;
    }
}

class LogTestLogger implements Logger {
    private LogLevel _level;
    private ArrayList<String> _lines = new ArrayList<>();

    public ArrayList<String> getLines() {
        return _lines;
    }

    public void setLevel(LogLevel level) {
        _level = level;
    }

    @Override
    public LogLevel getLevel() {
        return _level;
    }

    @Override
    public void log(LogLevel level, LogDomain domain, String message) {
        if(level.compareTo(_level) < 0) {
            return;
        }

        _lines.add(message);
    }
}
