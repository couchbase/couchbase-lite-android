package com.couchbase.lite.android;

import com.couchbase.lite.Context;
import com.couchbase.lite.NetworkReachabilityManager;

import java.io.File;

public class AndroidContext implements Context {

    private android.content.Context wrappedContext;
    private NetworkReachabilityManager networkReachabilityManager;

    public AndroidContext(android.content.Context wrappedContext) {
        this.wrappedContext = wrappedContext;
    }

    @Override
    public File getFilesDir() {
        return wrappedContext.getFilesDir();
    }

    @Override
    public void setNetworkReachabilityManager(NetworkReachabilityManager networkReachabilityManager) {
        this.networkReachabilityManager = networkReachabilityManager;
    }

    @Override
    public NetworkReachabilityManager getNetworkReachabilityManager() {
        if (networkReachabilityManager == null) {
            networkReachabilityManager = new AndroidNetworkReachabilityManager(this);
        }
        return networkReachabilityManager;
    }

    public android.content.Context getWrappedContext() {
        return wrappedContext;
    }

}
