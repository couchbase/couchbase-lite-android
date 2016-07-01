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
package com.couchbase.lite.auth;

import com.couchbase.lite.LiteTestCase;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by hideki on 6/15/16.
 */
public class OpenIDConnectAuthorizerTest extends LiteTestCase {
    private OpenIDConnectAuthorizer authorizer;
    
    @Override
    public void setUp() throws Exception {
        super.setUp();
        authorizer = new com.couchbase.lite.auth.OpenIDConnectAuthorizer(
                new OIDCLoginCallback() {
                    @Override
                    public void callback(URL loginURL, URL authBaseURL,
                                         OIDCLoginContinuation loginContinuation) {
                    }
                }, TokenStoreFactory.build(getTestContext("db")));
        authorizer.setRemoteURL(new URL("http://10.0.0.1:9999/db"));
    }

    public void testTokens() throws Exception {
        assertFalse(authorizer.loadTokens());
        Map<String, String> tokens = new HashMap<>();
        tokens.put("id_token", "1234567890");
        tokens.put("refresh_token", "abcdefghij");
        tokens.put("name", "cbl");
        tokens.put("session_id", "1a2b3c4d5e");
        assertTrue(authorizer.saveTokens(tokens));
        assertTrue(authorizer.loadTokens());
        assertEquals("cbl", authorizer.getUsername());
        assertTrue(authorizer.deleteTokens());
    }
}
