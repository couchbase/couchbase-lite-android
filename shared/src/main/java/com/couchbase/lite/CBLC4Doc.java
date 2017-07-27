package com.couchbase.lite;

import com.couchbase.litecore.C4Document;
import com.couchbase.litecore.LiteCoreException;

class CBLC4Doc implements CBLFLDataSource{
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

    int getFlags() {
        return rawDoc.getFlags();
    }

    long getSequence() {
        return rawDoc.getSequence();
    }

    String getRevID() {
        return rawDoc.getRevID();
    }

    void free() {
        if (rawDoc != null) {
            rawDoc.free();
            rawDoc = null;
        }
    }

    // --- Following is Java only methods

    String getSelectedRevID() {
        return rawDoc.getSelectedRevID();
    }

    byte[] getSelectedBody() throws LiteCoreException {
        return rawDoc.getSelectedBody();
    }

    long getSelectedSequence() {
        return rawDoc.getSelectedSequence();
    }

    long getSelectedRevFlags() {
        return rawDoc.getSelectedFlags();
    }
}
