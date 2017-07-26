package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLArray;

class CBLFLArray {
    private FLArray flArray;
    private CBLC4Doc c4doc;
    private CBLFLDataSource lflDataSource;
    private Database database;

    CBLFLArray(FLArray array, CBLFLDataSource lflDataSource, Database database) {
        this.flArray = array;
        this.lflDataSource = lflDataSource;
        this.database = database;
    }

    FLArray getFLArray() {
        return flArray;
    }

    CBLC4Doc getC4doc() {
        return c4doc;
    }

    CBLFLDataSource getLflDataSource() {
        return lflDataSource;
    }

    Database getDatabase() {
        return database;
    }
}
