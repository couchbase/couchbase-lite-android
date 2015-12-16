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

/**
 * Created by hideki on 12/11/15.
 */
public class SmartMockResponseImpl implements SmartMockResponse {
    private MockResponse mockResponse;
    private boolean isSticky;
    private long delayMs;

    public SmartMockResponseImpl(MockResponse mockResponse) {
        this.mockResponse = mockResponse;
    }

    @Override
    public MockResponse generateMockResponse(RecordedRequest request) {
        return mockResponse;
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

    public void setDelayMs(long delayMs) {
        this.delayMs = delayMs;
    }
}
