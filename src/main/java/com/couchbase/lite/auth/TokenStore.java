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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.URL;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
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
public class TokenStore implements ITokenStore {

    public static final String TAG = Log.TAG_SYNC;

    // https://developer.android.com/training/articles/keystore.html#SupportedCiphers
    private static final String CIPHER_ALGORITHM = "RSA/ECB/PKCS1Padding";

    // https://developer.android.com/reference/java/security/KeyPairGenerator.html
    private static final String KEYPAIRGEN_ALGORITHM = "RSA";

    private static final String kOIDCKeychainServiceName = "OpenID Connect";

    private static final String serviceName = "CouchbaseLite";
    private static final String alias = "CouchbaseLiteTokenStore";

    private static final boolean hasKeyStore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2; // API 18

    private Context context = null;

    private URL remoteURL;
    private String label;

    ////////////////////////////////////////////////////////////
    // Member variables
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    // Constructors
    ////////////////////////////////////////////////////////////

    public TokenStore(Context context) {
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
        String encryptedStr = prefs.getString(key, null);
        if (encryptedStr == null)
            return null;

        return decrypt(encryptedStr);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public boolean saveTokens(URL remoteURL, Map<String, String> tokens) {
        if (!hasKeyStore)
            return false;

        String encryptedStr = encrypt(tokens);
        if (encryptedStr == null)
            return false;

        SharedPreferences prefs = context.getSharedPreferences(serviceName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String key = getKey(remoteURL);
        editor.putString(key, encryptedStr);
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
        editor.remove(key);
        return editor.commit();
    }

    ////////////////////////////////////////////////////////////
    // protected/private methods
    ////////////////////////////////////////////////////////////

    /*package*/  String getKey(URL remoteURL) {
        String account = remoteURL.toExternalForm();
        String label = String.format(Locale.ENGLISH, "%s OpenID Connect tokens", remoteURL.getHost());
        return String.format(Locale.ENGLISH, "%s%s", label, account);
    }

    /*package*/ String encrypt(Map<String, String> map) {
        byte[] bytes = toByteArray(map);
        if (bytes == null)
            return null;
        return _encrypt(bytes);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    /*package*/ String _encrypt(byte[] bytes) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(alias, null);
            RSAPublicKey publicKey = (RSAPublicKey) privateKeyEntry.getCertificate().getPublicKey();
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher);
            cipherOutputStream.write(bytes);
            cipherOutputStream.close();
            byte[] encrypted = outputStream.toByteArray();
            return Base64.encodeToString(encrypted, Base64.DEFAULT);
        } catch (Exception ex) {
            Log.e(TAG, "Unable to open KeyStore", ex);
            return null;
        }
    }

    Map decrypt(String strValue) {
        try {
            return fromByteArray(_decrypt(strValue));
        } catch (IOException e) {
            Log.e(TAG, "Unable to decrypt: value=<%s>", e, strValue);
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    /*package*/ byte[] _decrypt(String base64Str) {
        try {
            byte[] encrypted = Base64.decode(base64Str, Base64.DEFAULT);

            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(alias, null);
            RSAPrivateKey privateKey = (RSAPrivateKey) privateKeyEntry.getPrivateKey();

            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);

            ByteArrayInputStream inputStream = new ByteArrayInputStream(encrypted);
            CipherInputStream cipherInputStream = new CipherInputStream(inputStream, cipher);
            ArrayList<Byte> values = new ArrayList<>();
            int nextByte;
            while ((nextByte = cipherInputStream.read()) != -1) {
                values.add((byte) nextByte);
            }
            byte[] bytes = new byte[values.size()];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = values.get(i).byteValue();
            }

            cipherInputStream.close();
            return bytes;


        } catch (Exception ex) {
            Log.e(TAG, "Unable to open KeyStore", ex);
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

    private byte[] toByteArray(Map obj) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try {
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                try {
                    oos.writeObject(obj);
                    return bos.toByteArray();
                } finally {
                    oos.close();
                }
            } finally {
                bos.close();
            }
        } catch (IOException ioe) {
            Log.e(TAG, "Error in toByteArray()", ioe);
        }
        return null;
    }

    private Map fromByteArray(byte[] bytes) throws IOException {
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
            try {
                ObjectInputStream ois = new ObjectInputStream(bis);
                try {
                    return (Map) ois.readObject();
                } finally {
                    ois.close();
                }
            } finally {
                bis.close();
            }
        } catch (IOException ioe) {
            Log.e(TAG, "Error in fromByteArray()", ioe);
        } catch (ClassNotFoundException cnfe) {
            Log.e(TAG, "Error in fromByteArray()", cnfe);
        }
        return null;
    }
}
