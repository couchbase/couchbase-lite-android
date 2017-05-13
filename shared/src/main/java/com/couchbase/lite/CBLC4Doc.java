package com.couchbase.lite;


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
    int getFlags(){
        return rawDoc.getFlags();
    }
    long getSequence(){
        return rawDoc.getSequence();
    }
    String getRevID(){
        return rawDoc.getRevID();
    }
//    boolean deleted(){
//        return rawDoc.deleted();
//    }
//    boolean exists(){
//        return rawDoc.exists();
//    }

//    void getSelectedRev(){
//        return rawDoc.se
//    }

    void free(){
        if (rawDoc != null) {
            rawDoc.free();
            rawDoc = null;
        }
    }
}
