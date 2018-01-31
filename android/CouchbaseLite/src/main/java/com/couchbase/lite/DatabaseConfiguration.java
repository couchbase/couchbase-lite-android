package com.couchbase.lite;

import android.content.Context;

/**
 * Configuration for opening a database.
 */
public final class DatabaseConfiguration {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private boolean readonly = false;
    private Context context = null;
    private String directory = null;
    private EncryptionKey encryptionKey = null;
    private ConflictResolver conflictResolver = null;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    public DatabaseConfiguration(Context context) {
        if (context == null)
            throw new IllegalArgumentException("context is null");
        this.readonly = false;
        this.context = context;
        this.directory = context.getFilesDir().getAbsolutePath();
        this.encryptionKey = null;
        this.conflictResolver = new DefaultConflictResolver();
    }

    public DatabaseConfiguration(DatabaseConfiguration config) {
        if (config == null)
            throw new IllegalArgumentException("config is null");
        this.readonly = false;
        this.context = config.context;
        this.directory = config.directory;
        this.encryptionKey = config.encryptionKey;
        this.conflictResolver = config.conflictResolver;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Set the path to the directory to store the database in. If the directory doesn't already exist it willbe created when the database is opened.
     *
     * @param directory the directory
     * @return The self object.
     */
    public DatabaseConfiguration setDirectory(String directory) {
        if (directory == null)
            throw new IllegalArgumentException("null directory is not allowed");
        if (readonly)
            throw new IllegalStateException("DatabaseConfiguration is readonly mode.");
        this.directory = directory;
        return this;
    }

    /**
     * Set a key to encrypt the database with. If the database does not exist and is being created,
     * it will use this key, and the same key must be given every time it's opened
     *
     * @param encryptionKey the key
     * @return The self object.
     */
    public DatabaseConfiguration setEncryptionKey(EncryptionKey encryptionKey) {
        if (readonly)
            throw new IllegalStateException("DatabaseConfiguration is readonly mode.");
        this.encryptionKey = encryptionKey;
        return this;
    }

    /**
     * Sets a custom conflict resolver used for solving the conflicts
     * when saving or deleting documents in the database. Without setting the
     * conflict resolver, CouchbaseLite will use the default conflict
     * resolver.
     *
     * @param conflictResolver The conflict resolver.
     * @return The self object.
     */
    public DatabaseConfiguration setConflictResolver(ConflictResolver conflictResolver) {
        if (conflictResolver == null)
            throw new IllegalArgumentException("conflictResolver parameter is null");
        if (readonly)
            throw new IllegalStateException("DatabaseConfiguration is readonly mode.");
        this.conflictResolver = conflictResolver;
        return this;
    }

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

    /**
     * Returns the conflict resolver for this database.
     *
     * @return the conflict resolver
     */
    public ConflictResolver getConflictResolver() {
        return conflictResolver;
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------
    DatabaseConfiguration readonlyCopy() {
        DatabaseConfiguration config = new DatabaseConfiguration(this);
        config.readonly = true;
        return config;
    }

    Context getContext() {
        return context;
    }

    String getTempDir() {
        return context.getCacheDir().getAbsolutePath();
    }
}
