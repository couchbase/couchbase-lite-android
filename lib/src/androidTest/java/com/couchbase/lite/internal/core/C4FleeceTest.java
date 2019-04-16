//
// C4FleeceTest.java
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
package com.couchbase.lite.internal.core;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLValue;

import static com.couchbase.lite.internal.fleece.FLConstants.ValueType.DATA;
import static com.couchbase.lite.internal.fleece.FLConstants.ValueType.DICT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


public class C4FleeceTest extends C4BaseTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testEncodeBytes() throws LiteCoreException {
        byte[] input = "Hello World!".getBytes();

        FLEncoder enc = new FLEncoder();
        try {
            enc.writeData(input);
            byte[] optionsFleece = enc.finish();
            assertNotNull(optionsFleece);

            FLValue value = FLValue.fromData(optionsFleece);
            assertNotNull(value);
            assertEquals(DATA, value.getType());
            byte[] output = value.asData();
            assertNotNull(output);
            Assert.assertArrayEquals(input, output);
        }
        finally {
            enc.free();
        }
    }

    @Test
    public void testEncodeMapWithBytes() throws LiteCoreException {
        byte[] input = "Hello World!".getBytes();
        Map<String, Object> map = new HashMap<>();
        map.put("bytes", input);

        FLEncoder enc = new FLEncoder();
        try {
            enc.write(map);
            byte[] optionsFleece = enc.finish();
            assertNotNull(optionsFleece);

            FLValue value = FLValue.fromData(optionsFleece);
            assertNotNull(value);
            assertEquals(DICT, value.getType());
            Map<String, Object> map2 = value.asDict();
            assertNotNull(map2);
            assertTrue(map2.containsKey("bytes"));
            byte[] output = (byte[]) map2.get("bytes");
            assertNotNull(output);
            Assert.assertArrayEquals(input, output);
        }
        finally {
            enc.free();
        }
    }
}
