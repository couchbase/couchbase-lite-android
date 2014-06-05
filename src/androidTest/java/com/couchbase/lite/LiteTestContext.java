package com.couchbase.lite;

import com.couchbase.test.lite.*;
import org.apache.commons.io.*;

import java.io.*;

public class LiteTestContext extends LiteTestContextBase implements Context {
    private File filesDir;

    public LiteTestContext(String subdir, boolean deleteSubdirectory) {
        filesDir = new File(getRootDirectory(), subdir);

        if (deleteSubdirectory) {
            try {
                FileUtils.deleteDirectory(filesDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (filesDir.exists() == false && filesDir.mkdir() == false) {
            throw new RuntimeException("Couldn't create directory " + filesDir.getAbsolutePath());
        }
    }

    public LiteTestContext(String subdir) {
        this(subdir, true);
    }

    public LiteTestContext() {
        this(true);
    }

    public LiteTestContext(boolean deleteSubdirectory) {
        this("test", deleteSubdirectory);
    }

    @Override
    public File getFilesDir() {
        return filesDir;
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
