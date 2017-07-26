package com.couchbase.lite;

import com.couchbase.litecore.fleece.FLDict;

class CBLFLDict {
    private FLDict flDict;
    private CBLFLDataSource flDataSource;
    private Database database;

    CBLFLDict(FLDict dict, CBLFLDataSource flDataSource, Database database) {
        this.flDict = dict;
        this.flDataSource = flDataSource;
        this.database = database;
    }

    FLDict getFlDict() {
        return flDict;
    }

    CBLFLDataSource getFlDataSource() {
        return flDataSource;
    }

    Database getDatabase() {
        return database;
    }
}
