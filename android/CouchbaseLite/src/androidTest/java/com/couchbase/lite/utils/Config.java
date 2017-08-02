package com.couchbase.lite.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Config extends java.util.Properties {
    public static final String TEST_PROPERTIES_FILE = "test.properties";

    public Config(InputStream in) throws IOException {
        try {
            load(new InputStreamReader(in, "UTF-8"));
        } finally {
            in.close();
        }
    }

    public boolean replicatorTestsEnabled() {
        return Boolean.parseBoolean(getProperty("replicatorTestsEnabled"));
    }

    public boolean concurrentTestsEnabled() {
        return Boolean.parseBoolean(getProperty("concurrentTestsEnabled"));
    }

    public String remoteHost() {
        return getProperty("remoteHost");
    }

    public int remotePort() {
        return Integer.parseInt(getProperty("remotePort"));
    }

    public String remoteDB() {
        return getProperty("remoteDB");
    }
}
