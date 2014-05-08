package com.couchbase.test.lite;

import android.test.*;

/**
 * This class is changed depending on if we are in Android or Java. Amongst other things it lets us
 * change the base class for tests between TestCase (Java) and AndroidTestCase (Android).
 */
public class LiteTestCaseBase extends AndroidTestCase {
}
