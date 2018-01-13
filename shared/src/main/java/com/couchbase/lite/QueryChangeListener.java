package com.couchbase.lite;

/**
 * The listener interface for receiving Live Query change events.
 */
public interface QueryChangeListener {
    /**
     * The callback function from live query
     *
     * @param change the query change informaiton
     */
    void changed(QueryChange change);
}
