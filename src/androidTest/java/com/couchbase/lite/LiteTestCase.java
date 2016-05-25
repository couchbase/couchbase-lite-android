/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite;

import com.couchbase.lite.util.Log;
import com.couchbase.test.lite.LiteTestCaseBase;

/**
 * Created by hideki on 9/24/15.
 */
public class LiteTestCase extends LiteTestCaseBase {
    public static final String TAG = "LiteTestCase";

    protected  boolean isAndriod() {
        return (System.getProperty("java.vm.name").equalsIgnoreCase("Dalvik"));
    }

    @Override
    public void runBare() throws Throwable {
        long start = System.currentTimeMillis();

        super.runBare();

        long end = System.currentTimeMillis();
        String name = getName();
        long duration= (end - start)/1000;
        Log.e(TAG, "DURATION: %s: %d sec%s", name, duration, duration >= 3 ? " - [SLOW]" : "");
    }
}
