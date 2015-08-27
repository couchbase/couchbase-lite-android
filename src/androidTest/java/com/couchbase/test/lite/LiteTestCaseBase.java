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
