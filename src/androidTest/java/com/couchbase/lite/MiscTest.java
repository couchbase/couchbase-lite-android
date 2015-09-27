/**
 *
 * Copyright (c) 2015 Couchbase, Inc All rights reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 *
 */

package com.couchbase.lite;

import com.couchbase.lite.support.security.SymmetricKey;
import com.couchbase.lite.util.ArrayUtils;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import java.security.SecureRandom;
import java.util.Arrays;

public class MiscTest extends LiteTestCase {
    public void testUnquoteString() {
        String testString = "attachment; filename=\"attach\"";
        String expected = "attachment; filename=attach";
        String result = com.couchbase.lite.Misc.unquoteString(testString);
        Assert.assertEquals(expected, result);
    }

    public void testSymmetricKey() throws Exception {
        long start = System.currentTimeMillis();
        // Generate a key from a password:
        String password = "letmein123456";
        byte[] salt = "SaltyMcNaCl".getBytes();

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
        start = System.currentTimeMillis();
        SymmetricKey.Encryptor encryptor = key.createEncryptor();
        byte[] incrementalClearText = new byte[0];
        byte[] incrementalCiphertext = new byte[0];
        for (int i = 0; i < 55; i++) {
            byte[] data = new SecureRandom().generateSeed(555);
            byte[] cipherData = encryptor.encrypt(data);
            incrementalClearText = ArrayUtils.concat(incrementalClearText, data);
            incrementalCiphertext = ArrayUtils.concat(incrementalCiphertext, cipherData);
        }
        incrementalCiphertext = ArrayUtils.concat(incrementalCiphertext, encryptor.encrypt(null));
        decrypted = key.decryptData(incrementalCiphertext);
        Assert.assertTrue(Arrays.equals(incrementalClearText, decrypted));
        end = System.currentTimeMillis();
        Log.i(TAG, "Finished incremental encryption test in " + (end - start) + " msec.");
    }
}
