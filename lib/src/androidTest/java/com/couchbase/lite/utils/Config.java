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
package com.couchbase.lite.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import com.couchbase.lite.internal.utils.Preconditions;


public class Config {
    public static final String TEST_PROPERTIES_FILE = "test.properties";

    private final Properties props = new Properties();

    public Config() { }

    public Config(InputStream in) throws IOException {
        Preconditions.checkArgNotNull(in, "inputStream");
        try { props.load(new InputStreamReader(in, StandardCharsets.UTF_8)); }
        finally { in.close(); }
    }

    public boolean deleteDatabaseInTearDown() {
        return Boolean.parseBoolean(props.getProperty("deleteDatabaseInTearDown"));
    }

    public String remoteHost() { return props.getProperty("remoteHost"); }

    public int remotePort() { return Integer.parseInt(props.getProperty("remotePort")); }

    public String remoteDB() { return props.getProperty("remoteDB"); }

    public int secureRemotePort() { return Integer.parseInt(props.getProperty("secureRemotePort")); }
}
