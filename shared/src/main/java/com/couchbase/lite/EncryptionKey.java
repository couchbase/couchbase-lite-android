package com.couchbase.lite;

import com.couchbase.litecore.C4Key;

/**
 * The encryption key, a raw AES-256 key data which has exactly 32 bytes in length
 * or a password string. If the password string is given, it will be internally converted to a
 * raw AES key using 64,000 rounds of PBKDF2 hashing.
 */
public class EncryptionKey {
    private static final int kKeySize = 32; // 256-bit (32-byte)
    private static final String DEFAULT_PBKDF2_KEY_SALT = "Salty McNaCl";
    private static final int DEFAULT_PBKDF2_KEY_ROUNDS = 64000; // Same as what SQLCipher uses

    private byte[] key = null;

    /**
     * Initializes the encryption key with a raw AES-256 key data which has 32 bytes in length.
     * To create a key, generate random data using a secure cryptographic randomizer.
     *
     * @param key The raw AES-256 key data
     */
    public EncryptionKey(byte[] key) {
        if (key != null && key.length != kKeySize)
            throw new CouchbaseLiteRuntimeException("Key size is invalid. Key must be a 256-bit (32-byte) key.");
        this.key = key;
    }

    /**
     * Initializes the encryption key with the given password string. The password string will be
     * internally converted to a raw AES-256 key using 64,000 rounds of PBKDF2 hashing.
     *
     * @param password The password string.
     */
    public EncryptionKey(String password) {
        this(password != null ? C4Key.derivePBKDF2SHA256Key(password, DEFAULT_PBKDF2_KEY_SALT.getBytes(), DEFAULT_PBKDF2_KEY_ROUNDS) : null);
    }

    byte[] getKey() {
        return key;
    }
}
