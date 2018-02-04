//
// LiteCoreBridgeTest.java
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

import com.couchbase.litecore.LiteCoreException;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class LiteCoreBridgeTest {
    @Test
    public void testConvertRuntimeException() {
        LiteCoreException orgEx = new LiteCoreException(1, 2, "3");
        CouchbaseLiteRuntimeException ex = LiteCoreBridge.convertRuntimeException(orgEx);
        assertNotNull(ex);
        assertEquals(1, ex.getDomain());
        assertEquals(2, ex.getCode());
        assertEquals("3", ex.getMessage());
        assertEquals(orgEx, ex.getCause());
    }
}
