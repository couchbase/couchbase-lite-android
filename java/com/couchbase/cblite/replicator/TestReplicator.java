package com.couchbase.cblite.replicator;

import android.test.InstrumentationTestCase;

import junit.framework.Assert;

import java.net.URL;

public class TestReplicator extends InstrumentationTestCase {

    public void testBuildRelativeURLString() throws Exception {

        String dbUrlString = "http://10.0.0.3:4984/todos/";
        CBLReplicator replicator = new CBLPusher(null, new URL(dbUrlString), false, null);
        String relativeUrlString = replicator.buildRelativeURLString("_facebook");

        String expected = "http://10.0.0.3:4984/todos/_facebook";
        Assert.assertEquals(expected, relativeUrlString);
    }

}
