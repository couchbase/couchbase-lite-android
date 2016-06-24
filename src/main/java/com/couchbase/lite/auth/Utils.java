package com.couchbase.lite.auth;

import com.couchbase.lite.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;

/**
 * Created by hideki on 6/23/16.
 */
public class Utils {
    private static final String TAG = Log.TAG_SYNC;

    static byte[] toByteArray(Map obj) {
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

    static Map fromByteArray(byte[] bytes) throws IOException {
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
