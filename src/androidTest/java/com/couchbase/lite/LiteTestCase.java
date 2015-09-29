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
