package com.couchbase.lite;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
        ABC abc = new ABC();
        assertNotNull(abc);
        com.couchbase.lite.Test test = new com.couchbase.lite.Test();
        assertNotNull(test);
    }
}