package com.couchbase.lite;

public enum DocumentFlag {
    DocumentFlagsDeleted(1 << 0),
    DocumentFlagsAccessRemoved(1 << 1);

    private final int rawValue;

    private DocumentFlag(int rawValue) {
        this.rawValue = rawValue;
    }

    public int rawValue() {
        return rawValue;
    }
}
