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

import com.squareup.okhttp.mockwebserver.MockResponse;
import com.squareup.okhttp.mockwebserver.RecordedRequest;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*

    Generate a _bulk_get response, eg

    Request:
    --------

    {"docs":[
      {"atts_since":null,"id":"48c2868b-ec8b","rev":"1-dd9df9"},
      {"atts_since":null,"id":"53a11c38-d729","rev":"1-dd9df9"},
      ..
    ]}

    Response:
    ---------

    Headers:

    Content Type: multipart/mixed; boundary="77d6fa0c04b501832f5dcf93d515cb73f39fa74104b01bc5a5b24dd57f4a"

    Body:

    --77d6fa0c04b501832f5dcf93d515cb73f39fa74104b01bc5a5b24dd57f4a
    Content-Type: application/json

    {"_deleted":true,"_id":"6f0ac441-9092-45e5-82db-c76be5bd0e48","_rev":"4-4e06228ca6233e67c363a6a2c48e10bd","_revisions":{"ids":["4e06228ca6233e67c363a6a2c48e10bd","3a6249a96cd3fbceaff91d8a3b182a0f","c709c454aaf733264d332d4fd5132af5","c4103f122d27677c9db144cae1394a66"],"start":4}}
    --77d6fa0c04b501832f5dcf93d515cb73f39fa74104b01bc5a5b24dd57f4a
    X-Doc-Id: doc1-1407952481755
    X-Rev-Id: 76-8fbee7ae13afadf9fe81d1d0831930cc
    Content-Type: multipart/related; boundary="aff16d5e5921568ad8fab41b9ec3c2dc86390da4f9dbbc2cec7bf5e2655f"

    --aff16d5e5921568ad8fab41b9ec3c2dc86390da4f9dbbc2cec7bf5e2655f
    Content-Type: application/json

    {"_attachments":{"attachment.png":{"content_type":"image/png","digest":"sha1-LmsoqJJ6LOn4YS60pYnvrKbBd64=","follows":true,"length":519173,"revpos":1}},"_id":"doc1-1407952481755","_rev":"76-8fbee7ae13afadf9fe81d1d0831930cc","_revisions":{"ids":["8fbee7ae13afadf9fe81d1d0831930cc"],"start":76},"checked":false,"created_at":"","list_id":"","ocr_decoded":" \n\n","title":"","type":""}
    --aff16d5e5921568ad8fab41b9ec3c2dc86390da4f9dbbc2cec7bf5e2655f
    Content-Type: image/png
    Content-Disposition: attachment; filename="attachment.png"

    PNG
    --aff16d5e5921568ad8fab41b9ec3c2dc86390da4f9dbbc2cec7bf5e2655f--
    --77d6fa0c04b501832f5dcf93d515cb73f39fa74104b01bc5a5b24dd57f4a--
    ...

 */
public class MockDocumentBulkGet implements SmartMockResponse {

    private Map<String, MockDocumentGet.MockDocument> documents;
    private boolean isSticky;
    private long delayMs;
    private boolean is404 = false;

    public MockDocumentBulkGet() {
        this.documents = new HashMap<String, MockDocumentGet.MockDocument>();
    }

