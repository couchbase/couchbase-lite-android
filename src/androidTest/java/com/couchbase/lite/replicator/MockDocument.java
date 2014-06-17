package com.couchbase.lite.replicator;

import com.couchbase.lite.BlobKey;
import com.couchbase.lite.BlobStore;
import com.couchbase.lite.Manager;
import com.couchbase.lite.Misc;
import com.couchbase.lite.support.Base64;
import com.couchbase.lite.util.Log;
import com.couchbase.lite.util.TextUtils;
import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import org.apache.commons.io.IOUtils;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


/*

    Generate mock document, eg

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
public class MockDocument {

    private String docId;
    private String rev;
    private Map<String, Object> jsonMap;

    // a corresponding file must be in the /assets/ directory
    private List<String> attachmentFileNames;

    public MockDocument() {
        attachmentFileNames = new ArrayList<String>();
    }

    public String getDocId() {
        return docId;
    }

    public MockDocument setDocId(String docId) {
        this.docId = docId;
        return this;
    }

    public String getRev() {
        return rev;
    }

    public MockDocument setRev(String rev) {
        this.rev = rev;
        return this;
    }

    public void addAttachmentFilename(String attachmentFilename) {
        attachmentFileNames.add(attachmentFilename);
    }

    public Map<String, Object> getJsonMap() {
        return jsonMap;
    }

    public MockDocument setJsonMap(Map<String, Object> jsonMap) {
        this.jsonMap = jsonMap;
        return this;
    }

    private Map<String, Object> generateRevHistoryMap() {
        Map<String, Object> revHistoryMap = new HashMap<String, Object>();
        // parse rev into components, eg
        String[] revComponents = rev.split("-");
        String numericPrefixStr = revComponents[0];  // eg, "1"
        String digest = revComponents[1]; // eg, "d57b1bc60eb9273c3349d932e15f9949"
        int numericPrefix = Integer.parseInt(numericPrefixStr);
        List<String> revHistoryDigestIds = new ArrayList<String>();
        revHistoryDigestIds.add(digest);
        revHistoryMap.put("ids", revHistoryDigestIds);
        revHistoryMap.put("start", numericPrefix);
        return revHistoryMap;
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
            attachmentMap.put("follows", true);
            attachmentMap.put("length", MockDocument.getAssetByteArray(attachmentName).length);
            attachmentMap.put("revpos", 1);

            attachmentsMap.put(attachmentName, attachmentMap);

        }
        return attachmentsMap;
    }

    private String calculateSha1Digest(String attachmentAssetName) {
        byte[] attachmentBytes = MockDocument.getAssetByteArray(attachmentAssetName);
        BlobKey blobKey = BlobStore.keyForBlob(attachmentBytes);
        String base64Sha1Digest = Base64.encodeBytes(blobKey.getBytes());
        String sha1 = String.format("sha1-%s", base64Sha1Digest);
        String otherSha1 = String.format("sha1-%s", Misc.TDHexSHA1Digest(attachmentBytes));
        return sha1;
        // return String.format("sha1-%s", Misc.TDHexSHA1Digest(attachmentBytes));
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
            multiPart.addPart(partNameIgnored, new StringBody(generateDocumentBody(), "application/json", Charset.forName("UTF-8")));

            for (String attachmentName : attachmentFileNames) {

                byte[] attachmentBytes = getAssetByteArray(attachmentName);

                int contentLength = attachmentBytes.length;

                multiPart.addPart(attachmentName, new InputStreamBody(new ByteArrayInputStream(attachmentBytes), "image/png", attachmentName, contentLength));

                baos = new ByteArrayOutputStream();
                multiPart.writeTo(baos);

                mockResponse.setHeader(multiPart.getContentType().getName(), multiPart.getContentType().getValue());

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
        MockDocument o = new MockDocument();
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





}
