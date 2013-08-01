package com.couchbase.cblite;

import android.util.Log;

import com.couchbase.cblite.support.Base64;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Lets you stream a large attachment to a CBL_BlobStore asynchronously, e.g. from a network download. */
public class CBLBlobStoreWriter {

    /** The underlying blob store where it should be stored. */
    private CBLBlobStore store;

    /** The number of bytes in the blob. */
    private int length;

    /** After finishing, this is the key for looking up the blob through the CBL_BlobStore. */
    private CBLBlobKey blobKey;

    /** After finishing, store md5 digest result here */
    private byte[] md5DigestResult;

    /** Message digest for sha1 that is updated as data is appended */
    private MessageDigest sha1Digest;
    private MessageDigest md5Digest;

    private BufferedOutputStream outStream;
    private File tempFile;

    public CBLBlobStoreWriter(CBLBlobStore store) {
        this.store = store;

        try {
            sha1Digest = MessageDigest.getInstance("SHA-1");
            sha1Digest.reset();
            md5Digest = MessageDigest.getInstance("MD5");
            md5Digest.reset();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

        try {
            openTempFile();
        } catch (FileNotFoundException e) {
            throw new IllegalStateException(e);
        }

    }

    private void openTempFile() throws FileNotFoundException {

        String uuid = CBLMisc.TDCreateUUID();
        String filename = String.format("%s.blobtmp", uuid);
        File tempDir = store.tempDir();
        tempFile = new File(tempDir, filename);
        outStream = new BufferedOutputStream(new FileOutputStream(tempFile));

    }

    /** Appends data to the blob. Call this when new data is available. */
    public void appendData(byte[] data) throws IOException {
        outStream.write(data);
        length += data.length;
        sha1Digest.update(data);
        md5Digest.update(data);
    }

    /** Call this after all the data has been added. */
    public void finish() {
        try {
            outStream.close();
        } catch (IOException e) {
            Log.w(CBLDatabase.TAG, "Exception closing output stream", e);
        }
        blobKey = new CBLBlobKey(sha1Digest.digest());
        md5DigestResult = md5Digest.digest();
    }

    /** Call this to cancel before finishing the data. */
    public void cancel() {
        try {
            outStream.close();
        } catch (IOException e) {
            Log.w(CBLDatabase.TAG, "Exception closing output stream", e);
        }
        tempFile.delete();
    }

    /** Installs a finished blob into the store. */
    public void install() {

        if (tempFile == null) {
            return;  // already installed
        }

        // Move temp file to correct location in blob store:
        String destPath = store.pathForKey(blobKey);
        File destPathFile = new File(destPath);
        boolean result = tempFile.renameTo(destPathFile);

        // If the move fails, assume it means a file with the same name already exists; in that
        // case it must have the identical contents, so we're still OK.
        if (result == false) {
            cancel();
        }

        tempFile = null;

    }

    public String mD5DigestString() {
        String base64Md5Digest = Base64.encodeBytes(md5DigestResult);
        return String.format("md5-%s", base64Md5Digest);
    }

    public String sHA1DigestString() {
        String base64Sha1Digest = Base64.encodeBytes(blobKey.getBytes());
        return String.format("sha1-%s", base64Sha1Digest);
    }


}
