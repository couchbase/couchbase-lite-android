package com.couchbase.lite;

import com.couchbase.lite.internal.Misc;

public class ReadOnlyDocument extends ReadOnlyDictionary {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private String id;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /* package */ ReadOnlyDocument(String id, ReadOnlyDictionaryInterface data) {
        super(data);
        this.id = id;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------
    public String getId() {
        if (id == null)
            id = Misc.CreateUUID();
        return id;
    }

    public long getSequence() {
        //return c4doc.getSequence();
        return 0;
    }

    public boolean isDeleted() {
        //return c4doc.deleted();
        return false;
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------
}
