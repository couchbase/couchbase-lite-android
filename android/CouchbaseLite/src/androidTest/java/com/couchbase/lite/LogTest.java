package com.couchbase.lite;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import com.couchbase.lite.internal.support.Log;

import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LogTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getTargetContext();
    }

    //region Custom Logging Tests
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
    //endregion

    //region File Logging Tests
    @Test
    public void testFileLoggingLoggingLevels() {
        final File path = new File(
                context.getCacheDir().getAbsolutePath(),
                "testFileLoggingLoggingLevels"
        );
        final String logDirectory = emptyDirectory(path.getAbsolutePath());
        LogFileConfiguration config = new LogFileConfiguration(logDirectory)
                .setUsePlaintext(true)
                .setMaxRotateCount(0);

        testWithConfiguration(LogLevel.INFO, config, new Runnable() {
            @Override
            public void run() {
                LogLevel levels[] = {
                        LogLevel.NONE,
                        LogLevel.ERROR,
                        LogLevel.WARNING,
                        LogLevel.INFO,
                        LogLevel.VERBOSE
                };
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
    public void testFileLoggingDefaultBinaryFormat() {
        final File path = new File(
                context.getCacheDir().getAbsolutePath(),
                "testFileLoggingDefaultBinaryFormat"
        );
        final String logDirectory = emptyDirectory(path.getAbsolutePath());
        LogFileConfiguration config = new LogFileConfiguration(logDirectory);

        testWithConfiguration(LogLevel.INFO, config, new Runnable() {
            @Override
            public void run() {
                Log.i("DATABASE", "TEST INFO");

                File[] files = path.listFiles();
                assertTrue(files != null);
                assertTrue(files.length > 0);
                File lastModifiedFile = files[0];
                for(File log : files) {
                    if (log.lastModified() > lastModifiedFile.lastModified()) {
                        lastModifiedFile = log;
                    }
                }

                try {
                    byte[] bytes = new byte[4];
                    InputStream is = new FileInputStream(lastModifiedFile);
                    assertTrue(is.read(bytes) == 4);
                    assertTrue(bytes[0] == (byte) 0xCF);
                    assertTrue(bytes[1] == (byte) 0xB2);
                    assertTrue(bytes[2] == (byte) 0xAB);
                    assertTrue(bytes[3] == (byte) 0x1B);
                } catch(Exception e) {
                    fail("Exception during test callback " + e.toString());
                }
            }
        });
    }

    @Test
    public void testFileLoggingUsePlainText() {
        final File path = new File(
                context.getCacheDir().getAbsolutePath(),
                "testFileLoggingUsePlainText"
        );
        final String logDirectory = emptyDirectory(path.getAbsolutePath());
        LogFileConfiguration config = new LogFileConfiguration(logDirectory).setUsePlaintext(true);

        testWithConfiguration(LogLevel.INFO, config, new Runnable() {
            @Override
            public void run() {
                String uuidString = UUID.randomUUID().toString();
                Log.i(LogDomain.DATABASE.toString(), uuidString);

                File[] files = path.listFiles(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String name) {
                        return name.toLowerCase().startsWith("cbl_info_");
                    }
                });
                assertTrue(files != null);
                assertTrue(files.length > 0);

                File lastModifiedFile = files[0];
                for(File log : files) {
                    if (log.lastModified() > lastModifiedFile.lastModified()) {
                        lastModifiedFile = log;
                    }
                }

                try {
                    byte[] encoded = Files.readAllBytes(lastModifiedFile.toPath());
                    String contentsOfLastModified = new String(encoded, StandardCharsets.US_ASCII);
                    assertTrue(contentsOfLastModified.contains(uuidString));

                } catch (Exception e) {
                    fail("Exception during test callback " + e.toString());
                }
            }
        });
    }

    @Test
    public void testFileLoggingLogFilename() {
        final File path = new File(
                context.getCacheDir().getAbsolutePath(),
                "testFileLoggingLogFilename"
        );
        final String logDirectory = emptyDirectory(path.getAbsolutePath());
        LogFileConfiguration config = new LogFileConfiguration(logDirectory);

        testWithConfiguration(LogLevel.DEBUG, config, new Runnable() {
            @Override
            public void run() {
                Log.i(LogDomain.DATABASE.toString(), "TEST MESSAGE");
                File[] files = path.listFiles();
                assertTrue(files != null);
                assertTrue(files.length > 0);

                String filenameRegex = "cbl_(debug|verbose|info|warning|error)_\\d+\\.cbllog";
                for (File file : files) {
                    assertTrue(file.getName().matches(filenameRegex));
                }
            }
        });
    }
    //endregion

    //region Helper methods
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
        }

        return path;
    }
    //endregion
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
