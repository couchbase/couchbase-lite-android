package com.couchbase.lite;

public final class DatabaseOptions {
    private String directory;
    private Object encryptionKey;
    private boolean readOnly;

    public DatabaseOptions() {
    }

    public static DatabaseOptions getDefaultOptions() {
        return new DatabaseOptions();
    }

    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
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
