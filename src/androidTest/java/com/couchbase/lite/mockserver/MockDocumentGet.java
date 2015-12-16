/**
 * Copyright (c) 2015 Couchbase, Inc. All rights reserved.
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
package com.couchbase.lite.mockserver;

import com.couchbase.lite.BlobKey;
import com.couchbase.lite.BlobStore;
import com.couchbase.lite.Manager;
import com.couchbase.lite.support.Base64;
import com.couchbase.org.apache.http.entity.mime.MultipartEntity;
import com.couchbase.org.apache.http.entity.mime.content.InputStreamBody;
import com.couchbase.org.apache.http.entity.mime.content.StringBody;
import com.squareup.okhttp.mockwebserver.MockResponse;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
    Generate mock document GET response, eg

    {
       "_id":"doc1-1402588904847",
       "_rev":"1-d57b1bc60eb9273c3349d932e15f9949",
       "_revisions":{
          "ids":[
             "d57b1bc60eb9273c3349d932e15f9949"
          ],
          "start":1
       },
       "bar":false,
       "foo":1
    }

    Limitations: it cannot represent docs with revision histories longer
    than one rev.
 */
public class MockDocumentGet {

    private String docId;
    private String rev;
    private Map<String, Object> jsonMap;
    private boolean includeAttachmentPart;

    // you can optionally supply a revHistoryMap, otherwise
    // a simple default rev history will be generated.
    private Map<String, Object> revHistoryMap;

    // a corresponding file must be in the /assets/ directory
    private List<String> attachmentFileNames;

    public MockDocumentGet() {
        attachmentFileNames = new ArrayList<String>();
        this.revHistoryMap = new HashMap<String, Object>();
        this.includeAttachmentPart = true;
    }

    public MockDocumentGet(MockDocument mockDocument) {
        this();
        this.docId = mockDocument.getDocId();
        this.rev = mockDocument.getDocRev();
        this.jsonMap = mockDocument.getJsonMap();
    }

    public Map<String, Object> getRevHistoryMap() {
        return revHistoryMap;
    }

    public void setRevHistoryMap(Map<String, Object> revHistoryMap) {
        this.revHistoryMap = revHistoryMap;
    }

    public String getDocId() {
        return docId;
    }

    public MockDocumentGet setDocId(String docId) {
        this.docId = docId;
        return this;
    }

    public String getRev() {
        return rev;
    }

    public MockDocumentGet setRev(String rev) {
        this.rev = rev;
        return this;
    }

    public void addAttachmentFilename(String attachmentFilename) {
        attachmentFileNames.add(attachmentFilename);
    }

    public Map<String, Object> getJsonMap() {
        return jsonMap;
    }

    public MockDocumentGet setJsonMap(Map<String, Object> jsonMap) {
        this.jsonMap = jsonMap;
        return this;
    }

    private Map<String, Object> generateRevHistoryMap() {
        if (revHistoryMap.isEmpty()) {
            Map<String, Object> simpleRevHIstoryMap = new HashMap<String, Object>();
            // parse rev into components, eg
            String[] revComponents = rev.split("-");
            String numericPrefixStr = revComponents[0];  // eg, "1"
            String digest = revComponents[1]; // eg, "d57b1bc60eb9273c3349d932e15f9949"
            int numericPrefix = Integer.parseInt(numericPrefixStr);
            List<String> revHistoryDigestIds = new ArrayList<String>();
            revHistoryDigestIds.add(digest);
            simpleRevHIstoryMap.put("ids", revHistoryDigestIds);
            simpleRevHIstoryMap.put("start", numericPrefix);
            return simpleRevHIstoryMap;
        } else {
            return revHistoryMap;
        }
    }

    private Map<String, Object> generateDocumentMap() {
        Map<String, Object> docMap = new HashMap<String, Object>();
        docMap.put("_id", getDocId());
        docMap.put("_rev", getRev());
        docMap.put("_revisions", generateRevHistoryMap());
        if (!attachmentFileNames.isEmpty()) {
            docMap.put("_attachments", generateAttachmentsMap());
        }
        docMap.putAll(jsonMap);
        return docMap;
    }

