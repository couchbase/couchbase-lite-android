package com.couchbase.lite;

/**
 * This ResultContext implementation is simplified version of lite-core ResultContext implementation
 * by eliminating unused variables and methods
 */
class ResultContext extends DocContext {
    ResultContext(Database db) {
        super(db);
    }
}
