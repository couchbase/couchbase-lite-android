package com.couchbase.touchdb.support;


public interface TDRemoteRequestCompletionBlock {

    public void onCompletion(Object result, Throwable e);

}
