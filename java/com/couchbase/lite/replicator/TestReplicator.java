package com.couchbase.lite.replicator;

import android.test.InstrumentationTestCase;

import junit.framework.Assert;

import java.net.URL;

public class TestReplicator extends InstrumentationTestCase {

    public void testBuildRelativeURLString() throws Exception {

        String dbUrlString = "http://10.0.0.3:4984/todos/";
        Replication replicator = new CBLPusher(null, new URL(dbUrlString), false, null);
        String relativeUrlString = replicator.buildRelativeURLString("foo");

        String expected = "http://10.0.0.3:4984/todos/foo";
        Assert.assertEquals(expected, relativeUrlString);

    }

    public void testBuildRelativeURLStringWithLeadingSlash() throws Exception {

        String dbUrlString = "http://10.0.0.3:4984/todos/";
        Replication replicator = new CBLPusher(null, new URL(dbUrlString), false, null);
        String relativeUrlString = replicator.buildRelativeURLString("/foo");

        String expected = "http://10.0.0.3:4984/todos/foo";
        Assert.assertEquals(expected, relativeUrlString);

    }

}
