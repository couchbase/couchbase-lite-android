package com.couchbase.lite;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import com.couchbase.lite.internal.support.Log;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
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
        Database.log.setCustom(customLogger);

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
                    Database.log.getFile().setLevel(level);
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
        Database.log.setCustom(customLogger);

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

    @Test @Ignore
    public void testFileLoggingMaxSize() {
        final File path = new File(
                context.getCacheDir().getAbsolutePath(),
                "testFileLoggingMaxSize"
        );
        final String logDirectory = emptyDirectory(path.getAbsolutePath());
        final LogFileConfiguration config = new LogFileConfiguration(logDirectory)
                .setUsePlaintext(true)
                .setMaxSize(1024);

        testWithConfiguration(LogLevel.DEBUG, config, new Runnable() {
            @Override
            public void run() {

                // this should create two files, as the 1KB logs + extra header
                writeOneKiloByteOfLog();

                int maxRotateCount = config.getMaxRotateCount();
                int totalFilesShouldBeInDirectory = (maxRotateCount + 1) * 5;
                int totalLogFilesSaved = path.listFiles().length;
                assertEquals(totalFilesShouldBeInDirectory, totalLogFilesSaved);
            }
        });
        emptyDirectory(path.getAbsolutePath());
    }

    @Test
    public void testFileLoggingDisableLogging() {
        final File path = new File(
                context.getCacheDir().getAbsolutePath(),
                "testFileLoggingDisableLogging"
        );
        final String logDirectory = emptyDirectory(path.getAbsolutePath());
        final LogFileConfiguration config = new LogFileConfiguration(logDirectory)
                .setUsePlaintext(true);
        testWithConfiguration(LogLevel.NONE, config, new Runnable() {
            @Override
            public void run() {
                String uuidString = UUID.randomUUID().toString();
                writeAllLogs(uuidString);

                try {
                    for (File log : path.listFiles()) {
                        byte[] encoded = Files.readAllBytes(log.toPath());
                        String contents = new String(
                                encoded,
                                StandardCharsets.US_ASCII
                        );
                        assertFalse(contents.contains(uuidString));
                    }
                } catch(Exception e) {
                    fail("Exception during test callback " + e.toString());
                }
            }
        });
        emptyDirectory(path.getAbsolutePath());
    }

    @Test
    public void testFileLoggingReEnableLogging() {
        final File path = new File(
                context.getCacheDir().getAbsolutePath(),
                "testFileLoggingReEnableLogging"
        );
        final String logDirectory = emptyDirectory(path.getAbsolutePath());
        LogFileConfiguration config = new LogFileConfiguration(logDirectory)
                .setUsePlaintext(true);
        testWithConfiguration(LogLevel.NONE, config, new Runnable() {
            @Override
            public void run() {
                String uuidString = UUID.randomUUID().toString();
                writeAllLogs(uuidString);

                try {
                    for (File log : path.listFiles()) {
                        byte[] encoded = Files.readAllBytes(log.toPath());
                        String contents = new String(
                                encoded,
                                StandardCharsets.US_ASCII
                        );
                        assertFalse(contents.contains(uuidString));
                    }

                    Database.log.getFile().setLevel(LogLevel.VERBOSE);
                    writeAllLogs(uuidString);

                    File[] filesExceptDebug = path.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return !name.toLowerCase().startsWith("cbl_debug_");
                        }
                    });

                    for (File log : filesExceptDebug) {
                        byte[] encoded = Files.readAllBytes(log.toPath());
                        String contents = new String(
                                encoded,
                                StandardCharsets.US_ASCII
                        );
                        assertTrue(contents.contains(uuidString));
                    }

                } catch(Exception e) {
                    fail("Exception during test callback " + e.toString());
                }
            }
        });
        emptyDirectory(path.getAbsolutePath());
    }

    @Test @Ignore
    public void testFileLoggingHeader() {
        final File path = new File(
                context.getCacheDir().getAbsolutePath(),
                "testFileLoggingHeader"
        );
        final String logDirectory = emptyDirectory(path.getAbsolutePath());
        LogFileConfiguration config = new LogFileConfiguration(logDirectory)
                .setUsePlaintext(true);
        testWithConfiguration(LogLevel.VERBOSE, config, new Runnable() {
            @Override
            public void run() {
                writeOneKiloByteOfLog();

                try {
                    for (File log : path.listFiles()) {
                        String firstLine = Files.readAllLines(log.toPath()).get(0);
                        assertTrue(firstLine.contains("CouchbaseLite/"));
                        assertTrue(firstLine.contains("Build/"));
                        assertTrue(firstLine.contains("Commit/"));
                    }
                } catch(Exception e) {
                    fail("Exception during test callback " + e.toString());
                }
            }
        });
        emptyDirectory(path.getAbsolutePath());
    }

    private void testWithConfiguration(LogLevel level, LogFileConfiguration config, Runnable r) {
        LogFileConfiguration old = Database.log.getFile().getConfig();
        Database.log.getFile().setConfig(config);
        Database.log.getFile().setLevel(level);
        try {
            r.run();
        } finally {
            Database.log.getFile().setLevel(LogLevel.INFO);
            Database.log.getFile().setConfig(old);
        }
    }

    private static String emptyDirectory(String path){
        File f = new File(path);
        if(f.exists()) {
            for (File log : f.listFiles()) {
                log.delete();
            }
            f.delete();
        }
        return path;
    }

    private static void writeOneKiloByteOfLog() {
        String message = "11223344556677889900"; // ~43 bytes
        for (int i = 0; i < 24; i++) { // 24 * 43 = 1032
            writeAllLogs(message);
        }
    }

    private static void writeAllLogs(String message) {
        Log.v(LogDomain.DATABASE.toString(), message);
        Log.i(LogDomain.DATABASE.toString(), message);
        Log.w(LogDomain.DATABASE.toString(), message);
        Log.e(LogDomain.DATABASE.toString(), message);
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
