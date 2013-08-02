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

import java.util.HashMap;
import java.util.Map;

/**
 * Stores information about a revision -- its docID, revID, and whether it's deleted.
 *
 * It can also store the sequence number and document contents (they can be added after creation).
 */
public class CBLRevision {

    private String docId;
    private String revId;
    private boolean deleted;
    private CBLBody body;
    private long sequence;
    private CBLDatabase database;

    public CBLRevision(String docId, String revId, boolean deleted, CBLDatabase database) {
        this.docId = docId;
        this.revId = revId;
        this.deleted = deleted;
        this.database = database;
    }

    public CBLRevision(CBLBody body, CBLDatabase database) {
        this((String)body.getPropertyForKey("_id"),
                (String)body.getPropertyForKey("_rev"),
                (((Boolean)body.getPropertyForKey("_deleted") != null)
                        && ((Boolean)body.getPropertyForKey("_deleted") == true)), database);
        this.body = body;
    }

    public CBLRevision(Map<String, Object> properties, CBLDatabase database) {
        this(new CBLBody(properties), database);
    }

    public Map<String,Object> getProperties() {
        Map<String,Object> result = null;
        if(body != null) {
            result = body.getProperties();
        }
        return result;
    }

    public void setProperties(Map<String,Object> properties) {
        this.body = new CBLBody(properties);

        // this is a much more simplified version that what happens on the iOS.  it was
        // done this way due to time constraints, so at some point this needs to be
        // revisited to port the remaining functionality.
        Map<String, Object> attachments = (Map<String, Object>) properties.get("_attachments");
        if (attachments != null && attachments.size() > 0) {
            for (String attachmentName : attachments.keySet()) {
                Map<String, Object> attachment = (Map<String, Object>) attachments.get(attachmentName);
                CBLStatus status = database.installPendingAttachment(attachment);
                if (status.isSuccessful() == false) {
                    String msg = String.format("Unable to install pending attachment: %s.  Status: %d", attachment.toString(), status.getCode());
                    throw new IllegalStateException(msg);
                }
            }

        }

    }



    public byte[] getJson() {
        byte[] result = null;
        if(body != null) {
            result = body.getJson();
        }
        return result;
    }

    public void setJson(byte[] json) {
        this.body = new CBLBody(json);
    }

    @Override
    public boolean equals(Object o) {
        boolean result = false;
        if(o instanceof CBLRevision) {
            CBLRevision other = (CBLRevision)o;
            if(docId.equals(other.docId) && revId.equals(other.revId)) {
                result = true;
            }
        }
        return result;
    }

    @Override
    public int hashCode() {
        return docId.hashCode() ^ revId.hashCode();
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getRevId() {
        return revId;
    }

    public void setRevId(String revId) {
        this.revId = revId;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public CBLBody getBody() {
        return body;
    }

    public void setBody(CBLBody body) {
        this.body = body;
    }

    public CBLRevision copyWithDocID(String docId, String revId) {
        assert((docId != null) && (revId != null));
        assert((this.docId == null) || (this.docId.equals(docId)));
        CBLRevision result = new CBLRevision(docId, revId, deleted, database);
        Map<String, Object> properties = getProperties();
        if(properties == null) {
            properties = new HashMap<String, Object>();
        }
        properties.put("_id", docId);
        properties.put("_rev", revId);
        result.setProperties(properties);
        return result;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public long getSequence() {
        return sequence;
    }

    @Override
    public String toString() {
        return "{" + this.docId + " #" + this.revId + (deleted ? "DEL" : "") + "}";
    }

    /**
     * Generation number: 1 for a new document, 2 for the 2nd revision, ...
     * Extracted from the numeric prefix of the revID.
     */
    public int getGeneration() {
        return generationFromRevID(revId);
    }

    public static int generationFromRevID(String revID) {
        int generation = 0;
        int dashPos = revID.indexOf("-");
        if(dashPos > 0) {
            generation = Integer.parseInt(revID.substring(0, dashPos));
        }
        return generation;
    }

}