    /*
       {
           "_attachments":{
              "attachment3.png":{
                 "content_type":"image/png",
                 "digest":"sha1-nKikeP2tQRpJCHOpS4w7G+Kc12Y=",
                 "follows":true,
                 "length":19693,
                 "revpos":1
              }
           },
           "_id":"...",
           ...
       }
     */
    private Map<String, Object> generateAttachmentsMap() {
        Map<String, Object> attachmentsMap = new HashMap<String, Object>();
        for (String attachmentName : attachmentFileNames) {
            Map<String, Object> attachmentMap = new HashMap<String, Object>();
            if (attachmentName.endsWith("png")) {
                attachmentMap.put("content_type", "image/png");
            } else {
                throw new RuntimeException("Only png files are supported as test attachemnts");
            }
            attachmentMap.put("digest", calculateSha1Digest(attachmentName));
            if (this.isIncludeAttachmentPart()) {
                attachmentMap.put("follows", true);
            } else {
                attachmentMap.put("stub", true);
            }
            attachmentMap.put("length", MockDocumentGet.getAssetByteArray(attachmentName).length);
            attachmentMap.put("revpos", 1);

            attachmentsMap.put(attachmentName, attachmentMap);

        }
        return attachmentsMap;
    }

    public static String calculateSha1Digest(String attachmentAssetName) {
        byte[] attachmentBytes = MockDocumentGet.getAssetByteArray(attachmentAssetName);
        BlobKey blobKey = BlobStore.keyForBlob(attachmentBytes);
        String base64Sha1Digest = Base64.encodeBytes(blobKey.getBytes());
        String sha1 = String.format("sha1-%s", base64Sha1Digest);
        return sha1;
    }

