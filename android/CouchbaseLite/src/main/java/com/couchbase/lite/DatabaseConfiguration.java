package com.couchbase.lite;

import android.content.Context;

public class DatabaseConfiguration extends BaseDatabaseConfiguration {
    private Context context;

    public DatabaseConfiguration() {
    }

    public DatabaseConfiguration(Context context) {
        this.context = context;
        setDirectory(context.getFilesDir());
        setConflictResolver(null);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    /* package */ DatabaseConfiguration copy() {
        DatabaseConfiguration config = new DatabaseConfiguration();
        config.context = this.context;
        config.setConflictResolver(this.getConflictResolver());
        config.setDirectory(this.getDirectory());
        config.setEncryptionKey(this.getEncryptionKey());
        return config;
    }
}
