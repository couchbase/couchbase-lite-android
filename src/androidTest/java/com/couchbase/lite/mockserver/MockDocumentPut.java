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

import com.couchbase.lite.Manager;
import com.squareup.okhttp.mockwebserver.MockResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/*

    Generate mock document PUT response, eg

    {
       "id":"doc2-1403051744202",
       "ok":true,
       "rev":"2-6aa304118ef11cf427ec90ea9ba7ec09"
    }

    This could be completely "dynamic" and generated from the request.
    It would need to support parsing multipart requests (or use a regex).
    So for now, it just takes the id and rev as params.

 */
public class MockDocumentPut {

    private String docId;
    private String rev;

    public String getDocId() {
        return docId;
    }

    public MockDocumentPut setDocId(String docId) {
        this.docId = docId;
        return this;
    }

    public String getRev() {
        return rev;
    }

    public MockDocumentPut setRev(String rev) {
        this.rev = rev;
        return this;
    }

    private Map<String, Object> generateDocumentMap() {
        Map<String, Object> docMap = new HashMap<String, Object>();
        docMap.put("id", getDocId());
        docMap.put("ok", true);
        docMap.put("rev", getRev());
        return docMap;
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
        mockResponse.setBody(generateDocumentBody());
        MockHelper.set200OKJson(mockResponse);

        return mockResponse;
    }


}
