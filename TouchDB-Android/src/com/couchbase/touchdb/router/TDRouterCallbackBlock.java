package com.couchbase.touchdb.router;

public interface TDRouterCallbackBlock {

    void onResponseReady();

    void onDataAvailable(byte[] data);

    void onFinish();

}
