//
// C4Test.java
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
package com.couchbase.litecore;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class C4Test extends C4BaseTest {
    @Test
    public void testGetBuildInfo() {
        String res = C4.getBuildInfo();
        assertNotNull(res);
        assertTrue(res.length() > 0);
    }

    @Test
    public void testGetVersion() {
        String res = C4.getVersion();
        assertNotNull(res);
        assertTrue(res.length() > 0);
    }
}
