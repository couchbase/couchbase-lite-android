package com.couchbase.lite.mockserver;

import com.squareup.okhttp.mockwebserver.RecordedRequest;

/**
 * Created by hideki on 1/20/15.
 */
public class MockUtils {

    /**
     * returns decompress body if needs
     */
    public static String getUtf8Body(RecordedRequest request) throws Exception{
        // for gzip support core java #172
        // https://github.com/couchbase/couchbase-lite-java-core/issues/172
        byte[] body = request.getBody();
        if(request.getHeader("Content-Encoding")!=null&&request.getHeader("Content-Encoding").contains("gzip")){
            body = com.couchbase.lite.util.Utils.decompressByGzip(body);
        }
        return new String(body, "UTF-8");
    }
}
