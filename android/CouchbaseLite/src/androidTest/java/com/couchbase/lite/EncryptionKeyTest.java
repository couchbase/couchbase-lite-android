//
// EncryptionKeyTest.java
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

import com.couchbase.lite.internal.support.Log;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class EncryptionKeyTest extends BaseTest {
    @Test
    public void testEncryptionKey() {
        EncryptionKey key = new EncryptionKey("hello world!");
        assertNotNull(key.getKey());
        assertEquals(C4EncryptionKeySize.kC4EncryptionKeySizeAES128, key.getKey().length);
        Log.i(TAG, "key -> " + bytesToHex(key.getKey()));
    }

    static String bytesToHex(byte[] in) {
        final StringBuilder builder = new StringBuilder();
        for (byte b : in) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
