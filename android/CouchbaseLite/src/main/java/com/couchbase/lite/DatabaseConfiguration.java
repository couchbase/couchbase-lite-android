package com.couchbase.lite;

import android.content.Context;

public class DatabaseConfiguration extends BaseDatabaseConfiguration {
    private Context context;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    private DatabaseConfiguration() {
        // only for copy() method.
    }

    public DatabaseConfiguration(Context context) {
        if (context == null)
            throw new IllegalArgumentException("context is null");

        this.context = context;
        setDirectory(context.getFilesDir());
        setConflictResolver(null);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    /* package */ Context getContext() {
        return context;
    }

    /* package */ DatabaseConfiguration copy() {
        DatabaseConfiguration config = new DatabaseConfiguration();
        config.context = this.context;
        config.setConflictResolver(this.getConflictResolver());
        config.setDirectory(this.getDirectory());
        config.setEncryptionKey(this.getEncryptionKey());
        return config;
    }
}
