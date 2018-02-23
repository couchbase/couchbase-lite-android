//
// EncryptionKey.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import com.couchbase.litecore.C4Constants;
import com.couchbase.litecore.C4Key;

/**
 * An encryption key for a database. This is a symmetric key that be kept secret.
 * It should be stored either in the Keychain, or in the user's memory (hopefully not a sticky note.)
 */
public final class EncryptionKey {
    private static final String DEFAULT_PBKDF2_KEY_SALT = "Salty McNaCl";
    private static final int DEFAULT_PBKDF2_KEY_ROUNDS = 64000; // Same as what SQLCipher uses

    private byte[] key = null;

    /**
     * Initializes the encryption key with a raw AES-128 key 16 bytes in length.
     * To create a key, generate random data using a secure cryptographic randomizer.
     *
     * @param key The raw AES-128 key data.
     */
    public EncryptionKey(byte[] key) {
        if (key != null && key.length != C4Constants.C4EncryptionKeySize.kC4EncryptionKeySizeAES128)
            throw new CouchbaseLiteRuntimeException("Key size is invalid. Key must be a 128-bit (16-byte) key.");
        this.key = key;
    }

    /**
     * Initializes the encryption key with the given password string. The password string will be
     * internally converted to a raw AES-128 key using 64,000 rounds of PBKDF2 hashing.
     *
     * @param password The password string.
     */
    public EncryptionKey(String password) {
        this(password != null ? C4Key.pbkdf2(password,
                DEFAULT_PBKDF2_KEY_SALT.getBytes(), DEFAULT_PBKDF2_KEY_ROUNDS,
                C4Constants.C4EncryptionKeySize.kC4EncryptionKeySizeAES128) : null);
    }

    byte[] getKey() {
        return key;
    }
}
