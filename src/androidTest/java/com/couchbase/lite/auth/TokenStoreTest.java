package com.couchbase.lite.auth;


import com.couchbase.lite.LiteTestCaseWithDB;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by hideki on 6/22/16.
 */
public class TokenStoreTest extends LiteTestCaseWithDB {
    private TokenStore tokenStore = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tokenStore = TokenStoreFactory.build(getTestContext("db"));
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        tokenStore = null;
    }

    public void testSaveLoadTokens() throws Exception {
        URL remoteURL = new URL("http://10.0.0.1:1111/db");
        Map<String, String> input = new HashMap<>();
        input.put("key", "value");
        input.put("hello", "world");
        input.put("data1", "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");
        input.put("data2", "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");
        input.put("data3", "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");
        input.put("data4", "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");
        input.put("data5", "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");
        input.put("data6", "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");
        input.put("data7", "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");
        input.put("data8", "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");
        input.put("data9", "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");
        input.put("data0", "1234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890");
        assertTrue(tokenStore.saveTokens(remoteURL, null, input));
        Map output = tokenStore.loadTokens(remoteURL, null);
        assertNotNull(output);
        assertEquals(input, output);
    }

    public void testDeleteTokens() throws Exception {
        URL remoteURL = new URL("http://10.0.0.1:1111/db");

        Map<String, String> input = new HashMap<>();
        input.put("key", "value");
        input.put("hello", "world");
        assertTrue(tokenStore.saveTokens(remoteURL, null, input));

        Map output = tokenStore.loadTokens(remoteURL, null);
        assertNotNull(output);
        assertEquals(input, output);

        assertTrue(tokenStore.deleteTokens(remoteURL, null));

        assertNull(tokenStore.loadTokens(remoteURL, null));
    }
}
