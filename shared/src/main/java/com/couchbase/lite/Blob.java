//
// Blob.java
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

import android.support.annotation.NonNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.internal.core.C4BlobKey;
import com.couchbase.lite.internal.core.C4BlobReadStream;
import com.couchbase.lite.internal.core.C4BlobStore;
import com.couchbase.lite.internal.core.C4BlobWriteStream;
import com.couchbase.lite.internal.fleece.FLEncodable;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;


/**
 * A Couchbase Lite Blob. A Blob appears as a property of a Document; it contains arbitrary binary
 * data, tagged with MIME type.
 * Blobs can be arbitrarily large, and their data is loaded only on demand (when the `content` or
 * `contentStream` properties aar accessed), not when the document is loaded. The document's raw
 * JSON form only contains the Blob's metadata (type, length and digest of the data) in small
 * object. The data itself is stored externally to the document, keyed by the digest.)
 */
public final class Blob implements FLEncodable {
    private static final LogDomain DOMAIN = LogDomain.DATABASE;

    //---------------------------------------------
    // static constant variables
    //---------------------------------------------
    // Max size of data that will be cached in memory with the CBLBlob
    private static final int MAX_CACHED_CONTENT_LENGTH = 8 * 1024;

    /**
     * The sub-document property that identifies it as a special type of object.
     * For example, a blob is represented as `{"@type":"blob", "digest":"xxxx", ...}`
     */
    private static final String kMetaPropertyDigest = "digest";
    private static final String kMetaPropertyLength = "length";
    private static final String kMetaPropertyContentType = "content_type";
    private static final String kMetaPropertyData = "data";

    static final String kMetaPropertyType = "@type";
    static final String kBlobType = "blob";

    static final class BlobInputStream extends InputStream {
        private C4BlobStore store;
        private C4BlobKey key;
        private C4BlobReadStream readStream;

