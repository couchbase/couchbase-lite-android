package com.couchbase.lite;


import com.couchbase.litecore.LiteCoreException;

/*package*/ class CBLC4Doc {
    private com.couchbase.litecore.Document rawDoc;

    /*package*/ CBLC4Doc(com.couchbase.litecore.Document rawDoc) {
        this.rawDoc = rawDoc;
    }

    @Override
    protected void finalize() throws Throwable {
        free();
        super.finalize();
    }

    /*package*/ com.couchbase.litecore.Document getRawDoc() {
        return rawDoc;
    }

    /*package*/ int getFlags() {
        return rawDoc.getFlags();
    }

    /*package*/ long getSequence() {
        return rawDoc.getSequence();
    }

    /*package*/ String getRevID() {
        return rawDoc.getRevID();
    }

    /*package*/ void free() {
        if (rawDoc != null) {
            rawDoc.free();
            rawDoc = null;
        }
    }

    // --- Following is Java only methods

    /*package*/String getSelectedRevID() {
        return rawDoc.getSelectedRevID();
    }

    /*package*/ byte[] getSelectedBody() throws LiteCoreException {
        return rawDoc.getSelectedBody();
    }

    /*package*/ long getSelectedSequence() {
        return rawDoc.getSelectedSequence();
    }

    /*package*/ long getSelectedRevFlags() {
        return rawDoc.getSelectedRevFlags();
    }
}
