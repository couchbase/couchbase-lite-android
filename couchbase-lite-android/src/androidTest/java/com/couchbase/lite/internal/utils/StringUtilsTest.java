//
// StringUtilsTest.java
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
package com.couchbase.lite.internal.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;


public class StringUtilsTest {
    @Test
    public void testStringByDeletingLastPathComponent() {
        assertEquals("/tmp", StringUtils.stringByDeletingLastPathComponent("/tmp/scratch.tiff"));
        assertEquals("/tmp", StringUtils.stringByDeletingLastPathComponent("/tmp/lock/"));
        assertEquals("/", StringUtils.stringByDeletingLastPathComponent("/tmp/"));
        assertEquals("/", StringUtils.stringByDeletingLastPathComponent("/tmp"));
        assertEquals("", StringUtils.stringByDeletingLastPathComponent("scratch.tiff"));
    }

    @Test
    public void testLastPathComponent() {
        assertEquals("scratch.tiff", StringUtils.lastPathComponent("/tmp/scratch.tiff"));
        assertEquals("scratch", StringUtils.lastPathComponent("/tmp/scratch"));
        assertEquals("tmp", StringUtils.lastPathComponent("/tmp/"));
        assertEquals("scratch", StringUtils.lastPathComponent("scratch///"));
        assertEquals("/", StringUtils.lastPathComponent("/"));
    }
}
