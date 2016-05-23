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
package com.couchbase.test.lite;

import android.test.*;

import com.couchbase.lite.Context;
import com.couchbase.lite.android.AndroidContext;

import java.io.File;

/**
 * This class is changed depending on if we are in Android or Java. Amongst other things it lets us
 * change the base class for tests between TestCase (Java) and AndroidTestCase (Android).
 */
public class LiteTestCaseBase extends AndroidTestCase implements TestContextFactory {
    public Context getTestContext(String dirName) {
        File testDir = new File(getContext().getFilesDir(), dirName);
        if (!testDir.exists())
            testDir.mkdirs();
        return new AndroidTestContext(testDir, getContext());
    }

    private class AndroidTestContext extends AndroidContext {
        private File testDir;

        public AndroidTestContext(File testDir, android.content.Context wrappedContext) {
            super(wrappedContext);
            this.testDir = testDir;
        }

        @Override
        public File getFilesDir() {
            return testDir;
        }
    }
}
