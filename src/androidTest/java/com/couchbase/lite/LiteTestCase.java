package com.couchbase.lite;

import com.couchbase.test.lite.LiteTestCaseBase;

/**
 * Created by hideki on 9/24/15.
 */
public class LiteTestCase extends LiteTestCaseBase {
    public static final String TAG = "LiteTestCase";

    protected  boolean isAndriod() {
        return (System.getProperty("java.vm.name").equalsIgnoreCase("Dalvik"));
    }
}
