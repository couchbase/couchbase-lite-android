package com.couchbase.lite;

/**
 * The listener interface for receiving Database change events.
 */
public interface DatabaseChangeListener {
    /**
     * Callback function from Database when database has change
     *
     * @param change the database change information
     */
    void changed(DatabaseChange change);
}
