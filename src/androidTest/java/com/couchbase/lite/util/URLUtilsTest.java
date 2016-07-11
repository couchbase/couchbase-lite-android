package com.couchbase.lite.util;

import com.couchbase.lite.LiteTestCase;

import java.net.URL;

/**
 * Created by hideki on 7/8/16.
 */
public class URLUtilsTest  extends LiteTestCase {
    public void testGetUser() throws Exception {
        String username = URLUtils.getUser(new URL("https://johnny:p4ssw0rd@www.example.com:443/script.ext;param=value?query=value#ref"));
        assertNotNull(username);
        assertEquals("johnny", username);

        username = URLUtils.getUser(new URL("https://www.example.com:443/script.ext;param=value?query=value#ref"));
        assertNull(username);

        username = URLUtils.getUser(new URL("https://:p4ssw0rd@www.example.com:443/script.ext;param=value?query=value#ref"));
        assertNotNull(username);
        assertEquals("", username);
    }
}
