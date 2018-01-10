package com.couchbase.lite;


import com.couchbase.lite.internal.support.Log;

import org.junit.Test;

public class ReplicatorConfigurationTest extends BaseTest {
    public static final String TAG = QueryTest.class.getSimpleName();

    @Test
    public void testUserAgent() {
        Log.e(TAG, ReplicatorConfiguration.getUserAgent());
    }
}
