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

public class WrappedSmartMockResponse implements SmartMockResponse {

    private MockResponse mockResponse;
    private long delayMs;
    private boolean sticky;


    public WrappedSmartMockResponse(MockResponse mockResponse) {
        this.mockResponse = mockResponse;
    }

    public WrappedSmartMockResponse(MockResponse mockResponse, boolean sticky) {
        this.mockResponse = mockResponse;
        this.sticky = sticky;
    }

    @Override
    public MockResponse generateMockResponse(RecordedRequest request) {
        return mockResponse;
    }

    @Override
    public boolean isSticky() {
        return this.sticky;
    }

    public void setSticky(boolean sticky) {
        this.sticky = sticky;
    }

    @Override
    public long delayMs() {
        return delayMs;
    }

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }
}
