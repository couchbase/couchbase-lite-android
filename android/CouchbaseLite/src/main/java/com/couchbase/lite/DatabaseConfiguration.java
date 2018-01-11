package com.couchbase.lite;

import android.content.Context;

public final class DatabaseConfiguration {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Context context = null;
    private String directory = null;
    private EncryptionKey encryptionKey = null;
    private ConflictResolver conflictResolver = null;

    //---------------------------------------------
    // Builder
    //---------------------------------------------
    public final static class Builder {
        //---------------------------------------------
        // member variables
        //---------------------------------------------
        DatabaseConfiguration conf;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------
        public Builder(Context context) {
            if (context == null)
                throw new IllegalArgumentException("context parameter is null");
            conf = new DatabaseConfiguration(context);
        }

        public Builder(DatabaseConfiguration config) {
            if (config == null)
                throw new IllegalArgumentException("config parameter is null");
            conf = config.copy();
        }
        //---------------------------------------------
        // Setters
        //---------------------------------------------

        /**
         * Set the path to the directory to store the database in. If the directory doesn't already exist it willbe created when the database is opened.
         *
         * @param directory the directory
         */
        public DatabaseConfiguration.Builder setDirectory(String directory) {
            if (directory == null)
                throw new IllegalArgumentException("null directory is not allowed");
            conf.directory = directory;
            return this;
        }

        /**
         * Set a key to encrypt the database with. If the database does not exist and is being created,
         * it will use this key, and the same key must be given every time it's opened
         *
         * @param encryptionKey the key
         */
        public DatabaseConfiguration.Builder setEncryptionKey(EncryptionKey encryptionKey) {
            conf.encryptionKey = encryptionKey;
            return this;
        }

        public DatabaseConfiguration.Builder setConflictResolver(ConflictResolver conflictResolver) {
            if (conflictResolver == null)
                throw new IllegalArgumentException("conflictResolver parameter is null");
            conf.conflictResolver = conflictResolver;
            return this;
        }

        //---------------------------------------------
        // public API
        //---------------------------------------------
        public DatabaseConfiguration build() {
            return conf.copy();
        }
    }

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    private DatabaseConfiguration(Context context, String directory, EncryptionKey encryptionKey, ConflictResolver conflictResolver) {
        this.context = context;
        this.directory = directory;
        this.encryptionKey = encryptionKey;
        this.conflictResolver = conflictResolver;
    }

    private DatabaseConfiguration(Context context) {
        if (context == null)
            throw new IllegalArgumentException("context is null");
        this.context = context;
        this.directory = context.getFilesDir().getAbsolutePath();
        this.encryptionKey = null;
        this.conflictResolver = new DefaultConflictResolver();
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Returns the path to the directory to store the database in.
     *
     * @return the directory
     */
    public String getDirectory() {
        return directory;
    }


    /**
     * Returns a key to encrypt the database with.
     *
     * @return the key
     */
    public EncryptionKey getEncryptionKey() {
        return encryptionKey;
    }


    public ConflictResolver getConflictResolver() {
        return conflictResolver;
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------
    DatabaseConfiguration copy() {
        return new DatabaseConfiguration(context, directory, encryptionKey, conflictResolver);
    }

    Context getContext() {
        return context;
    }

    String getTempDir() {
        return context.getCacheDir().getAbsolutePath();
    }
}
