/**
 * Original iOS version by  Jens Alfke
 * Ported to Android by Marty Schoch
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.cblite;


import com.couchbase.cblite.support.Base64;

import java.io.IOException;
import java.util.Arrays;

/**
 * Key identifying a data blob. This happens to be a SHA-1 digest.
 */
public class CBLBlobKey {

    private byte[] bytes;

    public CBLBlobKey() {
    }

    public CBLBlobKey(byte[] bytes) {
        this.bytes = bytes;
    }

    /**
     * Constructor
     *
     * @param base64Digest string with base64'd digest, with leading "sha1-" attached.
     *                     eg, "sha1-LKJ32423JK..."
     */
    public CBLBlobKey(String base64Digest) {
        this(decodeBase64Digest(base64Digest));
    }

    /**
     * Decode base64'd digest into a byte array that is suitable for use
     * as a blob key.
     *
     * @param base64Digest string with base64'd digest, with leading "sha1-" attached.
     *                     eg, "sha1-LKJ32423JK..."
     * @return a byte[] blob key
     */
    private static byte[] decodeBase64Digest(String base64Digest) {
        String expectedPrefix = "sha1-";
        if (!base64Digest.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException(base64Digest + " did not start with " + expectedPrefix);
        }
        base64Digest = base64Digest.replaceFirst(expectedPrefix, "");
        byte[] bytes = new byte[0];
        try {
            bytes = Base64.decode(base64Digest);
        } catch (IOException e) {
            new IllegalArgumentException(e);
        }
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    };

    public static String convertToHex(byte[] data) {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < data.length; i++) {
            int halfbyte = (data[i] >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                if ((0 <= halfbyte) && (halfbyte <= 9))
                    buf.append((char) ('0' + halfbyte));
                else
                    buf.append((char) ('a' + (halfbyte - 10)));
                halfbyte = data[i] & 0x0F;
            } while(two_halfs++ < 1);
        }
        return buf.toString();
    }

    public static byte[] convertFromHex(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    @Override
    public boolean equals(Object o) {
        if(!(o instanceof CBLBlobKey)) {
            return false;
        }
        CBLBlobKey oBlobKey = (CBLBlobKey)o;
        return Arrays.equals(getBytes(), oBlobKey.getBytes());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }


    @Override
    public String toString() {
        return CBLBlobKey.convertToHex(bytes);
    }
}
