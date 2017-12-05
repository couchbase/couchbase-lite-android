package com.couchbase.lite;

import java.util.Collections;
import java.util.List;

/**
 * Provides details about a Database change.
 */
public class DatabaseChange {
    final private List<String> documentIDs;
    final private Database database;

    DatabaseChange(Database database, List<String> documentIDs) {
        this.database = database;
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

    @Override
    public String toString() {
        return "DatabaseChange{" +
                "database=" + database +
                ", documentIDs=" + documentIDs +
                '}';
    }
}