    private String generateDocumentBody() {
        Map documentMap = generateDocumentMap();
        try {
            return Manager.getObjectMapper().writeValueAsString(documentMap);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public MockResponse generateMockResponse() {

        MockResponse mockResponse = new MockResponse();
        if (attachmentFileNames.isEmpty()) {
            mockResponse.setBody(generateDocumentBody());
            mockResponse.setHeader("Content-Type", "application/json");

        } else {
            createMultipartResponse(mockResponse);
        }

        mockResponse.setStatus("HTTP/1.1 200 OK");

        return mockResponse;
    }

    private void createMultipartResponse(MockResponse mockResponse) {

        InputStream stream;
        ByteArrayOutputStream baos;

        try {

            MultipartEntity multiPart = new MultipartEntity();
            String partNameIgnored = "part";  // this never seems to appear anywhere in response
            multiPart.addPart(partNameIgnored, new StringBody(generateDocumentBody(),
                    "application/json", Charset.forName("UTF-8")));

            for (String attachmentName : attachmentFileNames) {

                byte[] attachmentBytes = getAssetByteArray(attachmentName);

                int contentLength = attachmentBytes.length;

                if (this.isIncludeAttachmentPart()) {
                    multiPart.addPart(attachmentName, new InputStreamBody(
                            new ByteArrayInputStream(attachmentBytes),
                            "image/png", attachmentName, contentLength));
                }

                baos = new ByteArrayOutputStream();
                multiPart.writeTo(baos);

                mockResponse.setHeader(multiPart.getContentType().getName(),
                        multiPart.getContentType().getValue());

                byte[] body = baos.toByteArray();
                mockResponse.setBody(body);

                String bodyString = new String(body);

                baos.close();
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static InputStream getAsset(String name) {
        MockDocumentGet o = new MockDocumentGet();
        return o.getClass().getResourceAsStream("/assets/" + name);
    }

    public static byte[] getAssetByteArray(String name) {
        try {
            InputStream attachmentStream = getAsset(name);
            ByteArrayOutputStream attachmentBaos = new ByteArrayOutputStream();
            IOUtils.copy(attachmentStream, attachmentBaos);
            byte[] attachmentBytes = attachmentBaos.toByteArray();
            attachmentStream.close();
            attachmentBaos.close();
            return attachmentBytes;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isIncludeAttachmentPart() {
        return includeAttachmentPart;
    }

    public void setIncludeAttachmentPart(boolean includeAttachmentPart) {  // TOO: rename to isStubbed()
        this.includeAttachmentPart = includeAttachmentPart;
    }

    public static class MockDocument {

        private String docId;
        private String docRev;
        private int docSeq;
        private String attachmentName;
        private Map<String, Object> jsonMap;
        private boolean missing = false;

        public MockDocument(String docId, String docRev, int docSeq) {
            this.docId = docId;
            this.docRev = docRev;
            this.docSeq = docSeq;
        }

        public MockDocument(String docId, String docRev, int docSeq, boolean missing) {
            this.docId = docId;
            this.docRev = docRev;
            this.docSeq = docSeq;
            this.missing = missing;
        }

        /**
         * TODO: the MockDocumentGet.generateDocumentMap() method should
         * TODO: be refactored to use this, but first the revision history
         * TODO: will need to be moved to this object.
         */
        private Map<String, Object> generateDocumentMap(boolean attachmentFollows) {
            Map<String, Object> docMap = new HashMap<String, Object>();
            if (missing) {
                docMap.put("id", getDocId());
                docMap.put("rev", getDocRev());
                docMap.put("error", "not_found");
                docMap.put("reason", "missing");
                docMap.put("status", 404);
            } else {
                docMap.put("_id", getDocId());
                docMap.put("_rev", getDocRev());
                if (hasAttachment()) {
                    docMap.put("_attachments", generateAttachmentsMap(attachmentFollows));
                }
                docMap.putAll(jsonMap);
            }
            return docMap;
        }

        /**
         * TODO: the MockDocumentGet.generateDocumentMap() method should
         * TODO: be refactored to use this.
         */
        public String generateDocumentBody(boolean attachmentFollows) {
            Map documentMap = generateDocumentMap(attachmentFollows);
            try {
                return Manager.getObjectMapper().writeValueAsString(documentMap);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * TODO: the MockDocumentGet.generateDocumentMap() method should
         * TODO: be refactored to use this, but first need to remove
         * TODO: attachmentFileNames field from MockDocumentGet
         */
        private Map<String, Object> generateAttachmentsMap(boolean attachmentFollows) {
            Map<String, Object> attachmentsMap = new HashMap<String, Object>();
            Map<String, Object> attachmentMap = new HashMap<String, Object>();
            if (attachmentName.endsWith("png")) {
                attachmentMap.put("content_type", "image/png");
            } else {
                throw new RuntimeException("Only png files are supported as test attachemnts");
            }
            attachmentMap.put("digest", calculateSha1Digest(attachmentName));
            if (attachmentFollows) {
                attachmentMap.put("follows", true);
            } else {
                attachmentMap.put("stub", true);
            }
            attachmentMap.put("length", MockDocumentGet.getAssetByteArray(attachmentName).length);
            attachmentMap.put("revpos", 1);

            attachmentsMap.put(attachmentName, attachmentMap);
            return attachmentsMap;
        }


        public Map<String, Object> getJsonMap() {
            return jsonMap;
        }

        public void setJsonMap(Map<String, Object> jsonMap) {
            this.jsonMap = jsonMap;
        }

        public String getAttachmentName() {
            return attachmentName;
        }

        public void setAttachmentName(String attachmentName) {
            this.attachmentName = attachmentName;
        }

        public boolean hasAttachment() {
            if (this.attachmentName != null && this.attachmentName.length() > 0) {
                return true;
            }
            return false;
        }

        public String getDocPathRegex() {
            return String.format("/db/%s\\?.*", getDocId());
        }

        public String getDocId() {
            return docId;
        }

        public void setDocId(String docId) {
            this.docId = docId;
        }

        public String getDocRev() {
            return docRev;
        }

        public void setDocRev(String docRev) {
            this.docRev = docRev;
        }

        public int getDocSeq() {
            return docSeq;
        }

        public void setDocSeq(int docSeq) {
            this.docSeq = docSeq;
        }

        public boolean isMissing() {
            return missing;
        }

        public void setMissing(boolean missing) {
            this.missing = missing;
        }
    }
}
