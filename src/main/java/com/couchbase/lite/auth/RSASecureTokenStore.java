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

import com.couchbase.lite.support.security.SymmetricKey;
import com.couchbase.lite.support.security.SymmetricKeyException;
import com.couchbase.lite.util.Base64;
import com.couchbase.lite.util.ConversionUtils;
import com.couchbase.lite.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URL;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Calendar;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
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

    // https://developer.android.com/reference/java/security/KeyPairGenerator.html
    private static final String KEYPAIRGEN_ALGORITHM = "RSA";

    private static final String serviceName = "CouchbaseLite";
    private static final String alias = "CouchbaseLiteTokenStoreRSA";

    private static final boolean hasKeyStore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2; // API 18

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
    public Map<String, String> loadTokens(URL remoteURL, String localUUID) throws Exception {
        if (!hasKeyStore)
            return null;
        SharedPreferences prefs = context.getSharedPreferences(serviceName, Context.MODE_PRIVATE);
        String key = getKey(remoteURL, localUUID);
        if (!prefs.contains(key + "_key")) return null;
        if (!prefs.contains(key + "_data")) return null;
        byte[] secretKey = Base64.decode(prefs.getString(key + "_key", null), Base64.DEFAULT);
        byte[] data = Base64.decode(prefs.getString(key + "_data", null), Base64.DEFAULT);
        return decrypt(secretKey, data);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public boolean saveTokens(URL remoteURL, String localUUID, Map<String, String> tokens) {
        if (!hasKeyStore)
            return false;

        byte[][] encrypted = encrypt(tokens);
        if (encrypted == null || encrypted.length != 2)
            return false;

        SharedPreferences prefs = context.getSharedPreferences(serviceName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String key = getKey(remoteURL, localUUID);
        editor.putString(key + "_key", Base64.encodeToString(encrypted[0], Base64.DEFAULT));
        editor.putString(key + "_data", Base64.encodeToString(encrypted[1], Base64.DEFAULT));
        return editor.commit();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public boolean deleteTokens(URL remoteURL, String localUUID) {
        if (!hasKeyStore)
            return false;

        SharedPreferences prefs = context.getSharedPreferences(serviceName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String key = getKey(remoteURL, localUUID);
        editor.remove(key + "_key");
        editor.remove(key + "_data");
        return editor.commit();
    }

    ////////////////////////////////////////////////////////////
    // protected/private methods
    ////////////////////////////////////////////////////////////

    String getKey(URL remoteURL, String localUUID) {
        String service = remoteURL.toExternalForm();
        String label = String.format(Locale.ENGLISH, "%s OpenID Connect tokens", remoteURL.getHost());
        if (localUUID == null)
            return String.format(Locale.ENGLISH, "%s%s%s", alias, label, service);
        else
            return String.format(Locale.ENGLISH, "%s%s%s%s", alias, label, service, localUUID);
    }

    byte[][] encrypt(Map<String, String> map) {
        // convert from Map to byte[]
        byte[] bytes = ConversionUtils.toByteArray(map);
        if (bytes == null)
            return null;

        try {
            // initialize Symmetric Key
            SymmetricKey symmetricKey = new SymmetricKey();

            // encrypt symmetricKey
            byte[] encryptedKey = encryptDataByRSA(getRSAPublicKeyFromKeyStore(), symmetricKey.getKey());
            if (encryptedKey == null)
                return null;

            // return encrypted symmetric key, encrypted datav
            byte[][] data = new byte[2][];
            data[0] = encryptedKey;
            data[1] = symmetricKey.encryptData(bytes);
            return data;
        } catch (SymmetricKeyException ex) {
            Log.e(TAG, "Error in encryption", ex);
            return null;
        }
    }

    Map decrypt(byte[] encryptedKey, byte[] encryptedData) {
        try {
            // decrypt symmetric Key by RSA, and initialize symmetric key
            SymmetricKey symmetricKey = new SymmetricKey(decryptDataByRSA(getRSAPrivateKeyFromKeyStore(), encryptedKey));
            if (symmetricKey == null)
                return null;

            // decrypt data
            byte[] bytes = symmetricKey.decryptData(encryptedData);
            if (bytes == null)
                return null;

            return ConversionUtils.fromByteArray(bytes);
        } catch (Exception ex) {
            Log.e(TAG, "Error in decryption", ex);
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
