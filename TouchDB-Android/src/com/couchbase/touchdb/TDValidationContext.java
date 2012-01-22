package com.couchbase.touchdb;

public interface TDValidationContext {

    TDRevision getCurrentRevision();

    TDStatus getErrorType();
    void setErrorType(TDStatus status);

    String getErrorMessage();
    void setErrorMessage(String message);

}
