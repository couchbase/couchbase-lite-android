package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLArray;

class CBLFLArray {
    private FLArray flArray;
    private CBLC4Doc c4doc;
    private CBLFLDataSource flDataSource;
    private Database database;

    CBLFLArray(FLArray array, CBLFLDataSource flDataSource, Database database) {
        this.flArray = array;
        this.flDataSource = flDataSource;
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
