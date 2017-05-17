package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLArray;

/*package*/ class CBLFLArray {
    private FLArray flArray;
    private CBLC4Doc c4doc;
    private Database database;

    /*package*/  CBLFLArray(FLArray array, CBLC4Doc c4doc, Database database) {
        this.flArray = array;
        this.c4doc = c4doc;
        this.database = database;
    }

    /*package*/  FLArray getFLArray() {
        return flArray;
    }

    /*package*/  CBLC4Doc getC4doc() {
        return c4doc;
    }

    /*package*/  Database getDatabase() {
        return database;
    }
}
