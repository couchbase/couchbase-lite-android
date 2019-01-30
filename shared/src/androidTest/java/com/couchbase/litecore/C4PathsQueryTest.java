package com.couchbase.litecore;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public class C4PathsQueryTest extends C4QueryBaseTest {
    C4Query query = null;

    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @Override
    public void setUp() throws Exception {
        super.setUp();
        importJSONLines("paths.json");
    }

    @Override
    public void tearDown() throws Exception {
        if (query != null) {
            query.free();
            query = null;
        }
        super.tearDown();
    }

    // - DB Query ANY w/paths
    @Test
    public void testDBQueryANYwPaths() throws LiteCoreException {
        // For https://github.com/couchbase/couchbase-lite-core/issues/238
        compile(json5("['ANY','path',['.paths'],['=',['?path','city'],'San Jose']]"));
        assertEquals(Arrays.asList("0000001"), run());

        compile(json5("['ANY','path',['.paths'],['=',['?path.city'],'San Jose']]"));
        assertEquals(Arrays.asList("0000001"), run());

        compile(json5("['ANY','path',['.paths'],['=',['?path','city'],'Palo Alto']]"));
        assertEquals(Arrays.asList("0000001", "0000002"), run());
    }
}
