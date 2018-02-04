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

import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.internal.utils.ClassUtils;
import com.couchbase.litecore.C4BlobKey;
import com.couchbase.litecore.C4BlobReadStream;
import com.couchbase.litecore.C4BlobStore;
import com.couchbase.litecore.C4BlobWriteStream;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.fleece.FLEncodable;
import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.FLSliceResult;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * A Couchbase Lite Blob. A Blob appears as a property of a Document; it contains arbitrary binary
 * data, tagged with MIME type.
 * Blobs can be arbitrarily large, and their data is loaded only on demand (when the `content` or
 * `contentStream` properties aar accessed), not when the document is loaded. The document's raw
 * JSON form only contains the Blob's metadata (type, length and digest of the data) in small
 * object. The data itself is stored externally to the document, keyed by the digest.)
 */
public final class Blob implements FLEncodable {
    //---------------------------------------------
    // static constant variables
    //---------------------------------------------

    static final String TAG = Log.DATABASE;

    /**
     * The sub-document property that identifies it as a special type of object.
     * For example, a blob is represented as `{"@type":"blob", "digest":"xxxx", ...}`
     */
    static final String kC4ObjectTypeProperty = "@type";
    static final String kC4ObjectType_Blob = "blob";

    // Max size of data that will be cached in memory with the CBLBlob
    static final int MAX_CACHED_CONTENT_LENGTH = 8 * 1024;

    //---------------------------------------------
    // member variables
    //---------------------------------------------

    /**
     * The type of content this CBLBlob represents; by convention this is a MIME type.
     */
    private String contentType = null;

    /**
     * The binary length of this CBLBlob.
     */
    private long length = 0L;

    /**
     * The cryptographic digest of this CBLBlob's contents, which uniquely identifies it.
     */
    private String digest = null;

    /**
     * Gets the contents of a CBLBlob as a block of memory.
     * Not recommended for very large blobs, as it may be slow and use up lots of RAM.
     */
    private byte[] content = null; // If new from data, or already loaded from db

    /**
     * The metadata associated with this CBLBlob
     */
    private Map<String, Object> properties = null; // Only in blob read from database

    private InputStream initialContentStream = null; // If new from stream

    private Database database = null; // nil if blob is new and unsaved

    // A newly created unsaved blob will have either _content or _initialContentStream.
    // A new blob saved to the database will have _db and _digest.
    // A blob loaded from the database will have _db and _properties, and _digest unless invalid

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Construct a Blob with the given in-memory data. The blob can then be added as a  property of
     * a Document.
     *
     * @param contentType The type of content this Blob will represent
     * @param content     The data that this Blob will contain
     */
    public Blob(String contentType, byte[] content) {
        this.contentType = contentType;
        this.content = content;
        this.length = content.length;
    }

    /**
     * Construct a Blob with the given stream of data The blob can then be added as a  property of
     * a Document.
     *
     * @param contentType The type of content this Blob will represent
     * @param stream      The stream of data that this Blob will consume
     */
    public Blob(String contentType, InputStream stream) {
        this.contentType = contentType;
        this.initialContentStream = stream;
        this.length = 0L; // unknown
    }

    /**
     * Construct a Blob with the content of a file. The blob can then be added as a  property of
     * a Document.
     *
     * @param contentType The type of content this Blob will represent
     * @param fileURL     A URL to a file containing the data that this Blob will represent.
     * @throws IOException
     */
    public Blob(String contentType, URL fileURL) throws IOException {
        this(contentType, fileURL.openStream());
    }

