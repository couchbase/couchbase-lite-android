package com.couchbase.lite;

import com.couchbase.lite.support.security.SymmetricKey;
import com.couchbase.lite.util.ArrayUtils;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import java.nio.ByteBuffer;
import java.security.AlgorithmParameters;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class MiscTest extends LiteTestCase {
    public void testUnquoteString() {
        String testString = "attachment; filename=\"attach\"";
        String expected = "attachment; filename=attach";
        String result = com.couchbase.lite.Misc.unquoteString(testString);
        Assert.assertEquals(expected, result);
    }

    public void testSymmetricKey() throws Exception {
        if (!isAndriod()) return;

        // Generate a key from a password:
        String password = "letmein123456";
        byte[] salt = "SaltyMcNaCl".getBytes();

        long start = System.currentTimeMillis();
        SymmetricKey key = new SymmetricKey(password, salt, 1024);
        long end = System.currentTimeMillis();
        Log.i(TAG, "Finished getting a symmetric key in " + (end - start) + " msec.");
        byte[] keyData = key.getKey();
        Log.i(TAG, "Key = " + key);

        // Encrypt using the key:
        byte[] clearText = "This is the clear text.".getBytes();
        byte[] ciphertext = key.encryptData(clearText);
        Log.i(TAG, "Encrypted = " + new String(ciphertext));
        Assert.assertNotNull(ciphertext);

        // Decrypt using the key:
        byte[] decrypted = key.decryptData(ciphertext);
        Log.i(TAG, "Decrypted String = " + new String(decrypted));
        Assert.assertTrue(Arrays.equals(clearText, decrypted));

        // Incremental encryption:
        SymmetricKey.Encryptor encryptor = key.createEncryptor();
        byte[] incrementalClearText = new byte[0];
        byte[] incrementalCiphertext = new byte[0];
        for (int i = 0; i < 100; i++) {
            byte[] data = new SecureRandom().generateSeed(5555);
            byte[] cipherData = encryptor.encrypt(data);
            incrementalClearText = ArrayUtils.concat(incrementalClearText, data);
            incrementalCiphertext = ArrayUtils.concat(incrementalCiphertext, cipherData);
        }
        incrementalCiphertext = ArrayUtils.concat(incrementalCiphertext, encryptor.encrypt(null));
        decrypted = key.decryptData(incrementalCiphertext);
        Assert.assertTrue(Arrays.equals(incrementalClearText, decrypted));
    }
}