        BlobInputStream(C4BlobStore store, C4BlobKey key) {
            if (key == null) { throw new IllegalArgumentException("key cannot be null."); }
            if (store == null) {
                key.free();
                throw new IllegalArgumentException("store cannot be null.");
            }
            this.store = store;
            this.key = key;

            try {
                this.readStream = store.openReadStream(key);
            }
            catch (LiteCoreException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public int read() throws IOException {
            try {
                final byte[] bytes = readStream.read(1);
                if (bytes.length == 1) { return bytes[0]; }
                // EOF
                else { return -1; }
            }
            catch (LiteCoreException e) {
                throw new IOException(e);
            }
        }

        @Override
        public int read(byte[] b) throws IOException {
            try {
                final byte[] bytes = readStream.read(b.length);
                System.arraycopy(bytes, 0, b, 0, bytes.length);
                return bytes.length;
            }
            catch (LiteCoreException e) {
                throw new IOException(e);
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                final byte[] bytes = readStream.read(len);
                System.arraycopy(bytes, 0, b, off, bytes.length);
                return bytes.length;
            }
            catch (LiteCoreException e) {
                throw new IOException(e);
            }
        }

        @Override
        public long skip(long n) throws IOException {
            try {
                readStream.seek(n);
                return n;
            }
            catch (LiteCoreException e) {
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

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    /**
     * The type of content this CBLBlob represents; by convention this is a MIME type.
     */
    private final String contentType;

    /**
     * The binary length of this CBLBlob.
     */
    private long blobLength;

    /**
     * The cryptographic digest of this CBLBlob's contents, which uniquely identifies it.
     */
    private String blobDigest;

    /**
     * Gets the contents of a CBLBlob as a block of memory.
     * Not recommended for very large blobs, as it may be slow and use up lots of RAM.
     */
    private byte[] content; // If new from data, or already loaded from db

    /**
     * The metadata associated with this CBLBlob
     */
    private Map<String, Object> properties; // Only in blob read from database

    private InputStream initialContentStream; // If new from stream

    private Database database; // nil if blob is new and unsaved

    // A newly created unsaved blob will have either _content or _initialContentStream.
    // A new blob saved to the database will have _db and _digest.
    // A blob loaded from the database will have _db and _properties, and _digest unless invalid

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Construct a Blob with the given in-memory data. The blob can then be added as a  property of
     * a Document.
     * <p>
     * !!FIXME: This method stores a mutable array as private data
     *
     * @param contentType The type of content this Blob will represent
     * @param content     The data that this Blob will contain
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public Blob(@NonNull String contentType, @NonNull byte[] content) {
        if (contentType == null) { throw new IllegalArgumentException("contentType cannot be null."); }
        if (content == null) { throw new IllegalArgumentException("content cannot be null."); }
        this.contentType = contentType;
        this.content = content;
        this.blobLength = content.length;
    }

    /**
     * Construct a Blob with the given stream of data The blob can then be added as a  property of
     * a Document.
     *
     * @param contentType The type of content this Blob will represent
     * @param stream      The stream of data that this Blob will consume
     */
    public Blob(@NonNull String contentType, @NonNull InputStream stream) {
        if (contentType == null) { throw new IllegalArgumentException("contentType cannot be null."); }
        if (stream == null) { throw new IllegalArgumentException("stream cannot be null."); }
        this.contentType = contentType;
        this.initialContentStream = stream;
        this.blobLength = 0L; // unknown
    }

    /**
     * Construct a Blob with the content of a file. The blob can then be added as a  property of
     * a Document.
     *
     * @param contentType The type of content this Blob will represent
     * @param fileURL     A URL to a file containing the data that this Blob will represent.
     * @throws IOException
     */
    public Blob(@NonNull String contentType, @NonNull URL fileURL) throws IOException {
        if (contentType == null) { throw new IllegalArgumentException("contentType cannot be null."); }
        if (fileURL == null) { throw new IllegalArgumentException("fileURL cannot be null."); }
        if (!fileURL.getProtocol().equalsIgnoreCase("file")) {
            throw new IllegalArgumentException(
                String.format(
                    Locale.ENGLISH,
                    "<%s> must be a file-based URL.",
                    fileURL.toString()));
        }
        this.contentType = contentType;
        this.initialContentStream = fileURL.openStream();
        this.blobLength = 0L; // unknown
    }

    // Initializer for an existing blob being read from a document
    Blob(Database database, Map<String, Object> properties) {
        this.database = database;
        this.properties = new HashMap<>(properties);
        this.properties.remove(kMetaPropertyType);

        // NOTE: length field might not be set if length is unknown.
        if (properties.get("length") != null && properties.get("length") instanceof Number) {
            this.blobLength = ClassUtils.cast(properties.get("length"), Number.class).longValue();
        }
        this.blobDigest = ClassUtils.cast(properties.get("digest"), String.class);
        this.contentType = ClassUtils.cast(properties.get("content_type"), String.class);

        final Object data = properties.get(kMetaPropertyData);
        if (data instanceof byte[]) { this.content = (byte[]) data; }

        if (this.blobDigest == null && this.content == null) {
            Log.w(
                DOMAIN,
                "Blob read from database has neither digest nor data.");
        }
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Gets the contents of a Blob as a block of memory. Not recommended for very large blobs, as it
     * may be slow and use up lots of RAM.
     * <p>
     * !!FIXME: This method returns a writeable copy of its private data
     *
     * @return the contents of a Blob as a block of memory
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public byte[] getContent() {
        if (content != null) {
            // Data is in memory:
            return content;
        }

        return (database != null) ? getBytesFromDatabase() : getBytesFromInitialContentStream();
    }

    /**
     * Get the stream of content of a Blob. The call is responsible for closing the stream when
     * finished.
     *
     * @return the stream of content of a Blob
     */
    public InputStream getContentStream() {
        if (database != null) {
            return new BlobInputStream(getBlobStore(), getBlobKey());
        }
        else {
            final byte[] content = getContent();
            return content == null ? null : new ByteArrayInputStream(content);
        }
    }

    /**
     * Return the type of content this Blob represents; by convention this is a MIME type.
     *
     * @return the type of content
     */
    @NonNull
    public String getContentType() {
        return contentType;
    }

    /**
     * The binary length of this Blob
     *
     * @return The binary length of this Blob
     */
    public long length() {
        return blobLength;
    }

    /**
     * The cryptograhic digest of this Blob's contents, which uniquely identifies it.
     *
     * @return The cryptograhic digest of this Blob's contents
     */
    public String digest() {
        return blobDigest;
    }

    /**
     * Return the metadata associated with this Blob
     *
     * @return the metadata associated with this Blob
     */
    @NonNull
    public Map<String, Object> getProperties() {
        if (properties != null) {
            // Blob read from database;
            return properties;
        }
        else {
            final Map<String, Object> props = new HashMap<>();
            props.put(kMetaPropertyDigest, blobDigest);
            props.put(kMetaPropertyLength, blobLength);
            props.put(kMetaPropertyContentType, contentType);
            return props;
        }
    }

    //---------------------------------------------
    // Override
    //---------------------------------------------

    /**
     * Returns a string representation of the object.
     *
     * @return a string representation of the object
     */
    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "Blob[%s; %d KB]",
            contentType, (length() + 512) / 1024);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof Blob)) { return false; }

        final Blob m = (Blob) o;
        if (blobDigest != null && m.blobDigest != null) { return blobDigest.equals(m.blobDigest); }
        else { return Arrays.equals(getContent(), m.getContent()); }
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getContent());
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    private Map<String, Object> jsonRepresentation() {
        final Map<String, Object> json = new HashMap<>(getProperties());
        json.put(kMetaPropertyType, kBlobType);
        if (blobDigest != null) {
            json.put(kMetaPropertyDigest, blobDigest);
        }
        else {
            json.put(kMetaPropertyData, getContent());
        }
        return json;
    }

    private void installInDatabase(Database db) {
        if (db == null) { throw new IllegalArgumentException("db cannot be null."); }

        if (this.database != null) {
            if (this.database != db) {
                throw new IllegalStateException(
                    "A document contains a blob that was saved to a different database; the save operation cannot "
                        + "complete.");
            }
            return;
        }

        C4BlobKey key = null;
        try {
            final C4BlobStore store = db.getBlobStore();
            try {
                if (content != null) {
                    key = store.create(content);
                }
                else {
                    final C4BlobWriteStream blobOut = store.openWriteStream();
                    try {
                        final byte[] buffer = new byte[MAX_CACHED_CONTENT_LENGTH];
                        int bytesRead;
                        this.blobLength = 0;
                        final InputStream contentStream = getContentStream();
                        try {
                            if (contentStream == null) {
                                throw new IllegalStateException(
                                    "No data available to write for install.  Please ensure that all blobs in the "
                                        + "document have non-null content.");
                            }
                            while ((bytesRead = contentStream.read(buffer)) > 0) {
                                this.blobLength += bytesRead;
                                blobOut.write(Arrays.copyOfRange(buffer, 0, bytesRead));
                            }
                        }
                        finally {
                            try {
                                if (contentStream != null) { contentStream.close(); }
                            }
                            catch (IOException ignore) { }
                        }
                        key = blobOut.computeBlobKey();
                        blobOut.install();
                    }
                    finally {
                        blobOut.close();
                    }
                }
                this.blobDigest = key.toString();
                this.database = db;
            }
            finally {
                if (store != null) { store.free(); }
            }
        }
        catch (Exception e) {
            throw new IllegalStateException(e);
        }
        finally {
            if (key != null) { key.free(); }
        }
    }

    //FLEncodable
    @Override
    public void encodeTo(FLEncoder encoder) {
        final Object info = encoder.getExtraInfo();
        if (info != null) {
            installInDatabase(((MutableDocument) info).getDatabase());
        }

        final Map<String, Object> dict = jsonRepresentation();
        encoder.beginDict(dict.size());
        for (Map.Entry<String, Object> entry : dict.entrySet()) {
            encoder.writeKey(entry.getKey());
            encoder.writeValue(entry.getValue());
        }
        encoder.endDict();
    }

    //---------------------------------------------
    // Private (in class only)
    //---------------------------------------------
    private C4BlobStore getBlobStore() {
        if (database == null) {
            Log.w(DOMAIN, "database instance is null.");
            return null;
        }
        try {
            return database.getBlobStore();
        }
        catch (LiteCoreException e) {
            throw new IllegalStateException(e);
        }
    }

    private C4BlobKey getBlobKey() {
        try {
            return new C4BlobKey(blobDigest);
        }
        catch (LiteCoreException e) {
            Log.w(DOMAIN, "Invalid digest: " + blobDigest, e);
            return null;
        }
    }

    private byte[] getBytesFromInitialContentStream() {
        // No recourse but to read the initial stream into memory:
        final byte[] contentResult;
        if (initialContentStream == null) {
            throw new IllegalStateException("initialContentStream variable is null");
        }
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            final byte[] buffer = new byte[MAX_CACHED_CONTENT_LENGTH];
            try {
                int bytesRead;
                while ((bytesRead = initialContentStream.read(buffer)) >= 0) {
                    out.write(buffer, 0, bytesRead);
                }
                contentResult = out.toByteArray();
                blobLength = contentResult.length;
                if (blobLength <= MAX_CACHED_CONTENT_LENGTH) {
                    content = contentResult;  // cache for later re-use
                }
            }
            catch (IOException e) {
                Log.w(DOMAIN, "I/O Error with the given stream.", e);
                throw new IllegalStateException(e);
            }
            finally {
                try {
                    initialContentStream.close();
                    initialContentStream = null;
                }
                catch (IOException ignore) { }
            }
        }
        finally {
            try { out.close(); }
            catch (IOException ignore) { }
        }
        return contentResult;
    }

