package com.couchbase.touchdb;

/**
 * Context passed into a TDValidationBlock.
 */
public interface TDValidationContext {

    /**
     * The contents of the current revision of the document, or nil if this is a new document.
     */
    TDRevision getCurrentRevision();

    /**
     * The type of HTTP status to report, if the validate block returns NO.
     * The default value is 403 ("Forbidden").
     */
    TDStatus getErrorType();
    void setErrorType(TDStatus status);

    /**
     * The error message to return in the HTTP response, if the validate block returns NO.
     * The default value is "invalid document".
     */
    String getErrorMessage();
    void setErrorMessage(String message);

}
