package com.couchbase.touchdb;

public interface TDValidationBlock {

    boolean validate(TDRevision newRevision, TDValidationContext context);

}
