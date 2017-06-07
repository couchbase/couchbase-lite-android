package com.couchbase.lite.internal.support;

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
    public void testSastPathComponent() {
        assertEquals("scratch.tiff", StringUtils.lastPathComponent("/tmp/scratch.tiff"));
        assertEquals("scratch", StringUtils.lastPathComponent("/tmp/scratch"));
        assertEquals("tmp", StringUtils.lastPathComponent("/tmp/"));
        assertEquals("scratch", StringUtils.lastPathComponent("scratch///"));
        assertEquals("/", StringUtils.lastPathComponent("/"));
    }
}
