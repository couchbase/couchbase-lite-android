package com.couchbase.cblite;

/**
 * Validation block, used to approve revisions being added to the database.
 */
public interface CBLValidationBlock {

    boolean validate(CBLRevision newRevision, CBLValidationContext context);

}
