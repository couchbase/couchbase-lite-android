package com.couchbase.cblite;

/**
 * Context passed into a CBLValidationBlock.
 */
public interface CBLValidationContext {

    /**
     * The contents of the current revision of the document, or nil if this is a new document.
     */
    CBLRevision getCurrentRevision();

    /**
     * The type of HTTP status to report, if the validate block returns NO.
     * The default value is 403 ("Forbidden").
     */
    CBLStatus getErrorType();
    void setErrorType(CBLStatus status);

    /**
     * The error message to return in the HTTP response, if the validate block returns NO.
     * The default value is "invalid document".
     */
    String getErrorMessage();
    void setErrorMessage(String message);

}