    private byte[] getBytesFromDatabase() {
        byte[] contentResult = null;
        // Read blob from the BlobStore:
        // NOTE: should not return null.
        // BlobStore does not require to be closed because it is created from database.
        final C4BlobStore blobStore = getBlobStore();

        try {
            final C4BlobKey key = getBlobKey();
            if (key == null) { throw new IllegalStateException("Invalid digest: " + blobDigest); }
            try {
                final FLSliceResult res = blobStore.getContents(key);
                try {
                    final byte[] bytes = res.getBuf();
                    if (bytes != null) {
                        if (bytes.length <= MAX_CACHED_CONTENT_LENGTH) {
                            content = bytes;  // cache for later re-use
                        }
                        contentResult = bytes;
                    }
                }
                finally {
                    res.free();
                }
            }
            catch (LiteCoreException e) {
                Log.e(DOMAIN, "Failed to obtain content from BlobStore. digest=" + blobDigest, e);
                throw new IllegalStateException("Failed to obtain content from BlobStore. digest=" + blobDigest, e);
            }
            finally {
                key.free();
            }
            return contentResult;
        }
        finally {
            if (blobStore != null) { blobStore.free(); }
        }
    }

    //---------------------------------------------
    // Load LiteCore library and its dependencies
    //---------------------------------------------
    static {
        NativeLibraryLoader.load();
    }
}
