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

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;

import com.couchbase.lite.util.Base64;
import com.couchbase.lite.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URL;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;


/**
 * Created by hideki on 6/22/16.
 */
public class RSASecureTokenStore implements TokenStore {
    ////////////////////////////////////////////////////////////
    // Constant variables
    ////////////////////////////////////////////////////////////
    private static final String TAG = Log.TAG_SYNC;

    // https://developer.android.com/training/articles/keystore.html#SupportedCiphers
    private static final String CIPHER_ALGORITHM_RSA = "RSA/ECB/PKCS1Padding";
    private static final String CIPHER_ALGORITHM_AES = "AES/CBC/PKCS7Padding";

    // https://developer.android.com/reference/java/security/KeyPairGenerator.html
    private static final String KEYPAIRGEN_ALGORITHM = "RSA";

    private static final String serviceName = "CouchbaseLite";
    private static final String alias = "CouchbaseLiteTokenStoreRSA";

    private static final boolean hasKeyStore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2; // API 18

    private static final String SEED = "2UlWi3DbJVfRMlqp2CGBFlrql15OWawaqi0KR+K9Qkc=";

    ////////////////////////////////////////////////////////////
    // Member variables
    ////////////////////////////////////////////////////////////
    private Context context = null;

    ////////////////////////////////////////////////////////////
    // Constructors
    ////////////////////////////////////////////////////////////
    public RSASecureTokenStore(Context context) {
        this.context = context;
        initializePrivateKey(context);
    }

