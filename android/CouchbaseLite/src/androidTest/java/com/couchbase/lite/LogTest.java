package com.couchbase.lite;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.utils.FileUtils;

public class LogTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getTargetContext();
    }

    @After
    public void tearDown() {
        Database.getLog().getFile().setUsePlaintext(false);
        Database.getLog().getFile().setDirectory(new File(context.getFilesDir().getAbsolutePath(), "Logs").getAbsolutePath());
    }

    @Test
    public void testCustomLoggingLevels() {
        // NOTE: The implicit vs explicit file logger modes are tested elsewhere
        File logPath = new File(context.getCacheDir().getAbsolutePath(), "Logs");
        logPath.deleteOnExit();
        Database.getLog().getFile().setDirectory(context.getCacheDir().getAbsolutePath());

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
    public void testImplicitFileLogging() throws IOException {
        Database.getLog().getFile().setUsePlaintext(true);
        File logPath = new File(context.getFilesDir().getAbsolutePath(), "Logs");
        FileUtils.deleteRecursive(logPath);

        // Simulate creating a new database
        DatabaseConfiguration config = new DatabaseConfiguration(context);

        Log.i("DATABASE", "TEST MESSAGE");
        File[] logFiles = logPath.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.contains("info");
            }
        });

        assertEquals(1, logFiles.length);
        BufferedReader fin = new BufferedReader(new FileReader(logFiles[0]));
        fin.readLine(); // skip
        assertTrue(fin.readLine().contains("TEST MESSAGE"));
    }

    @Test
    public void testExplicitFileLogging() throws IOException {
        Database.getLog().getFile().setUsePlaintext(true);
        File logPath = new File(context.getCacheDir().getAbsolutePath(), "Logs");
        FileUtils.deleteRecursive(logPath);

        Database.getLog().getFile().setDirectory(logPath.getAbsolutePath());

        Log.i("DATABASE", "TEST MESSAGE");
        File[] logFiles = logPath.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return s.contains("info");
            }
        });

        assertEquals(1, logFiles.length);
        BufferedReader fin = new BufferedReader(new FileReader(logFiles[0]));
        fin.readLine(); // skip
        assertTrue(fin.readLine().contains("TEST MESSAGE"));
    }

    @Test
    public void testPlaintextLoggingLevels() throws IOException {
        Database.getLog().getFile().setUsePlaintext(true);
        File logPath = new File(context.getCacheDir().getAbsolutePath(), "Logs");
        FileUtils.deleteRecursive(logPath);

        Database.getLog().getFile().setDirectory(logPath.getAbsolutePath());

        LogLevel levels[] = { LogLevel.NONE, LogLevel.ERROR, LogLevel.WARNING, LogLevel.INFO, LogLevel.VERBOSE };
        for (LogLevel level : levels) {
            Database.getLog().getFile().setLevel(level);
            Log.v("DATABASE", "TEST VERBOSE");
            Log.i("DATABASE", "TEST INFO");
            Log.w("DATABASE", "TEST WARNING");
            Log.e("DATABASE", "TEST ERROR");
        }

        for(File log : logPath.listFiles()) {
            BufferedReader fin = new BufferedReader(new FileReader(log));
            int lineCount = 0;
            while(fin.readLine() != null) {
                lineCount++;
            }

            // One meta line per log, so the actual logging lines is X - 1
            if(log.getAbsolutePath().contains("verbose")) {
                assertEquals(2, lineCount);
            } else if(log.getAbsolutePath().contains("info")) {
                assertEquals(3, lineCount);
            } else if(log.getAbsolutePath().contains("warning")) {
                assertEquals(4, lineCount);
            } else if(log.getAbsolutePath().contains("error")) {
                assertEquals(5, lineCount);
            }
        }
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
