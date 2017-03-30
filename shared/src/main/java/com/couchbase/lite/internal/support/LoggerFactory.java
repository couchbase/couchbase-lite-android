/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite.internal.support;

public class LoggerFactory {
    static String classname = "com.couchbase.lite.internal.support.LoggerImpl";

    public static Logger createLogger() {
        try {
            //Log.v(Database.TAG, "Loading logger: %s", classname);
            Class clazz = Class.forName(classname);
            Logger logger = (Logger) clazz.newInstance();
            return logger;
        } catch (Exception e) {
            System.err.println("Failed to load the logger: " + classname + ". Use SystemLogger.");
            return new SystemLogger();
        }
    }
}
