package com.couchbase.lite;

import java.util.Collections;
import java.util.List;

public class DatabaseChange {
    final private List<String> documentIDs;
    final private Database database;

    /* package */ DatabaseChange(Database database, List<String> documentIDs/*, long lastSequence, boolean external*/) {
        this.database = database;
        // make List unmodifiable
        this.documentIDs = Collections.unmodifiableList(documentIDs);
    }

    public Database getDatabase() {
        return database;
    }

    public List<String> getDocumentIDs() {
        return documentIDs;
    }

}