    @Override
    public MockResponse generateMockResponse(RecordedRequest request) {

        if (is404) {
            MockResponse mockResponse = new MockResponse();
            MockHelper.set404NotFoundJson(mockResponse);
            return mockResponse;
        }

        try {
            byte[] body = MockHelper.getUncompressedBody(request);
            Map<String, Object> jsonMap = MockHelper.getJsonMapFromRequest(body);
            List docs = (List)jsonMap.get("docs");

            MockResponse mockResponse = new MockResponse();

            String boundary = "77d6fa0c04b501832f5dcf93d515cb73f39fa74104b01bc5a5b24dd57f4a";
            String contentType = String.format("multipart/mixed; boundary=\"%s\"", boundary);
            mockResponse.setHeader("Content-Type", contentType);

            ByteArrayOutputStream o = new ByteArrayOutputStream();


            /*
                    byte[] mime = new String("--BOUNDARY\r\nFoo: Bar\r\n Header : Val ue \r\n\r\npart the first\r\n--BOUNDARY  \r\n\r\n2nd part\r\n--BOUNDARY--").getBytes(utf8);

             */

            int i = 0;
            for (Object doc : docs) {
                Map<String, Object> documentMap = (Map) doc;
                String docId = (String) documentMap.get("id");
                MockDocumentGet.MockDocument mockDocument = documents.get(docId);
                if(mockDocument == null)
                    continue;
                boolean addEmptyLine = false;
                if (i > 0) {
                    addEmptyLine = true;
                }
                addMockDocument(o, mockDocument, boundary, addEmptyLine);
                i += 1;
            }

            // final boundary to mark end
            append(o, "\r\n").append(o, "--").append(o, boundary).append(o, "--");

            byte[] byteArray = o.toByteArray();

            mockResponse.setBody(byteArray);

            mockResponse.setStatus("HTTP/1.1 200 OK");

            return mockResponse;

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }


    }

    private void addMockDocument(ByteArrayOutputStream o, MockDocumentGet.MockDocument mockDocument, String boundary, boolean addEmptyLine) throws Exception {

        boolean attachmentFollows = true;

        if (addEmptyLine) {
            append(o, "\r\n");
        }

        append(o, "--").append(o, boundary).append(o, "\r\n");

        if(mockDocument.isMissing()){
            // missing revision
            append(o, "Content-Type: application/json; error=\"true\"");
            append(o, "\r\n\r\n");
            append(o, mockDocument.generateDocumentBody(attachmentFollows));
            append(o, "\r\n");
        }
        else if (!mockDocument.hasAttachment()) {
            // no attachment, add json part
            append(o, "Content-Type: application/json");
            append(o, "\r\n\r\n");
            append(o, mockDocument.generateDocumentBody(attachmentFollows));
            append(o, "\r\n");
        } else {
            // has attachment

            // headers
            append(o, "X-Doc-Id: ").append(o, mockDocument.getDocId()).append(o, "\r\n");
            append(o, "X-Rev-Id: ").append(o, mockDocument.getDocRev()).append(o, "\r\n");
            String innerBoundary = "aff16d5e5921568ad8fab41b9ec3c2dc86390da4f9dbbc2cec7bf5e2655f";
            String innerContentType = String.format("multipart/related; boundary=\"%s\"", innerBoundary);
            append(o, "Content-Type: ").append(o, innerContentType);
            append(o, "\r\n\r\n");

            // boundary
            append(o, "--").append(o, innerBoundary).append(o, "\r\n");

            // json part
            append(o, "Content-Type: application/json");
            append(o, "\r\n\r\n");
            append(o, mockDocument.generateDocumentBody(attachmentFollows));
            append(o, "\r\n");

            // boundary
            append(o, "--").append(o, innerBoundary).append(o, "\r\n");

            // image part
            append(o, "Content-Type: image/png");
            append(o, "\r\n");
            String contentDisposition = String.format("Content-Disposition: attachment; filename=\"%s\"", mockDocument.getAttachmentName());
            append(o, contentDisposition);
            append(o, "\r\n\r\n");

            // image data
            byte[] attachmentBytes = MockDocumentGet.getAssetByteArray(mockDocument.getAttachmentName());
            o.write(attachmentBytes);

            // final boundary to mark end of part
            append(o, "\r\n").append(o, "--").append(o, innerBoundary).append(o, "--");
        }
    }

    private MockDocumentBulkGet append(ByteArrayOutputStream baos, String string) throws Exception {
        baos.write(string.getBytes("UTF-8"));
        return this;
    }

    public void addDocument(MockDocumentGet.MockDocument mockDocument) {
        this.documents.put(mockDocument.getDocId(), mockDocument);
    }

    @Override
    public boolean isSticky() {
        return this.isSticky;
    }

    @Override
    public long delayMs() {
        return delayMs;
    }

    public void setSticky(boolean isSticky) {
        this.isSticky = isSticky;
    }

    public long getDelayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }


    public void set404(boolean is404) {
        this.is404 = is404;
    }
}
