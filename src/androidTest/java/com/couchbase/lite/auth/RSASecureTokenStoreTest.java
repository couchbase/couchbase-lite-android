//
// Copyright (c) 2016 Couchbase, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.auth;

import com.couchbase.lite.LiteTestCase;

/**
 * NOTE: This test only contains unit test for Android. All test causes compilation error for Java.
 * Currently all tests are commented out. To test, please enable commented out codes manually.
 */
public class RSASecureTokenStoreTest extends LiteTestCase {

    /**
     * Please enable to test
     * private static boolean supportedAndroidAPI() {
     * // 18 <= API <= 22
     * return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
     * && Build.VERSION.SDK_INT < Build.VERSION_CODES.M;
     * }
     */

    public void testDummy() {
    }

    /* Please enable to test
    public void testRSAEncryptDecryptData() throws Exception {
        if (!supportedAndroidAPI())
            return;

        String input = "Hello World!";

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.genKeyPair();
        assertNotNull(keyPair);

        byte[] encryptedData = RSASecureTokenStore.encryptDataByRSA((RSAPublicKey) keyPair.getPublic(), input.getBytes());
        assertNotNull(encryptedData);

        byte[] decryptedData = RSASecureTokenStore.decryptDataByRSA((RSAPrivateKey) keyPair.getPrivate(), encryptedData);
        assertNotNull(decryptedData);
        String output = new String(decryptedData);
        assertEquals(input, output);
    }

    public void testSaveLoadTokens() throws Exception {
        if (!supportedAndroidAPI())
            return;

        RSASecureTokenStore tokenStore = new RSASecureTokenStore(getContext());
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
        assertTrue(tokenStore.saveTokens(remoteURL, input));
        Map output = tokenStore.loadTokens(remoteURL);
        assertNotNull(output);
        assertEquals(input, output);
    }

    public void testDeleteTokens() throws Exception {
        if (!supportedAndroidAPI())
            return;

        RSASecureTokenStore tokenStore = new RSASecureTokenStore(getContext());

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
    */
}
