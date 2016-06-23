//
// Copyright (c) 2016 Couchbase, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.mockserver;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * http://developer.couchbase.com/documentation/mobile/1.2/develop/references/sync-gateway/admin-rest-api/database-admin/put-db/index.html
 */
public class MockCreateDB implements SmartMockResponse {
    private boolean isSticky;

    @Override
    public MockResponse generateMockResponse(RecordedRequest request) {
        MockResponse mockResponse = new MockResponse();
        if (!request.getMethod().equals("PUT")) {
            // For PUT, always return 201
            mockResponse.setBody("{}");
            MockHelper.set201OKJson(mockResponse);
        } else {
            // For GET, always return 404
            MockHelper.set404NotFoundJson(mockResponse);
        }
        return mockResponse;
    }

    @Override
    public boolean isSticky() {
        return isSticky;
    }

    @Override
    public long delayMs() {
        return 0;
    }

    public void setSticky(boolean isSticky) {
        this.isSticky = isSticky;
    }
}
