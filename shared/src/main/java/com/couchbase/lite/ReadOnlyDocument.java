package com.couchbase.lite;

import com.couchbase.lite.internal.Misc;

import java.util.Locale;

public class ReadOnlyDocument extends ReadOnlyDictionary {
    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private String id;
    private com.couchbase.litecore.Document c4doc;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /* package */ ReadOnlyDocument(String id,
                                   com.couchbase.litecore.Document c4doc,
                                   CBLFLDict data) {
        super(data);
        this.id = id;
        this.c4doc = c4doc;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------
    /**
     * return the document's ID.
     *
     * @return the document's ID
     */
    public String getId() {
        if (id == null)
            id = Misc.CreateUUID();
        return id;
    }

    /**
     * Return the sequence number of the document in the database.
     * This indicates how recently the document has been changed: every time any document is updated,
     * the database assigns it the next sequential sequence number. Thus, if a document's `sequence`
     * property changes that means it's been changed (on-disk); and if one document's `sequence`
     * is greater than another's, that means it was changed more recently.
     *
     * @return the sequence number of the document in the database.
     */
    public long getSequence() {
        return c4doc != null ? c4doc.getSequence() : 0;
    }

    /**
     * Return whether the document is deleted
     *
     * @return true if deleted, false otherwise
     */
    public boolean isDeleted() {
        return c4doc != null ? c4doc.deleted() : false;
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "ReadOnlyDocument[%s]", id);
    }

    //---------------------------------------------
    // protected level access
    //---------------------------------------------



    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    /**
     * Return whether the document exists in the database.
     *
     * @return true if exists, false otherwise.
     */
    /* package */ boolean exists() {
        return c4doc != null ? c4doc.exists() : false;
    }

    /* package */ long generation() {
        // TODO:
        return 0;
    }

    /*package*/ com.couchbase.litecore.Document getC4doc() {
        return c4doc;
    }

    /*package*/  void setC4doc(com.couchbase.litecore.Document c4doc) {
        this.c4doc = c4doc;
    }
    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------
}
