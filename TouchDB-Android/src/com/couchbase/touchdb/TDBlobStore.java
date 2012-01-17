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

package com.couchbase.touchdb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.util.Log;

public class TDBlobStore {

    public static String FILE_EXTENSION = ".blob";

    private String path;

    public TDBlobStore(String path) {
        this.path = path;
        File directory = new File(path);
        if(!directory.exists()) {
            boolean result = directory.mkdirs();
            if(result == false) {
                throw new IllegalArgumentException("Unable to create directory for blob store");
            }
        }
        else if(!directory.isDirectory()) {
            throw new IllegalArgumentException("Directory for blob store is not a directory");
        }
    }

    public static TDBlobKey keyForBlob(byte[] data) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            Log.e(TDDatabase.TAG, "Error, SHA-1 digest is unavailable.");
            return null;
        }
        byte[] sha1hash = new byte[40];
        md.update(data, 0, data.length);
        sha1hash = md.digest();
        TDBlobKey result = new TDBlobKey(sha1hash);
        return result;
    }

    public String pathForKey(TDBlobKey key) {
        return path + File.separator + TDBlobKey.convertToHex(key.getBytes()) + FILE_EXTENSION;
    }

    public boolean getKeyForFilename(TDBlobKey outKey, String filename) {
        if(!filename.endsWith(FILE_EXTENSION)) {
            return false;
        }
        //trim off extension
        String rest = filename.substring(path.length() + 1, filename.length() - FILE_EXTENSION.length());

        outKey.setBytes(TDBlobKey.convertFromHex(rest));

        return true;
    }

    public byte[] blobForKey(TDBlobKey key) {
        String path = pathForKey(key);
        File file = new File(path);
        byte[] result = null;
        try {
            result = getBytesFromFile(file);
        } catch (IOException e) {
            Log.e(TDDatabase.TAG, "Error reading file", e);
        }
        return result;
    }

    public boolean storeBlob(byte[] data, TDBlobKey outKey) {
        TDBlobKey newKey = keyForBlob(data);
        outKey.setBytes(newKey.getBytes());
        String path = pathForKey(outKey);
        File file = new File(path);
        if(file.canRead()) {
            return true;
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(data);
        } catch (FileNotFoundException e) {
            Log.e(TDDatabase.TAG, "Error opening file for output", e);
            return false;
        } catch(IOException ioe) {
            Log.e(TDDatabase.TAG, "Error writing to file", ioe);
            return false;
        } finally {
            if(fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        return true;
    }

    private static byte[] getBytesFromFile(File file) throws IOException {
        InputStream is = new FileInputStream(file);

        // Get the size of the file
        long length = file.length();

        // Create the byte array to hold the data
        byte[] bytes = new byte[(int)length];

        // Read in the bytes
        int offset = 0;
        int numRead = 0;
        while (offset < bytes.length
               && (numRead=is.read(bytes, offset, bytes.length-offset)) >= 0) {
            offset += numRead;
        }

        // Ensure all the bytes have been read in
        if (offset < bytes.length) {
            throw new IOException("Could not completely read file "+file.getName());
        }

        // Close the input stream and return bytes
        is.close();
        return bytes;
    }

    public Set<TDBlobKey> allKeys() {
        Set<TDBlobKey> result = new HashSet<TDBlobKey>();
        File file = new File(path);
        File[] contents = file.listFiles();
        for (File attachment : contents) {
            TDBlobKey attachmentKey = new TDBlobKey();
            getKeyForFilename(attachmentKey, attachment.getPath());
            result.add(attachmentKey);
        }
        return result;
    }

    public int count() {
        File file = new File(path);
        File[] contents = file.listFiles();
        return contents.length;
    }

    public long totalDataSize() {
        long total = 0;
        File file = new File(path);
        File[] contents = file.listFiles();
        for (File attachment : contents) {
            total += attachment.length();
        }
        return total;
    }

    public int deleteBlobsExceptWithKeys(List<TDBlobKey> keysToKeep) {
        int numDeleted = 0;
        File file = new File(path);
        File[] contents = file.listFiles();
        for (File attachment : contents) {
            TDBlobKey attachmentKey = new TDBlobKey();
            getKeyForFilename(attachmentKey, attachment.getPath());
            if(!keysToKeep.contains(attachmentKey)) {
                boolean result = attachment.delete();
                if(result) {
                    ++numDeleted;
                }
                else {
                    Log.e(TDDatabase.TAG, "Error deleting attachmetn");
                }
            }
        }
        return numDeleted;
    }
}
