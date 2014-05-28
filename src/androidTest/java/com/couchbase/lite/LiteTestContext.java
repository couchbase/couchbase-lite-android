package com.couchbase.lite;

import com.couchbase.test.lite.*;

import java.io.File;

public class LiteTestContext extends LiteTestContextBase implements Context {

    private String subdir;

    public LiteTestContext(String subdir) {
        this.subdir = subdir;
    }

    public LiteTestContext() {
        this.subdir = "test";
    }

    @Override
    public File getFilesDir() {
        return new File(getRootDirectory(), subdir);
    }

    @Override
    public void setNetworkReachabilityManager(NetworkReachabilityManager networkReachabilityManager) {

    }

    @Override
    public NetworkReachabilityManager getNetworkReachabilityManager() {
        return new TestNetworkReachabilityManager();
    }

    class TestNetworkReachabilityManager extends NetworkReachabilityManager {
        @Override
        public void startListening() {

        }

        @Override
        public void stopListening() {

        }
    }

}
