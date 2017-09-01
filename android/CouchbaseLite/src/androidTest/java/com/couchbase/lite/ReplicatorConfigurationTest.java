package com.couchbase.lite;


import org.junit.Test;

public class ReplicatorConfigurationTest extends BaseTest {
    public static final String TAG = QueryTest.class.getSimpleName();

    @Test
    public void testUserAgent(){
        Log.e(TAG, ReplicatorConfiguration.getUserAgent());
    }
}
