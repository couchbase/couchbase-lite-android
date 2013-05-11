package com.couchbase.cblite.support;


public interface TDRemoteRequestCompletionBlock {

    public void onCompletion(Object result, Throwable e);

}
