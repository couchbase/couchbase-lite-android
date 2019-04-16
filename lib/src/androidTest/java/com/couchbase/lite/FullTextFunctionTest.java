//
// FullTextFunctionTest.java
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

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class FullTextFunctionTest {
    @Test
    public void testRank() {
        Expression expr = FullTextFunction.rank("abc");
        assertNotNull(expr);
        Object obj = expr.asJSON();
        assertNotNull(obj);
        assertTrue(obj instanceof List);
        assertEquals((List<Object>) Arrays.asList((Object) "RANK()", (Object) "abc"), (List<Object>) obj);
    }
}
