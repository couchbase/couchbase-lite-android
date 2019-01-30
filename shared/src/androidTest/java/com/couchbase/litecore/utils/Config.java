//
// Config.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.litecore.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Config extends java.util.Properties {
    public static final String TEST_PROPERTIES_FILE = "test.properties";
    public static final String EE_TEST_PROPERTIES_FILE = "ee_test.properties";

    public Config() {
    }

    public Config(InputStream in) throws IOException {
        try {
            load(new InputStreamReader(in, "UTF-8"));
        } finally {
            in.close();
        }
    }

    public boolean eeFeaturesTestsEnabled() {
        return Boolean.parseBoolean(getProperty("eeFeaturesTestsEnabled"));
    }

    public boolean replicatorTestsEnabled() {
        return Boolean.parseBoolean(getProperty("replicatorTestsEnabled"));
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
