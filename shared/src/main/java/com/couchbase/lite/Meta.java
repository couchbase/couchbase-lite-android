package com.couchbase.lite;

/**
 * Meta is a factory class for creating the expressions that refer to
 * the metadata properties of the document.
 */
public class Meta {
    //---------------------------------------------
    // Constructor
    //---------------------------------------------
    private Meta() {

    }
    //---------------------------------------------
    // API - public static variables
    //---------------------------------------------

    /**
     * A metadata expression referring to the ID of the document.
     */
    public static final MetaExpression id = new MetaExpression("_id", "id", null);

    /**
     * A metadata expression refering to the sequence number of the document.
     * The sequence number indicates how recently the document has been changed. If one document's
     * `sequence` is greater than another's, that means it was changed more recently.
     */
    public static final MetaExpression sequence = new MetaExpression("_sequence", "sequence", null);
}
