package com.couchbase.cblite.support;


public interface CBLRemoteRequestCompletionBlock {

    public void onCompletion(Object result, Throwable e);

}
