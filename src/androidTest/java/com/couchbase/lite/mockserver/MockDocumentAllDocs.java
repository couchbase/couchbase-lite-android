/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite.mockserver;


import com.couchbase.lite.Manager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Created by hideki on 2/11/16.
 */
public class MockDocumentAllDocs implements SmartMockResponse {

    /* http://docs.couchdb.org/en/2.0.0/api/database/bulk-api.html

{
  "offset": 0,
  "rows": [
    {
      "id": "16e458537602f5ef2a710089dffd9453",
      "key": "16e458537602f5ef2a710089dffd9453",
      "value": {
        "rev": "1-967a00dff5e02add41819138abb3284d"
      }
    },
    {
      "id": "a4c51cdfa2069f3e905c431114001aff",
      "key": "a4c51cdfa2069f3e905c431114001aff",
      "value": {
        "rev": "1-967a00dff5e02add41819138abb3284d"
      }
    },
    {
      "id": "a4c51cdfa2069f3e905c4311140034aa",
      "key": "a4c51cdfa2069f3e905c4311140034aa",
      "value": {
        "rev": "5-6182c9c954200ab5e3c6bd5e76a1549f"
      }
    },
    {
      "id": "a4c51cdfa2069f3e905c431114003597",
      "key": "a4c51cdfa2069f3e905c431114003597",
      "value": {
        "rev": "2-7051cbe5c8faecd085a3fa619e6e6337"
      }
    },
    {
      "id": "f4ca7773ddea715afebc4b4b15d4f0b3",
      "key": "f4ca7773ddea715afebc4b4b15d4f0b3",
      "value": {
        "rev": "2-7051cbe5c8faecd085a3fa619e6e6337"
      }
    }
  ],
  "total_rows": 5
}
     */

    boolean sticky = false;

    List<MockDocumentGet.MockDocument> mockDocs = new ArrayList<MockDocumentGet.MockDocument>();

    public MockDocumentAllDocs(List<MockDocumentGet.MockDocument> mockDocs) {
        this.mockDocs.addAll(mockDocs);
    }

    public MockDocumentAllDocs() {
    }

    public MockResponse generateMockResponse(RecordedRequest request) {
        Map<String, Object> respDict = new HashMap<String, Object>();
        respDict.put("offset", 0);
        respDict.put("total_rows", mockDocs.size());
        List<Object> rows = new ArrayList<Object>();
        for(MockDocumentGet.MockDocument mockDoc: mockDocs){
            Map<String, Object> doc = new HashMap<String, Object>();
            doc.put("id", mockDoc.getDocId());
            doc.put("key", mockDoc.getDocId());
            Map<String, Object> value = new HashMap<String, Object>();
            value.put("rev", mockDoc.getDocRev());
            doc.put("value", value);
            if(false){
                doc.put("doc", mockDoc.getJsonMap());
            }
            rows.add(doc);
        }
        respDict.put("rows", rows);

        try {
            MockResponse mockResponse = new MockResponse();
            mockResponse.setBody(Manager.getObjectMapper().writeValueAsString(respDict));
            mockResponse.setStatus("HTTP/1.1 200 OK");
            return mockResponse;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isSticky() {
        return sticky;
    }

    public void setSticky(boolean sticky){
        this.sticky = sticky;
    }

    @Override
    public long delayMs() {
        return 0;
    }
}