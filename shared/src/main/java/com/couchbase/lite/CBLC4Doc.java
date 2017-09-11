package com.couchbase.lite;

import com.couchbase.litecore.C4Document;

class CBLC4Doc implements CBLFLDataSource {
    private C4Document rawDoc;

    CBLC4Doc(C4Document rawDoc) {
        this.rawDoc = rawDoc;
    }

    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }

    C4Document getRawDoc() {
        return rawDoc;
    }

    void free() {
        if (rawDoc != null) {
            rawDoc.free();
            rawDoc = null;
        }
    }

    // --- Following is Java only methods

    String getSelectedRevID() {
        return rawDoc != null ? rawDoc.getSelectedRevID() : null;
    }

    long getSelectedSequence() {
        return rawDoc != null ? rawDoc.getSelectedSequence() : 0;
    }
}
