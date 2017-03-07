package com.couchbase.lite;

import java.io.File;

public final class DatabaseOptions {
    private File directory; // TODO: File or String
    private Object encryptionKey;
    private boolean readOnly;

    public DatabaseOptions() {
    }

    public static DatabaseOptions getDefaultOptions() {
        return new DatabaseOptions();
    }

    public File getDirectory() {
        return directory;
    }

    public void setDirectory(File directory) {
        this.directory = directory;
    }

    public Object getEncryptionKey() {
        return encryptionKey;
    }

    public void setEncryptionKey(Object encryptionKey) {
        this.encryptionKey = encryptionKey;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
}
