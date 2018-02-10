//
// MiscTest.java
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

import com.couchbase.lite.internal.utils.DateUtils;

import org.junit.Test;

import java.util.Date;

import static org.junit.Assert.assertEquals;

public class MiscTest {
    // Verify that round trip NSString -> NSDate -> NSString conversion doesn't alter the string (#1611)
    @Test
    public void testJSONDateRoundTrip() {
        String dateStr1 = "2017-02-05T18:14:06.347Z";
        Date date1 = DateUtils.fromJson(dateStr1);
        String dateStr2 = DateUtils.toJson(date1);
        Date date2 = DateUtils.fromJson(dateStr2);
        assertEquals(date1, date2);
        assertEquals(date1.getTime(), date2.getTime());
    }
}
