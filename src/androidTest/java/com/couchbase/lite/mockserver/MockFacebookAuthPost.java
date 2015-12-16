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

public class MockFacebookAuthPost {

    public MockResponse generateMockResponse() {
        MockResponse mockResponse = new MockResponse();
        String jsonBody = "{\n" +
                "   \"error\":\"Unauthorized\",\n" +
                "   \"reason\":\"Facebook verification server status 400\"\n" +
                "}";
        mockResponse.setBody(jsonBody);
        mockResponse.setStatus("HTTP/1.1 401 Unauthorized").setHeader("Content-Type", "application/json");
        return mockResponse;
    }

    public MockResponse generateMockResponseForSuccess(String email) {
        /**
         * Facebook actual response
         *
        HTTP/1.1 200 OK
        Date: Mon, 22 Dec 2014 22:16:31 GMT
        Content-Type: application/json
        Content-Length: 568
        Connection: keep-alive
        Server: Couchbase Sync Gateway/1.00
        Set-Cookie: SyncGatewaySession=015f9770ebe9968ae04306f591d38e12c1391886; Path=/todolite/; Expires=Tue, 23 Dec 2014 22:16:31 UTC

        {"authentication_handlers":["default","cookie"],"ok":true,"userCtx":{"channels":{"list-00F9B6FC-7B2C-47A8-9E4C-0855E1468A96":376,"list-07FB5332-7D6E-4B43-9C4B-A3C48F6548A4":57408,"list-0919487F-1D3D-41CE-88A0-C3F08BEFFF0D":381,"list-4290A9F4-0BCD-4C49-BEAF-4177B7585CD7":57507,"list-66ece56f-2422-4d4a-8858-583ddb665ec5":58528,"list-67C0AD63-6946-42AC-BF2A-3CAE7DCDD1C4":57761,"list-7E0A551D-13CC-4008-8749-125CC11E2F98":59284,"list-DF3A5540-8A99-4222-9C77-74D83DE2B8A6":428,"list-E5C26632-4E5A-46F0-9C97-C09698BF20D5":57801,"profiles":375},"name":"myfacebook@gmail.com"}}
         */
        MockResponse mockResponse = new MockResponse();
        String jsonBody = "{\"name\":\""+email+"\"}";
        mockResponse.setBody(jsonBody);
        mockResponse.setStatus("HTTP/1.1 200 OK").setHeader("Content-Type", "application/json");
        return mockResponse;
    }
}
