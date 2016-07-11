package com.couchbase.lite.auth;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import com.couchbase.lite.util.Base64;
import com.couchbase.lite.util.ConversionUtils;
import com.couchbase.lite.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URL;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

/**
 * Created by hideki on 6/23/16.
 */
public class AESSecureTokenStore implements TokenStore {
    ////////////////////////////////////////////////////////////
    // Constant variables
    ////////////////////////////////////////////////////////////
    public static final String TAG = Log.TAG_SYNC;

    // https://developer.android.com/training/articles/keystore.html#SupportedCiphers
    private static final String CIPHER_ALGORITHM = "AES/CBC/PKCS7Padding";

    private static final String serviceName = "CouchbaseLite";
    private static final String alias = "CouchbaseLiteTokenStoreAES";

    private static final boolean hasKeyStore = Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2; // API 18
    private static final boolean hasKeyGenerator = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M; // API 23

    ////////////////////////////////////////////////////////////
    // Member variables
    ////////////////////////////////////////////////////////////
    private Context context = null;

    ////////////////////////////////////////////////////////////
    // Constructors
    ////////////////////////////////////////////////////////////
    public AESSecureTokenStore(Context context) {
        this.context = context;
        initializePrivateKey(context);
    }

    ////////////////////////////////////////////////////////////
    // Implementation of TokenStore
    ////////////////////////////////////////////////////////////

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public Map<String, String> loadTokens(URL remoteURL, String localUUID) throws Exception {
        if (!hasKeyStore || !hasKeyGenerator)
            return null;

        SharedPreferences prefs = context.getSharedPreferences(serviceName, Context.MODE_PRIVATE);
        String key = getKey(remoteURL, localUUID);
        String base64EncryptedStr = prefs.getString(key, null);
        if (base64EncryptedStr == null)
            return null;
        String base64Iv = prefs.getString(key + "_iv", null);
        if (base64Iv == null)
            return null;
        return decrypt(base64EncryptedStr, base64Iv);
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public boolean saveTokens(URL remoteURL, String localUUID, Map<String, String> tokens) {
        if (!hasKeyStore || !hasKeyGenerator)
            return false;

        String[] encryptedData = encrypt(tokens);
        if (encryptedData == null)
            return false;

        SharedPreferences prefs = context.getSharedPreferences(serviceName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String key = getKey(remoteURL, localUUID);
        editor.putString(key, encryptedData[0]);
        editor.putString(key + "_iv", encryptedData[1]);
        return editor.commit();
    }

    @TargetApi(Build.VERSION_CODES.M)
    @Override
    public boolean deleteTokens(URL remoteURL, String localUUID) {
        if (!hasKeyStore || !hasKeyGenerator)
            return false;

        SharedPreferences prefs = context.getSharedPreferences(serviceName, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        String key = getKey(remoteURL, localUUID);
        editor.remove(key);
        return editor.commit();
    }

    ////////////////////////////////////////////////////////////
    // protected/private methods
    ////////////////////////////////////////////////////////////

    private String getKey(URL remoteURL, String localUUID) {
        String service = remoteURL.toExternalForm();
        String label = String.format(Locale.ENGLISH, "%s OpenID Connect tokens", remoteURL.getHost());
        if (localUUID == null)
            return String.format(Locale.ENGLISH, "%s%s%s", alias, label, service);
        else
            return String.format(Locale.ENGLISH, "%s%s%s%s", alias, label, service, localUUID);
    }

    @TargetApi(Build.VERSION_CODES.M)
    private String[] encrypt(Map<String, String> map) {
        byte[] bytes = ConversionUtils.toByteArray(map);
        if (bytes == null)
            return null;

        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            SecretKey secretKey = (SecretKey) keyStore.getKey(alias, null);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher);
            byte[] iv = null;
            try {
                cipherOutputStream.write(bytes);
                iv = cipher.getIV();
            } finally {
                cipherOutputStream.close();
            }
            byte[] encrypted = outputStream.toByteArray();

            String[] encryptedData = new String[2];
            encryptedData[0] = Base64.encodeToString(encrypted, Base64.DEFAULT);
            encryptedData[1] = Base64.encodeToString(iv, Base64.DEFAULT);
            return encryptedData;
        } catch (Exception ex) {
            Log.e(TAG, "Unable to open KeyStore", ex);
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private Map decrypt(String base64EncryptedStr, String base64Iv) {
        byte[] decrypted = null;

        try {
            byte[] encrypted = Base64.decode(base64EncryptedStr, Base64.DEFAULT);
            byte[] iv = Base64.decode(base64Iv, Base64.DEFAULT);

            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);

            SecretKey secretKey = (SecretKey) keyStore.getKey(alias, null);
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(iv));

            ByteArrayInputStream inputStream = new ByteArrayInputStream(encrypted);
            CipherInputStream cipherInputStream = new CipherInputStream(inputStream, cipher);
            try {
                ArrayList<Byte> values = new ArrayList<>();
                int nextByte;
                while ((nextByte = cipherInputStream.read()) != -1) {
                    values.add((byte) nextByte);
                }
                decrypted = new byte[values.size()];
                for (int i = 0; i < decrypted.length; i++) {
                    decrypted[i] = values.get(i).byteValue();
                }
            } finally {
                cipherInputStream.close();
            }
        } catch (Exception ex) {
            Log.e(TAG, "Unable to open KeyStore", ex);
            return null;
        }

        try {
            return ConversionUtils.fromByteArray(decrypted);
        } catch (IOException e) {
            Log.e(TAG, "Unable to decrypt: value=<%s>", e, base64EncryptedStr);
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    private void initializePrivateKey(Context context) {
        if (!hasKeyStore || !hasKeyGenerator)
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
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            keyGenerator.init(new KeyGenParameterSpec.Builder(alias,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                    .build());
            SecretKey secretKey = keyGenerator.generateKey();
        } catch (Exception ex) {
            Log.e(TAG, "Unable to create new key", ex);
            return;
        }
    }
}
