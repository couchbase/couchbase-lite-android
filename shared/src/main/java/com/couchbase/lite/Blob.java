/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite;

import com.couchbase.lite.internal.bridge.LiteCoreBridge;
import com.couchbase.lite.internal.support.ClassUtils;
import com.couchbase.litecore.C4BlobKey;
import com.couchbase.litecore.C4BlobStore;
import com.couchbase.litecore.C4BlobWriteStream;
import com.couchbase.litecore.LiteCoreException;
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
public final class Blob implements FleeceEncodable {
    //---------------------------------------------
    // static constant variables
    //---------------------------------------------

    static final String TAG = Log.BLOB;
    static final String TYPE_META_PROPERTY = "_cbltype";
    static final String BLOB_TYPE = "blob";

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
        this.properties.remove(TYPE_META_PROPERTY);

        this.length = ClassUtils.cast(properties.get("length"), Number.class).longValue();
        this.digest = ClassUtils.cast(properties.get("digest"), String.class);
        this.contentType = ClassUtils.cast(properties.get("content-type"), String.class);
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
            props.put("content-type", contentType);
            return props;
        }
    }

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

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    Map<String, Object> jsonRepresentation() {
        Map<String, Object> json = new HashMap<>(getProperties());
        json.put(TYPE_META_PROPERTY, BLOB_TYPE);
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

    // FleeceEncodable implementation
    @Override
    public void fleeceEncode(FLEncoder encoder, Database database) {
        installInDatabase(database);

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
}
