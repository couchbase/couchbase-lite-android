package com.couchbase.cblite;

import java.util.List;

/**
 * Block container for the reduce callback function
 */
public interface CBLViewReduceBlock {

    /**
     * A "reduce" function called to summarize the results of a view.
     *
     * @param keys An array of keys to be reduced (or null if this is a rereduce).
     * @param values A parallel array of values to be reduced, corresponding 1::1 with the keys.
     * @param rereduce true if the input values are the results of previous reductions.
     * @return The reduced value; almost always a scalar or small fixed-size object.
     */
    Object reduce(List<Object> keys, List<Object> values, boolean rereduce);

}
