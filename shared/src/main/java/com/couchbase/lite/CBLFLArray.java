package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLArray;

class CBLFLArray {
    private FLArray flArray;
    private CBLC4Doc c4doc;
    private Database database;

    CBLFLArray(FLArray array, CBLC4Doc c4doc, Database database) {
        this.flArray = array;
        this.c4doc = c4doc;
        this.database = database;
    }

    FLArray getFLArray() {
        return flArray;
    }

    CBLC4Doc getC4doc() {
        return c4doc;
    }

    Database getDatabase() {
        return database;
    }
}
