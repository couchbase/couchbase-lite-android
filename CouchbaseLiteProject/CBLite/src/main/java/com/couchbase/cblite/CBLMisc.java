package com.couchbase.cblite;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import android.util.Log;

public class CBLMisc {

    public static String TDCreateUUID() {
        return UUID.randomUUID().toString();
    }

    public static String TDHexSHA1Digest(byte[] input) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            Log.e(CBLDatabase.TAG, "Error, SHA-1 digest is unavailable.");
            return null;
        }
        byte[] sha1hash = new byte[40];
        md.update(input, 0, input.length);
        sha1hash = md.digest();
        return convertToHex(sha1hash);
    }

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

    public static int TDSequenceCompare(long a, long b) {
        long diff = a - b;
        return diff > 0 ? 1 : (diff < 0 ? -1 : 0);
    }

}
