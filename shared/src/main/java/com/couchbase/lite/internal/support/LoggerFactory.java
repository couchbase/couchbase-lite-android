package com.couchbase.lite.internal.support;

public class LoggerFactory {
    static String classname = "com.couchbase.lite.internal.support.LoggerImpl";

    public static Logger createLogger() {
        try {
            //Log.v(Database.TAG, "Loading logger: %s", classname);
            Class clazz = Class.forName(classname);
            Logger logger = (Logger) clazz.newInstance();
            return logger;
        } catch (Exception e) {
            System.err.println("Failed to load the logger: " + classname + ". Use SystemLogger.");
            return new SystemLogger();
        }
    }
}
