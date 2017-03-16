package com.couchbase.lite.internal.support;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;

public class SystemLogger implements Logger {
    private final static java.util.logging.Logger logger = java.util.logging.Logger.getLogger("com.couchbase.lite");

    static {
        // Logging levels are filtered on top of this class but if we don't set this to all then all info and
        // higher will get through no matter what levels are set higher in the chain.
        logger.setLevel(Level.ALL);
    }

    @Override
    public void v(String tag, String msg) {
        logger.finer(tag + ": " + msg);
    }

    @Override
    public void v(String tag, String msg, Throwable tr) {
        logger.finer(tag + ": " + msg + '\n' + getStackTraceString(tr));
    }

    @Override
    public void i(String tag, String msg) {
        logger.info(tag + ": " + msg);
    }

    @Override
    public void i(String tag, String msg, Throwable tr) {
        logger.info(tag + ": " + msg + '\n' + getStackTraceString(tr));
    }

    @Override
    public void w(String tag, String msg) {
        logger.warning(tag + ": " + msg);
    }

    @Override
    public void w(String tag, Throwable tr) {
        logger.warning(tag + ": " + '\n' + getStackTraceString(tr));
    }

    @Override
    public void w(String tag, String msg, Throwable tr) {
        logger.warning(tag + ": " + msg + '\n' + getStackTraceString(tr));
    }

    @Override
    public void e(String tag, String msg) {
        logger.severe(tag + ": " + msg);
    }

    @Override
    public void e(String tag, String msg, Throwable tr) {
        logger.severe(tag + ": " + msg + '\n' + getStackTraceString(tr));
    }

    private static String getStackTraceString(Throwable tr) {
        if (tr == null) return "";

        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        tr.printStackTrace(printWriter);

        return stringWriter.toString();
    }
}
