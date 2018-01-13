package com.couchbase.lite.internal.utils;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;

public class URIUtilsTest {
    @Test
    public void testGetPort() throws URISyntaxException {
        URI uri = new URI("blip://foo.couchbase.com/db");
        assertEquals(-1, uri.getPort());
    }
}
