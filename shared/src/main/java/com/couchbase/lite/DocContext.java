package com.couchbase.lite;

import com.couchbase.litecore.fleece.AllocSlice;
import com.couchbase.litecore.fleece.MContext;

/**
 * This DocContext implementation is simplified version of lite-core DocContext implementation
 * by eliminating unused variables and methods
 */
class DocContext extends MContext {
    private Database _db;

    DocContext(Database db) {
        super(new AllocSlice("{}".getBytes()), db.getSharedKeys().getFLSharedKeys());
        _db = db;
    }

    Database getDatabase() {
        return _db;
    }
}
