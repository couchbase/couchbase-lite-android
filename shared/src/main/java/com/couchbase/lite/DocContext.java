package com.couchbase.lite;

import com.couchbase.litecore.fleece.AllocSlice;
import com.couchbase.litecore.fleece.MContext;

public class DocContext extends MContext {
    DocContext(Database db) {
        super(new AllocSlice("{}".getBytes()), db.getSharedKeys().getFLSharedKeys());
        this.setNative(db);
    }

    public void free() {
        super.free();
    }

    public Database getDatabase() {
        return (Database) getNative();
    }

    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }
}
