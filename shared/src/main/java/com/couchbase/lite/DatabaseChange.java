package com.couchbase.lite;


import java.util.Collections;
import java.util.List;

public class DatabaseChange {
    final List<String> docIDs;
    final long lastSequence;
    final boolean external;

    public DatabaseChange(List<String> docIDs, long lastSequence, boolean external) {
        // make List unmodifiable
        this.docIDs = Collections.unmodifiableList(docIDs);
        this.lastSequence = lastSequence;
        this.external = external;
    }

    public List<String> getDocIDs() {
        return docIDs;
    }

    public long getLastSequence() {
        return lastSequence;
    }

    public boolean isExternal() {
        return external;
    }
}
