package com.couchbase.test.lite;

import com.couchbase.lite.Context;

/**
 * Created by pasin on 8/27/15.
 */
public interface TestContextFactory {

    public Context getTestContext(String dirName);

}
