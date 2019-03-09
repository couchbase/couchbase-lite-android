package com.couchbase.lite;

import android.support.annotation.NonNull;


/**
 * A class that describes the file configuration for the {@link FileLogger} class.
 * These options must be set atomically so they won't take effect unless a new
 * configuration object is set on the logger.  Attempting to modify an in-use
 * configuration object will result in an exception being thrown.
 */
public final class LogFileConfiguration {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private final String directory;
    private int maxRotateCount = 1;
    private long maxSize = 1024 * 500;


    private boolean readonly;
    private boolean usePlaintext;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Constructs a file configuration object with the given directory
     *
     * @param directory The directory that the logs will be written to
     */
    public LogFileConfiguration(@NonNull String directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory cannot be null");
        }

        this.directory = directory;
    }

    /**
     * Constructs a file configuration object based on another one so
     * that it may be modified
     *
     * @param other The other configuration to copy settings from
     */
    public LogFileConfiguration(@NonNull LogFileConfiguration other) {
        if (other == null) {
            throw new IllegalArgumentException("other cannot be null");
        }

        directory = other.directory;
        maxRotateCount = other.maxRotateCount;
        maxSize = other.maxSize;
        usePlaintext = other.usePlaintext;
    }

    /**
     * Constructs a file configuration object based on another one but changing
     * the directory
     *
     * @param directory The directory that the logs will be written to
     * @param other     The other configuration to copy settings from
     */
    public LogFileConfiguration(@NonNull String directory, LogFileConfiguration other) {
        this(directory);
        if (other != null) {
            maxRotateCount = other.maxRotateCount;
            maxSize = other.maxSize;
            usePlaintext = other.usePlaintext;
        }
    }

    //---------------------------------------------
    // Setters
    //---------------------------------------------

    /**
     * Sets whether or not to log in plaintext.  The default is
     * to log in a binary encoded format that is more CPU and I/O friendly
     * and enabling plaintext is not recommended in production.
     *
     * @param usePlaintext Whether or not to log in plaintext
     * @return The self object
     */
    @NonNull
    public LogFileConfiguration setUsePlaintext(boolean usePlaintext) {
        if (readonly) { throw new IllegalStateException("LogFileConfiguration is readonly mode."); }

        this.usePlaintext = usePlaintext;
        return this;
    }

    /**
     * Gets the number of rotated logs that are saved (i.e.
     * if the value is 1, then 2 logs will be present:  the 'current'
     * and the 'rotated')
     *
     * @return The number of rotated logs that are saved
     */
    public int getMaxRotateCount() {
        return maxRotateCount;
    }

    /**
     * Sets the number of rotated logs that are saved (i.e.
     * if the value is 1, then 2 logs will be present:  the 'current'
     * and the 'rotated')
     *
     * @param maxRotateCount The number of rotated logs to be saved
     * @return The self object
     */
    @NonNull
    public LogFileConfiguration setMaxRotateCount(int maxRotateCount) {
        if (readonly) { throw new IllegalStateException("LogFileConfiguration is readonly mode."); }

        this.maxRotateCount = maxRotateCount;
        return this;
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    /**
     * Gets the max size of the log file in bytes.  If a log file
     * passes this size then a new log file will be started.  This
     * number is a best effort and the actual size may go over slightly.
     *
     * @return The max size of the log file in bytes
     */
    public long getMaxSize() {
        return maxSize;
    }

    /**
     * Sets the max size of the log file in bytes.  If a log file
     * passes this size then a new log file will be started.  This
     * number is a best effort and the actual size may go over slightly.
     *
     * @param maxSize The max size of the log file in bytes
     * @return The self object
     */
    @NonNull
    public LogFileConfiguration setMaxSize(long maxSize) {
        if (readonly) { throw new IllegalStateException("LogFileConfiguration is readonly mode."); }

        this.maxSize = maxSize;
        return this;
    }

    /**
     * Gets whether or not CBL is logging in plaintext.  The default is
     * to log in a binary encoded format that is more CPU and I/O friendly
     * and enabling plaintext is not recommended in production.
     *
     * @return Whether or not CBL is logging in plaintext
     */
    public boolean usesPlaintext() {
        return usePlaintext;
    }

    /**
     * Gets the directory that the logs files are stored in.
     *
     * @return The directory that the logs files are stored in.
     */
    @NonNull
    public String getDirectory() {
        return directory;
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    LogFileConfiguration readOnlyCopy() {
        final LogFileConfiguration config = new LogFileConfiguration(this);
        config.readonly = true;
        return config;
    }
}