    ////////////////////////////////////////////////////////////
    // Implementation of TokenStore
    ////////////////////////////////////////////////////////////

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public Map<String, String> loadTokens(URL remoteURL) throws Exception {
        if (!hasKeyStore)
            return null;
        SharedPreferences prefs = context.getSharedPreferences(serviceName, Context.MODE_PRIVATE);
        String key = getKey(remoteURL);
        if (!prefs.contains(key + "_key")) return null;
        if (!prefs.contains(key + "_data")) return null;
        if (!prefs.contains(key + "_iv")) return null;
        byte[] secretKey = Base64.decode(prefs.getString(key + "_key", null), Base64.DEFAULT);
        byte[] data = Base64.decode(prefs.getString(key + "_data", null), Base64.DEFAULT);
        byte[] iv = Base64.decode(prefs.getString(key + "_iv", null), Base64.DEFAULT);
        return decrypt(secretKey, data, iv);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public boolean saveTokens(URL remoteURL, Map<String, String> tokens) {
        if (!hasKeyStore)
            return false;

        byte[][] encrypted = encrypt(tokens);
        if (encrypted == null)
            return false;

        SharedPreferences prefs = context.getSharedPreferences(serviceName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String key = getKey(remoteURL);
        editor.putString(key + "_key", Base64.encodeToString(encrypted[0], Base64.DEFAULT));
        editor.putString(key + "_data", Base64.encodeToString(encrypted[1], Base64.DEFAULT));
        editor.putString(key + "_iv", Base64.encodeToString(encrypted[2], Base64.DEFAULT));
        return editor.commit();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public boolean deleteTokens(URL remoteURL) {
        if (!hasKeyStore)
            return false;

        SharedPreferences prefs = context.getSharedPreferences(serviceName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String key = getKey(remoteURL);
        editor.remove(key + "_key");
        editor.remove(key + "_data");
        editor.remove(key + "_iv");
        return editor.commit();
    }

    ////////////////////////////////////////////////////////////
    // protected/private methods
    ////////////////////////////////////////////////////////////

    String getKey(URL remoteURL) {
        String account = remoteURL.toExternalForm();
        String label = String.format(Locale.ENGLISH, "%s OpenID Connect tokens", remoteURL.getHost());
        return String.format(Locale.ENGLISH, "%s%s%s", alias, label, account);
    }

    byte[][] encrypt(Map<String, String> map) {
        byte[] bytes = Utils.toByteArray(map);
        if (bytes == null)
            return null;

        // generate SecretKey
        SecretKey secretKey = generateSecretKey();
        if (secretKey == null)
            return null;

        // encrypte data by secretKey
        byte[][] encryptedData = encryptDataByAES(secretKey, bytes);
        if (encryptedData == null || encryptedData.length != 2)
            return null;

        // encrypt secretkey
        byte[] encryptedKey = encryptDataByRSA(getRSAPublicKeyFromKeyStore(), secretKey.getEncoded());
        if (encryptedKey == null)
            return null;

        // return encrypted secretKey, encrypted data, and iv
        byte[][] data = new byte[3][];
        data[0] = encryptedKey;
        data[1] = encryptedData[0];
        data[2] = encryptedData[1];
        return data;
    }


    Map decrypt(byte[] encrtypedSecretKey, byte[] encryptedData, byte[] iv) {
        SecretKey secreteKey = new SecretKeySpec(decryptDataByRSA(getRSAPrivateKeyFromKeyStore(), encrtypedSecretKey), "AES");
        if (secreteKey == null)
            return null;

        byte[] bytes = decryptDataByAES(secreteKey, encryptedData, iv);
        if (bytes == null)
            return null;

        try {
            return Utils.fromByteArray(bytes);
        } catch (IOException e) {
            return null;
        }
    }

    // get RSA public key from KeyStore
    static RSAPublicKey getRSAPublicKeyFromKeyStore() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(alias, null);
            RSAPublicKey publicKey = (RSAPublicKey) privateKeyEntry.getCertificate().getPublicKey();
            return publicKey;
        } catch (Exception ex) {
            Log.e(TAG, "Unable to open KeyStore or to get RSA key", ex);
            return null;
        }
    }

    // get RSA private key from KeyStore
    static RSAPrivateKey getRSAPrivateKeyFromKeyStore() {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(alias, null);
            RSAPrivateKey privateKey = (RSAPrivateKey) privateKeyEntry.getPrivateKey();
            return privateKey;
        } catch (Exception ex) {
            Log.e(TAG, "Unable to open KeyStore or to get RSA key", ex);
            return null;
        }
    }

    // generate SecretKey
    static SecretKey generateSecretKey() {
        try {
            KeyGenerator kgen = KeyGenerator.getInstance("AES");
            SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
            sr.setSeed(SEED.getBytes());
            kgen.init(256, sr);
            return kgen.generateKey();
        } catch (Exception ex) {
            Log.e(TAG, "Unable to generate SecretKey", ex);
            return null;
        }
    }

    // encrypt data by RSA
    static byte[] encryptDataByRSA(RSAPublicKey publicKey, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM_RSA);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                CipherOutputStream cos = new CipherOutputStream(bos, cipher);
                try {
                    cos.write(data);
                } finally {
                    cos.close();
                }
                return bos.toByteArray();
            } finally {
                bos.close();
            }
        } catch (Exception ex) {
            Log.e(TAG, "Unable to open KeyStore", ex);
            return null;
        }
    }

    // decrypt data by RSA
    static byte[] decryptDataByRSA(RSAPrivateKey privateKey, byte[] encryptedData) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(2048);
            try {
                Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM_RSA);
                cipher.init(Cipher.DECRYPT_MODE, privateKey);
                ByteArrayInputStream bis = new ByteArrayInputStream(encryptedData);
                try {
                    CipherInputStream cis = new CipherInputStream(bis, cipher);
                    try {
                        byte[] read = new byte[512]; // Your buffer size.
                        for (int i; (i = cis.read(read)) != -1; )
                            bos.write(read, 0, i);
                    } finally {
                        cis.close();
                    }
                } finally {
                    bis.close();
                }
                return bos.toByteArray();
            } finally {
                bos.close();
            }
        } catch (Exception ex) {
            Log.e(TAG, "Unable to decrypt data", ex);
            return null;
        }
    }

    // encrypt data by SecretKey with AES
    static byte[][] encryptDataByAES(SecretKey secretKey, byte[] data) {
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM_AES);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                CipherOutputStream cos = new CipherOutputStream(bos, cipher);
                try {
                    cos.write(data);
                } finally {
                    cos.close();
                }
                byte[][] encryptedData = new byte[2][];
                encryptedData[0] = bos.toByteArray();
                encryptedData[1] = cipher.getIV();
                return encryptedData;
            } finally {
                bos.close();
            }
        } catch (Exception ex) {
            Log.e(TAG, "Unable to open KeyStore", ex);
            return null;
        }
    }

    // decrypt data by SecretKey with AES
    static byte[] decryptDataByAES(SecretKey secretKey, byte[] encryptedData, byte[] iv) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(2048);
            try {
                Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM_AES);
                cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));
                ByteArrayInputStream bis = new ByteArrayInputStream(encryptedData);
                try {
                    CipherInputStream cis = new CipherInputStream(bis, cipher);
                    try {
                        byte[] read = new byte[512]; // Your buffer size.
                        for (int i; (i = cis.read(read)) != -1; )
                            bos.write(read, 0, i);
                    } finally {
                        cis.close();
                    }
                } finally {
                    bis.close();
                }
                return bos.toByteArray();
            } finally {
                bos.close();
            }
        } catch (Exception ex) {
            Log.e(TAG, "Unable to decrypt data", ex);
            return null;
        }
    }


    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void initializePrivateKey(Context context) {
        if (!hasKeyStore)
            return;

        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(alias))
                return;
        } catch (Exception ex) {
            Log.e(TAG, "Unable to open KeyStore", ex);
            return;
        }

        // Create the keys if necessary
        try {
            // https://developer.android.com/reference/android/security/KeyPairGeneratorSpec.Builder.html
            Calendar start = Calendar.getInstance();
            Calendar end = Calendar.getInstance();
            end.add(Calendar.YEAR, 1);
            KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                    .setAlias(alias)
                    .setSubject(new X500Principal("CN=" + alias))
                    .setSerialNumber(BigInteger.valueOf(1337))
                    .setStartDate(start.getTime())
                    .setEndDate(end.getTime())
                    .build();
            KeyPairGenerator generator = KeyPairGenerator.getInstance(KEYPAIRGEN_ALGORITHM, "AndroidKeyStore");
            generator.initialize(spec);
            KeyPair keyPair = generator.generateKeyPair();
        } catch (Exception ex) {
            Log.e(TAG, "Unable to create new key", ex);
            return;
        }
    }
}
