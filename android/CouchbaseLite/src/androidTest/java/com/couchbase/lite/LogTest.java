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
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.UUID;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

public class LogTest extends BaseTest {
    private Context context;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        context = InstrumentationRegistry.getTargetContext();
    }

    //region Custom Logging Tests
    @Test
    public void testCustomLoggingLevels() {
        LogTestLogger customLogger = new LogTestLogger();
        Database.log.setCustom(customLogger);

        for(int i = 5; i >= 1; i--) {
            customLogger.getLines().clear();
            customLogger.setLevel(LogLevel.values()[i]);
            Log.v(LogDomain.DATABASE, "TEST VERBOSE");
            Log.i(LogDomain.DATABASE, "TEST INFO");
            Log.w(LogDomain.DATABASE, "TEST WARNING");
            Log.e(LogDomain.DATABASE, "TEST ERROR");
            assertEquals(5 - i, customLogger.getLines().size());
        }
    }

    @Test
    public void testEnableAndDisableCustomLogging() {
        LogTestLogger customLogger = new LogTestLogger();
        Database.log.setCustom(customLogger);

        customLogger.setLevel(LogLevel.NONE);
        Log.v(LogDomain.DATABASE, "TEST VERBOSE");
        Log.i(LogDomain.DATABASE, "TEST INFO");
        Log.w(LogDomain.DATABASE, "TEST WARNING");
        Log.e(LogDomain.DATABASE, "TEST ERROR");
        assertEquals(0, customLogger.getLines().size());

        customLogger.setLevel(LogLevel.VERBOSE);
        Log.v(LogDomain.DATABASE, "TEST VERBOSE");
        Log.i(LogDomain.DATABASE, "TEST INFO");
        Log.w(LogDomain.DATABASE, "TEST WARNING");
        Log.e(LogDomain.DATABASE, "TEST ERROR");
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
                    Log.v(LogDomain.DATABASE, "TEST VERBOSE");
                    Log.i(LogDomain.DATABASE, "TEST INFO");
                    Log.w(LogDomain.DATABASE, "TEST WARNING");
                    Log.e(LogDomain.DATABASE, "TEST ERROR");
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
                Log.i(LogDomain.DATABASE, "TEST INFO");

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
                Log.i(LogDomain.DATABASE, uuidString);

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
                    byte[] b = new byte[(int) lastModifiedFile.length()];
                    FileInputStream fileInputStream = new FileInputStream(lastModifiedFile);
                    fileInputStream.read(b);
                    String contentsOfLastModified = new String(b, StandardCharsets.US_ASCII);
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
                Log.i(LogDomain.DATABASE, "TEST MESSAGE");
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

    @Test
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
                if (!BuildConfig.DEBUG) {
                    totalFilesShouldBeInDirectory -= 1;
                }
                int totalLogFilesSaved = path.listFiles().length;
                assertEquals(totalFilesShouldBeInDirectory, totalLogFilesSaved);
            }
        });
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
                        byte[] b = new byte[(int) log.length()];
                        FileInputStream fileInputStream = new FileInputStream(log);
                        fileInputStream.read(b);
                        String contents = new String(b, StandardCharsets.US_ASCII);
                        assertFalse(contents.contains(uuidString));
                    }
                } catch(Exception e) {
                    fail("Exception during test callback " + e.toString());
                }
            }
        });
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
                        byte[] b = new byte[(int) log.length()];
                        FileInputStream fileInputStream = new FileInputStream(log);
                        fileInputStream.read(b);
                        String contents = new String(b, StandardCharsets.US_ASCII);
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
                        byte[] b = new byte[(int) log.length()];
                        FileInputStream fileInputStream = new FileInputStream(log);
                        fileInputStream.read(b);
                        String contents = new String(b, StandardCharsets.US_ASCII);
                        assertTrue(contents.contains(uuidString));
                    }

                } catch(Exception e) {
                    fail("Exception during test callback " + e.toString());
                }
            }
        });
    }

    @Test
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
                        BufferedReader fin = new BufferedReader(new FileReader(log));
                        String firstLine = fin.readLine();
                        assertNotNull(firstLine);
                        assertTrue(firstLine.contains("CouchbaseLite/"));
                        assertTrue(firstLine.contains("Build/"));
                        assertTrue(firstLine.contains("Commit/"));
                    }
                } catch(Exception e) {
                    fail("Exception during test callback " + e.toString());
                }
            }
        });
    }

    @Test
    public void testWriteLogWithError() {
        final File path = new File(
                context.getCacheDir().getAbsolutePath(),
                "testLogWithError"
        );
        final String logDirectory = emptyDirectory(path.getAbsolutePath());
        LogFileConfiguration config = new LogFileConfiguration(logDirectory)
                .setUsePlaintext(true);
        testWithConfiguration(LogLevel.DEBUG, config, new Runnable() {
            @Override
            public void run() {
                String uuid = UUID.randomUUID().toString();
                String message = "test message";
                CouchbaseLiteException error = new CouchbaseLiteException(uuid);
                Log.v(LogDomain.DATABASE, message, error);
                Log.i(LogDomain.DATABASE, message, error);
                Log.w(LogDomain.DATABASE, message, error);
                Log.e(LogDomain.DATABASE, message, error);
                Log.d(LogDomain.DATABASE, message, error);
                try {
                    for (File log : path.listFiles()) {
                        byte[] b = new byte[(int) log.length()];
                        FileInputStream fileInputStream = new FileInputStream(log);
                        fileInputStream.read(b);
                        String contents = new String(b, StandardCharsets.US_ASCII);
                        assertTrue(contents.contains(uuid));
                    }
                } catch(Exception e) {
                    fail("Exception during test callback " + e.toString());
                }
            }
        });
    }

    @Test
    public void testWriteLogWithErrorAndArgs() {
        final File path = new File(
                context.getCacheDir().getAbsolutePath(),
                "testWriteLogWithErrorAndArgs"
        );
        final String logDirectory = emptyDirectory(path.getAbsolutePath());
        LogFileConfiguration config = new LogFileConfiguration(logDirectory)
                .setUsePlaintext(true);
        testWithConfiguration(LogLevel.DEBUG, config, new Runnable() {
            @Override
            public void run() {
                String uuid1 = UUID.randomUUID().toString();
                String uuid2 = UUID.randomUUID().toString();
                String message = "test message %s";
                CouchbaseLiteException error = new CouchbaseLiteException(uuid1);
                Log.v(LogDomain.DATABASE, message, error, uuid2);
                Log.i(LogDomain.DATABASE, message, error, uuid2);
                Log.w(LogDomain.DATABASE, message, error, uuid2);
                Log.e(LogDomain.DATABASE, message, error, uuid2);
                Log.d(LogDomain.DATABASE, message, error, uuid2);
                try {
                    for (File log : path.listFiles()) {
                        byte[] b = new byte[(int) log.length()];
                        FileInputStream fileInputStream = new FileInputStream(log);
                        fileInputStream.read(b);
                        String contents = new String(b, StandardCharsets.US_ASCII);
                        assertTrue(contents.contains(uuid1));
                        assertTrue(contents.contains(uuid2));
                    }
                } catch(Exception e) {
                    fail("Exception during test callback " + e.toString());
                }
            }
        });
    }

    @Test
    public void testLogFileConfigurationConstructors() {
        int rotateCount = 4;
        long maxSize = 2048;
        boolean usePlainText = true;
        LogFileConfiguration config;

        thrown.expect(IllegalArgumentException.class);
        config = new LogFileConfiguration((String)null);

        thrown.expect(IllegalArgumentException.class);
        config = new LogFileConfiguration((LogFileConfiguration) null);

        final File path1 = new File(
                context.getCacheDir().getAbsolutePath(),
                "testFileLogConfiguration"
        );
        config = new LogFileConfiguration(path1.getAbsolutePath())
                .setMaxRotateCount(rotateCount)
                .setMaxSize(maxSize)
                .setUsePlaintext(usePlainText);
        assertEquals(config.getMaxRotateCount(), rotateCount);
        assertEquals(config.getMaxSize(), maxSize);
        assertEquals(config.usesPlaintext(), usePlainText);
        assertEquals(config.getDirectory(), path1.getAbsolutePath());

        // validate with LogFileConfiguration(String, config) constructor
        final File path2 = new File(
                context.getCacheDir().getAbsolutePath(),
                "testFileLogConfigurationNew"
        );
        LogFileConfiguration newConfig = new LogFileConfiguration(path2.getAbsolutePath(), config);
        assertEquals(newConfig.getMaxRotateCount(), rotateCount);
        assertEquals(newConfig.getMaxSize(), maxSize);
        assertEquals(newConfig.usesPlaintext(), usePlainText);
        assertEquals(newConfig.getDirectory(), path2.getAbsolutePath());
    }

    @Test
    public void testEditReadOnlyLogFileConfiguration() {
        final File path = new File(
                context.getCacheDir().getAbsolutePath(),
                "testEditReadOnlyLogFileConfiguration"
        );
        final String logDirectory = emptyDirectory(path.getAbsolutePath());
        LogFileConfiguration config = new LogFileConfiguration(logDirectory);
        Database.log.getFile().setConfig(config);

        thrown.expect(IllegalStateException.class);
        Database.log.getFile().getConfig().setMaxSize(1024);

        thrown.expect(IllegalStateException.class);
        Database.log.getFile().getConfig().setMaxRotateCount(3);

        thrown.expect(IllegalStateException.class);
        Database.log.getFile().getConfig().setUsePlaintext(true);
    }

    //endregion

    @Test
    public void testNonASCII() throws CouchbaseLiteException {
        LogTestLogger customLogger = new LogTestLogger();
        customLogger.setLevel(LogLevel.VERBOSE);
        Database.log.setCustom(customLogger);
        Database.log.getConsole().setDomains(EnumSet.of(LogDomain.ALL));
        Database.log.getConsole().setLevel(LogLevel.VERBOSE);

        String hebrew = "מזג האוויר נחמד היום"; // The weather is nice today.
        MutableDocument doc = new MutableDocument();
        doc.setString("hebrew", hebrew);
        save(doc);

        Query query = QueryBuilder.select(SelectResult.all()).from(DataSource.database(db));
        assertEquals(query.execute().allResults().size(), 1);

        String expectedHebrew = "[{\"hebrew\":\"" + hebrew + "\"}]";
        boolean found = false;
        for (String line : customLogger.getLines()) {
            if (line.contains(expectedHebrew)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    //region Helper methods
    private void testWithConfiguration(LogLevel level, LogFileConfiguration config, Runnable r) {
        LogFileConfiguration old = Database.log.getFile().getConfig();
        EnumSet<LogDomain> domains = Database.log.getConsole().getDomains();
        Database.log.getFile().setConfig(config);
        Database.log.getFile().setLevel(level);
        try {
            r.run();
        } finally {
            Database.log.getFile().setLevel(LogLevel.INFO);
            Database.log.getFile().setConfig(old);
            Database.log.getConsole().setDomains(domains);
            Database.log.getConsole().setLevel(LogLevel.WARNING);
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

    private static void writeOneKiloByteOfLog() {
        String message = "11223344556677889900"; // ~43 bytes
        for (int i = 0; i < 24; i++) { // 24 * 43 = 1032
            writeAllLogs(message);
        }
    }

    private static void writeAllLogs(String message) {
        Log.v(LogDomain.DATABASE, message);
        Log.i(LogDomain.DATABASE, message);
        Log.w(LogDomain.DATABASE, message);
        Log.e(LogDomain.DATABASE, message);
        Log.d(LogDomain.DATABASE, message);
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