    // Initializer for an existing blob being read from a document
    Blob(Database database, Map<String, Object> properties) {
        this.database = database;
        this.properties = new HashMap<>(properties);
        this.properties.remove(kC4ObjectTypeProperty);

        // NOTE: length field might not be set if length is unknown.
        if (properties.get("length") != null && properties.get("length") instanceof Number)
            this.length = ClassUtils.cast(properties.get("length"), Number.class).longValue();
        this.digest = ClassUtils.cast(properties.get("digest"), String.class);
        this.contentType = ClassUtils.cast(properties.get("content_type"), String.class);
        if (this.digest == null) {
            Log.w(TAG, "Blob read from database has missing digest");
            this.digest = "";
        }
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Gets the contents of a Blob as a block of memory. Not recommended for very large blobs, as it
     * may be slow and use up lots of RAM.
     *
     * @return the contents of a Blob as a block of memory
     */
    public byte[] getContent() {
        if (content != null) {
            // Data is in memory:
            return content;
        } else if (database != null) {
            // Read blob from the BlobStore:
            C4BlobStore blobStore = getBlobStore(); // BlobStore does not required to close because it is created from database.
            if (blobStore == null)
                return null;
            try {
                C4BlobKey key = getBlobKey();
                if (key == null)
                    return null;
                try {
                    FLSliceResult res = blobStore.getContents(key);
                    try {
                        byte[] bytes = res.getBuf();
                        if (bytes != null && bytes.length <= MAX_CACHED_CONTENT_LENGTH)
                            content = bytes;
                    } finally {
                        res.free();
                    }
                } catch (LiteCoreException e) {
                    Log.e(TAG, "Failed to obtain content from BlobStore. digest=" + digest, e);
                } finally {
                    key.free();
                }
                return content;
            } finally {
                if (blobStore != null)
                    blobStore.free();
            }
        } else {
            // No recourse but to read the initial stream into memory:
            if (initialContentStream == null)
                return null;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                byte[] buffer = new byte[MAX_CACHED_CONTENT_LENGTH];
                try {
                    int bytesRead;
                    while ((bytesRead = initialContentStream.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                    content = out.toByteArray();
                    length = content.length;
                } catch (IOException e) {
                    Log.w(TAG, "I/O Error with the given stream.", e);
                    throw new CouchbaseLiteRuntimeException(e);
                } finally {
                    try {
                        initialContentStream.close();
                        initialContentStream = null;
                    } catch (IOException e) {
                    }
                }
            } finally {
                try {
                    out.close();
                } catch (IOException e) {
                }
            }
            return content;
        }
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
        } else {
            byte[] content = getContent();
            return content == null ? null : new ByteArrayInputStream(content);
        }
    }

    /**
     * Return the type of content this Blob represents; by convention this is a MIME type.
     *
     * @return the type of content
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * The binary length of this Blob
     *
     * @return The binary length of this Blob
     */
    public long length() {
        return length;
    }

    /**
     * The cryptograhic digest of this Blob's contents, which uniquely identifies it.
     *
     * @return The cryptograhic digest of this Blob's contents
     */
    public String digest() {
        return digest;
    }

    /**
     * Return the metadata associated with this Blob
     *
     * @return the metadata associated with this Blob
     */
    public Map<String, Object> getProperties() {
        if (properties != null) {
            // Blob read from database;
            return properties;
        } else {
            Map<String, Object> props = new HashMap<>();
            props.put("digest", digest);
            props.put("length", length);
            props.put("content_type", contentType);
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
    @Override
    public String toString() {
        return String.format(Locale.ENGLISH, "Blob[%s; %d KB]",
                contentType, (length() + 512) / 1024);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Blob)) return false;

        Blob m = (Blob) o;
        if (digest != null && m.digest != null)
            return digest.equals(m.digest);
        else
            return Arrays.equals(getContent(), m.getContent());
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(getContent());
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    Map<String, Object> jsonRepresentation() {
        Map<String, Object> json = new HashMap<>(getProperties());
        json.put(kC4ObjectTypeProperty, kC4ObjectType_Blob);
        return json;
    }

    void installInDatabase(Database db) {
        if (db == null)
            throw new IllegalArgumentException("database is null");

        if (this.database != null) {
            if (this.database != db)
                throw new IllegalArgumentException("Blob belongs to a different database");
            return;
        }

        C4BlobKey key = null;
        try {
            C4BlobStore store = db.getBlobStore();
            try {
                if (content != null) {
                    key = store.create(content);
                } else {
                    C4BlobWriteStream blobOut = store.openWriteStream();
                    try {
                        byte[] buffer = new byte[MAX_CACHED_CONTENT_LENGTH];
                        int bytesRead = 0;
                        this.length = 0;
                        InputStream contentStream = getContentStream();
                        try {
                            while ((bytesRead = contentStream.read(buffer)) > 0) {
                                this.length += bytesRead;
                                blobOut.write(Arrays.copyOfRange(buffer, 0, bytesRead));
                            }
                        } finally {
                            try {
                                if (contentStream != null)
                                    contentStream.close();
                            } catch (IOException e) {
                            }
                        }
                        key = blobOut.computeBlobKey();
                        blobOut.install();
                    } finally {
                        blobOut.close();
                    }
                }
                this.digest = key.toString();
                this.database = db;
            } finally {
                if (store != null)
                    store.free();
            }
        } catch (LiteCoreException e) {
            throw LiteCoreBridge.convertRuntimeException(e);
        } catch (IOException ioe) {
            throw new CouchbaseLiteRuntimeException(ioe);
        } finally {
            if (key != null)
                key.free();
        }
    }

    //FLEncodable
    @Override
    public void encodeTo(FLEncoder encoder) {
        // TODO: error handling??
        Object obj = encoder.getExtraInfo();
        if (obj != null) {
            MutableDocument doc = (MutableDocument) obj;
            Database db = doc.getDatabase();
            installInDatabase(db);
        }

        Map<String, Object> dict = jsonRepresentation();
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
            Log.w(TAG, "database instance is null.");
            return null;
        }
        try {
            return database.getBlobStore();
        } catch (RuntimeException e) {
            Log.w(TAG, "Failed to get BlobStore instance", e);
            return null;
        }
    }

    private C4BlobKey getBlobKey() {
        try {
            return new C4BlobKey(digest);
        } catch (LiteCoreException e) {
            Log.w(TAG, "Invalid digest: " + digest, e);
            return null;
        }
    }

    static final class BlobInputStream extends InputStream {
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
}
