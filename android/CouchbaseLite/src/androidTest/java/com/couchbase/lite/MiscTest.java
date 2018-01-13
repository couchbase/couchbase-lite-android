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
