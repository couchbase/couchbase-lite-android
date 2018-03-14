//
// URLEndpointTest.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.Assert.fail;

public class URLEndpointTest extends BaseTest {
    @Test
    public void testEmbeddedUserCredentialIsNotAllowed() throws URISyntaxException {

        // only username in user-info is ok.
        URI url = new URI("ws://user@couchbase.com/sg");
        new URLEndpoint(url);

        // containing password is not allowed
        url = new URI("ws://user:pass@couchbase.com/sg");
        try {
            new URLEndpoint(url);
            fail();
        } catch (IllegalArgumentException e) {
            // expected!
        }
    }
}
