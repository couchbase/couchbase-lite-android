package com.couchbase.lite.auth;


import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.util.Log;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by hideki on 6/22/16.
 */
public class SecureTokenStoreTest extends LiteTestCaseWithDB {
    private SecureTokenStore tokenStore = null;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        tokenStore = new SecureTokenStore(getContext());
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        tokenStore = null;
    }

    public void testEncryptDecrypt() {
        String input = "Hello World!";
        Log.e(TAG, "testEncryptDecrypt() input=<%s>", input);
        String encrypted = tokenStore._encrypt(input.getBytes());
        Log.e(TAG, "testEncryptDecrypt() encrypted=<%s>", encrypted);
        String output = new String(tokenStore._decrypt(encrypted));
        Log.e(TAG, "testEncryptDecrypt() output=<%s>", output);
        assertEquals(input, output);
    }

    public void testEncryptDecrypt2() {
        Map<String, String> input = new HashMap<>();
        input.put("key", "value");
        input.put("hello", "world");

        Log.e(TAG, "testEncryptDecrypt() input=<%s>", input);
        String encrypted = tokenStore.encrypt(input);
        Log.e(TAG, "testEncryptDecrypt() encrypted=<%s>", encrypted);
        Map output = tokenStore.decrypt(encrypted);
        Log.e(TAG, "testEncryptDecrypt() output=<%s>", output);
        assertEquals(input, output);
    }

    public void testSaveLoadTokens() throws Exception {
        URL remoteURL = new URL("http://10.0.0.1:1111/db");
        Map<String, String> input = new HashMap<>();
        input.put("key", "value");
        input.put("hello", "world");
        assertTrue(tokenStore.saveTokens(remoteURL, input));
        Map output = tokenStore.loadTokens(remoteURL);
        assertNotNull(output);
        assertEquals(input, output);
    }

    public void testDeleteTokens() throws Exception {
        URL remoteURL = new URL("http://10.0.0.1:1111/db");

        Map<String, String> input = new HashMap<>();
        input.put("key", "value");
        input.put("hello", "world");
        assertTrue(tokenStore.saveTokens(remoteURL, input));

        Map output = tokenStore.loadTokens(remoteURL);
        assertNotNull(output);
        assertEquals(input, output);

        assertTrue(tokenStore.deleteTokens(remoteURL));

        assertNull(tokenStore.loadTokens(remoteURL));
    }
}
