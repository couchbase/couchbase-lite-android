package com.couchbase.cblite.support;

import android.util.Log;

import com.couchbase.cblite.CBLBlobStoreWriter;
import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLMisc;
import com.couchbase.cblite.CBLServer;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.ByteArrayBuffer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CBLMultipartDocumentReader implements CBLMultipartReaderDelegate {

    /** The response which contains the input stream we need to read from */
    private HttpResponse response;

    private CBLMultipartReader multipartReader;
    private CBLBlobStoreWriter curAttachment;
    private ByteArrayBuffer jsonBuffer;
    private Map<String, Object> document;
    private CBLDatabase database;
    private Map<String, CBLBlobStoreWriter> attachmentsByName;
    private Map<String, CBLBlobStoreWriter> attachmentsByMd5Digest;

    public CBLMultipartDocumentReader(HttpResponse response, CBLDatabase database) {
        this.response = response;
        this.database = database;
    }


    public Map<String, Object> getDocumentProperties() {
        return document;
    }

    public void parseJsonBuffer() {
        try {
            document = CBLServer.getObjectMapper().readValue(jsonBuffer.toByteArray(), Map.class);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to parse json buffer", e);
        }
    }

    public void setContentType(String contentType) {
        if (!contentType.startsWith("multipart/")) {
            throw new IllegalArgumentException("contentType must start with multipart/");
        }
        multipartReader = new CBLMultipartReader(contentType, this);
        attachmentsByName = new HashMap<String, CBLBlobStoreWriter>();
        attachmentsByMd5Digest = new HashMap<String, CBLBlobStoreWriter>();
    }

    public void appendData(byte[] data) {
        if (multipartReader != null) {
            multipartReader.appendData(data);
        }
        else {
            jsonBuffer.append(data, 0, data.length);
        }
    }

    public void finish() {
        if (multipartReader != null) {
            if (!multipartReader.finished()) {
                throw new IllegalStateException("received incomplete MIME multipart response");
            }

            registerAttachments();
        }
        else {
            parseJsonBuffer();
        }
    }

    private void registerAttachments() {

        int numAttachmentsInDoc = 0;

        Map<String, Object> attachments = (Map<String, Object>) document.get("_attachments");
        if (attachments == null) {
            return;
        }

        for (String attachmentName : attachments.keySet()) {
            Map<String, Object> attachment = (Map<String, Object>) attachments.get(attachmentName);

            int length = 0;
            if (attachment.containsKey("length")) {
                length = ((Integer)attachment.get("length")).intValue();
            }
            if (attachment.containsKey("encoded_length")) {
                length = ((Integer)attachment.get("encoded_length")).intValue();
            }

            if (attachment.containsKey("follows") &&
                    ((Boolean)attachment.get("follows")).booleanValue() == true) {

                // Check that each attachment in the JSON corresponds to an attachment MIME body.
                // Look up the attachment by either its MIME Content-Disposition header or MD5 digest:
                String digest = (String) attachment.get("digest");
                CBLBlobStoreWriter writer = attachmentsByName.get(attachmentName);
                if (writer != null) {
                    // Identified the MIME body by the filename in its Disposition header:
                    String actualDigest = writer.mD5DigestString();
                    if (digest != null &&
                            !digest.equals(actualDigest) &&
                            !digest.equals(writer.sHA1DigestString())) {
                        String errMsg = String.format("Attachment '%s' has incorrect MD5 digest (%s; should be %s)",
                                attachmentName, digest, actualDigest);
                        throw new IllegalStateException(errMsg);
                    }
                    attachment.put("digest", actualDigest);

                }
                else if (digest != null) {
                    writer = attachmentsByMd5Digest.get(digest);
                    if (writer == null) {
                        String errMsg = String.format("Attachment '%s' does not appear in MIME body (%s; should be %s)",
                                attachmentName);
                        throw new IllegalStateException(errMsg);
                    }
                }
                else if (attachments.size() == 1 && attachmentsByMd5Digest.size() == 1) {
                    // Else there's only one attachment, so just assume it matches & use it:
                    writer = attachmentsByMd5Digest.values().iterator().next();
                    attachment.put("digest", writer.mD5DigestString());
                }
                else {
                    // No digest metatata, no filename in MIME body; give up:
                    String errMsg = String.format("Attachment '%s' has no digest metadata; cannot identify MIME body",
                            attachmentName);
                    throw new IllegalStateException(errMsg);
                }

                // Check that the length matches:
                if (writer.getLength() != length) {
                    String errMsg = String.format("Attachment '%s' has incorrect length field %d (should be %d)",
                            attachmentName, length, writer.getLength());
                    throw new IllegalStateException(errMsg);
                }

                ++numAttachmentsInDoc;

            }
            else if (attachment.containsKey("data") && length > 1000) {
                String msg = String.format("Attachment '%s' sent inline (len=%d).  Large attachments " +
                        "should be sent in MIME parts for reduced memory overhead.", attachmentName);
                Log.w(CBLDatabase.TAG, msg);
            }

        }

        if (numAttachmentsInDoc < attachmentsByMd5Digest.size()) {
            String msg = String.format("More MIME bodies (%d) than attachments (%d) ",
                    attachmentsByMd5Digest.size(), numAttachmentsInDoc);
            throw new IllegalStateException(msg);
        }

        // hand over the (uninstalled) blobs to the database to remember:
        database.rememberAttachmentWritersForDigests(attachmentsByMd5Digest);

    }

    @Override
    public void startedPart(Map<String, String> headers) {

        if (document == null) {
           jsonBuffer = new ByteArrayBuffer(1024);
        }
        else {
            curAttachment = database.getAttachmentWriter();

            String contentDisposition = headers.get("Content-Disposition");
            if (contentDisposition.startsWith("attachment; filename=")) {
                // TODO: Parse this less simplistically. Right now it assumes it's in exactly the same
                // format generated by -[CBL_Pusher uploadMultipartRevision:]. CouchDB (as of 1.2) doesn't
                // output any headers at all on attachments so there's no compatibility issue yet.

                String contentDispositionUnquoted = CBLMisc.unquoteString(contentDisposition);
                String name = contentDispositionUnquoted.substring(21);

                if (name != null) {
                    attachmentsByName.put(name, curAttachment);
                }
            }
        }


    }

    @Override
    public void appendToPart(byte[] data) {
        if (jsonBuffer != null) {
            jsonBuffer.append(data, 0, data.length);
        }
        else {
            curAttachment.appendData(data);
        }
    }

    @Override
    public void finishedPart() {
        if (jsonBuffer != null) {
            parseJsonBuffer();
        }
        else {
            curAttachment.finish();
            String md5String = curAttachment.mD5DigestString();
            attachmentsByMd5Digest.put(md5String, curAttachment);
            curAttachment = null;
        }


    }
}
