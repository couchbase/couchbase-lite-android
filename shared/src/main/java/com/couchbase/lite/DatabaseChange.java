package com.couchbase.lite;

import java.util.Collections;
import java.util.List;

/**
 * Provides details about a Database change.
 */
public class DatabaseChange {
    final private List<String> documentIDs;
    final private Database database;

    /* package */ DatabaseChange(Database database, List<String> documentIDs/*, long lastSequence, boolean external*/) {
        this.database = database;
        // make List unmodifiable
        this.documentIDs = Collections.unmodifiableList(documentIDs);
    }

    /**
     * Returns the database instance
     */
    public Database getDatabase() {
        return database;
    }

    /**
     * Returns the list of the changed document IDs
     *
     * @return
     */
    public List<String> getDocumentIDs() {
        return documentIDs;
    }
}
