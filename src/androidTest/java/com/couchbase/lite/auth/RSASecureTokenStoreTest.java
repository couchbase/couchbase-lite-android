package com.couchbase.lite.auth;

import android.os.Build;

import com.couchbase.lite.LiteTestCase;

import java.net.URL;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

/**
 * Created by hideki on 6/23/16.
 */
public class RSASecureTokenStoreTest extends LiteTestCase {

    private static boolean supportedAndroidAPI() {
        // 18 <= API <= 22
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.M;
    }

    public void testDummy() {
        if(!supportedAndroidAPI())
            return;
    }

    public void testAESEncryptDecryptData(){
        if(!supportedAndroidAPI())
            return;

        String input = "Hello World!";

        SecretKey secretKey = RSASecureTokenStore.generateSecretKey();
        assertNotNull(secretKey);

        byte[][] encryptedData = RSASecureTokenStore.encryptDataByAES(secretKey, input.getBytes());
        assertNotNull(encryptedData);
        assertEquals(2, encryptedData.length);

        byte[] decryptedData = RSASecureTokenStore.decryptDataByAES(secretKey, encryptedData[0], encryptedData[1]);
        assertNotNull(decryptedData);
        String output = new String(decryptedData);
        assertEquals(input, output);
    }

    public void testRSAEncryptDecryptData()throws Exception{
        if(!supportedAndroidAPI())
            return;

        String input = "Hello World!";

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair keyPair = kpg.genKeyPair();
        assertNotNull(keyPair);

        byte[] encryptedData = RSASecureTokenStore.encryptDataByRSA((RSAPublicKey)keyPair.getPublic(), input.getBytes());
        assertNotNull(encryptedData);

        byte[] decryptedData = RSASecureTokenStore.decryptDataByRSA((RSAPrivateKey)keyPair.getPrivate(), encryptedData);
        assertNotNull(decryptedData);
        String output = new String(decryptedData);
        assertEquals(input, output);
    }

    public void testEncryptDecrypt() {
        if(!supportedAndroidAPI())
            return;

        Map<String, String> input = new HashMap<>();
        input.put("key", "value");
        input.put("hello", "world");

        RSASecureTokenStore tokenStore = new RSASecureTokenStore(getContext());
        byte[][] encryptedData=  tokenStore.encrypt(input);
        assertNotNull(encryptedData);
        assertEquals(3, encryptedData.length);

        Map<String, String> output = tokenStore.decrypt(encryptedData[0], encryptedData[1], encryptedData[2]);
        assertNotNull(output);
        assertEquals(input, output);
    }
    public void testSaveLoadTokens() throws Exception {
        if(!supportedAndroidAPI())
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
        if(!supportedAndroidAPI())
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
}
