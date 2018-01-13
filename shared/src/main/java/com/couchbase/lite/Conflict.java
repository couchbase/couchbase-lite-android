package com.couchbase.lite;

/**
 * Provides details about a Conflict.
 */
public final class Conflict {
    private Document mine;
    private Document theirs;
    private Document base;

    Conflict(Document mine, Document theirs, Document base) {
        this.mine = mine;
        this.theirs = theirs;
        this.base = base;
    }

    /**
     * Return the mine version of the document.
     */
    public Document getMine() {
        return mine;
    }

    /**
     * Return the theirs version of the document.
     */
    public Document getTheirs() {
        return theirs;
    }

    /**
     * Return the base or common anchester version of the document.
     */
    public Document getBase() {
        return base;
    }
}
