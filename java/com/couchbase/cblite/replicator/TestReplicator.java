package com.couchbase.cblite.replicator;

import android.test.InstrumentationTestCase;

import junit.framework.Assert;

import java.net.URL;

public class TestReplicator extends InstrumentationTestCase {

    public void testBuildRelativeURLString() throws Exception {
        CBLReplicator replicator = new CBLPusher(null, new URL("http://10.0.0.3:4984/todos/"), false, null);
        String relativeUrlString = replicator.buildRelativeURLString("/_facebook");

        
        Assert.assertTrue(relativeUrlString.contains("//") == false);


    }

}
