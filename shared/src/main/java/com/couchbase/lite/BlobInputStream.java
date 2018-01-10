package com.couchbase.lite;

import com.couchbase.litecore.C4BlobKey;
import com.couchbase.litecore.C4BlobReadStream;
import com.couchbase.litecore.C4BlobStore;
import com.couchbase.litecore.LiteCoreException;

import java.io.IOException;
import java.io.InputStream;

class BlobInputStream extends InputStream {
    C4BlobStore store = null;
    C4BlobKey key = null;
    boolean hasBytesAvailable = false;
    boolean closed = true;
    C4BlobReadStream readStream = null;

    BlobInputStream(C4BlobStore store, C4BlobKey key) {
        if (key == null)
            throw new IllegalArgumentException("key is null.");
        if (store == null) {
            key.free();
            throw new IllegalArgumentException("store is null.");
        }
        this.store = store;
        this.key = key;

        try {
            this.readStream = store.openReadStream(key);
            this.hasBytesAvailable = true;
            this.closed = false;
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertRuntimeException(e);
        }
    }

    @Override
    public int read() throws IOException {
        try {
            byte[] bytes = readStream.read(1);
            if (bytes.length == 1)
                return bytes[0];
            else
                // EOF
                return -1;
        } catch (LiteCoreException e) {
            throw new IOException(e);
        }
    }

    @Override
    public int read(byte[] b) throws IOException {
        try {
            byte[] bytes = readStream.read(b.length);
            System.arraycopy(bytes, 0, b, 0, bytes.length);
            if (bytes.length >= 0)
                return bytes.length;
            else
                return -1;
        } catch (LiteCoreException e) {
            throw new IOException(e);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        try {
            byte[] bytes = readStream.read(len);
            System.arraycopy(bytes, 0, b, off, bytes.length);
            if (bytes.length >= 0)
                return bytes.length;
            else
                return -1;
        } catch (LiteCoreException e) {
            throw new IOException(e);
        }
    }

    @Override
    public long skip(long n) throws IOException {
        try {
            readStream.seek(n);
            return n;
        } catch (LiteCoreException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        super.close();

        // close internal stream
        if (readStream != null) {
            readStream.close();
            readStream = null;
            closed = true;
            hasBytesAvailable = false;
        }

        // key should be free
        if (key != null) {
            key.free();
            key = null;
        }

        if (store != null) {
            store.free();
            store = null;
        }
    }
}
