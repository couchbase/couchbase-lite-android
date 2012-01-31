package com.couchbase.touchdb.testapp.tests;

import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;

import org.codehaus.jackson.map.ObjectMapper;

import android.test.InstrumentationTestCase;
import android.util.Log;

import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.TDStatus;
import com.couchbase.touchdb.router.TDRouter;
import com.couchbase.touchdb.router.TDURLConnection;
import com.couchbase.touchdb.router.TDURLStreamHandlerFactor;

public class Router extends InstrumentationTestCase {

    public static final String TAG = "Router";

    static TDURLConnection sendRequest(TDServer server, String method, String path, Map<String,String> headers, Object bodyObj) {
        try {
            URL url = new URL("touchdb://" + path);
            TDURLConnection conn = (TDURLConnection)url.openConnection();
            conn.setRequestMethod(method);
            if(headers != null) {
                for (String header : headers.keySet()) {
                    conn.setRequestProperty(header, headers.get(header));
                }
            }
            if(bodyObj != null) {
                conn.setDoInput(true);
                ObjectMapper mapper = new ObjectMapper();
                OutputStream os = conn.getOutputStream();
                os.write(mapper.writeValueAsBytes(bodyObj));
            }

            TDRouter router = new TDRouter(server, conn);
            router.start();
            return conn;
        } catch (MalformedURLException e) {
            fail();
        } catch(IOException e) {
            fail();
        }
        return null;
    }

    static Object parseJSONResponse(TDURLConnection conn) {
        byte[] json = conn.getResponseBody().getJson();
        String jsonString = null;
        Object result = null;
        if(json != null) {
            jsonString = new String(json);
            ObjectMapper mapper = new ObjectMapper();
            try {
                result = mapper.readValue(jsonString, Object.class);
            } catch (Exception e) {
                fail();
            }
        }
        return result;
    }

    static Object sendBody(TDServer server, String method, String path, Object bodyObj, int expectedStatus, Object expectedResult) {
        TDURLConnection conn = sendRequest(server, method, path, null, bodyObj);
        Object result = parseJSONResponse(conn);
        try {
            Log.v(TAG, String.format("%s %s --> %d", method, path, conn.getResponseCode()));
        } catch (IOException e) {
            fail();
        }
        try {
            Assert.assertEquals(expectedStatus, conn.getResponseCode());
        } catch (IOException e) {
            fail();
        }
        if(expectedResult != null) {
            Assert.assertEquals(expectedResult, result);
        }
        return result;
    }

    static Object send(TDServer server, String method, String path, int expectedStatus, Object expectedResult) {
        return sendBody(server, method, path, null, expectedStatus, expectedResult);
    }

    public void testServer() {
        URL.setURLStreamHandlerFactory(new TDURLStreamHandlerFactor());

        String filesDir = getInstrumentation().getContext().getFilesDir().getAbsolutePath();

        TDServer server = null;
        try {
            server = new TDServer(filesDir);
        } catch (IOException e) {
            fail("Creating server caused IOException");
        }

        Map<String,Object> responseBody = new HashMap<String,Object>();
        responseBody.put("TouchDB", "Welcome");
        responseBody.put("couchdb", "Welcome");
        responseBody.put("version", TDRouter.getVersionString());
        send(server, "GET", "/", TDStatus.OK, responseBody);
    }

//    public void testURLHandler() {
//
//        URL url;
//
//        try {
//            url = new URL("touchdb:///");
//            fail("TouchDB URL should fail before nwe handler is installed");
//        } catch (MalformedURLException e) {
//            //ignore
//        }
//
//        try {
//            url = new URL("http://couchbase.com/");
//            Log.v(TAG, String.format("URL is %s", url.toExternalForm()));
//        } catch(MalformedURLException e) {
//            fail("Broke handling of HTTP");
//        }
//
//        URL.setURLStreamHandlerFactory(new TDURLStreamHandlerFactor());
//
//
//        try {
//            url = new URL("touchdb:///");
//            Log.v(TAG, String.format("URL is %s", url.toExternalForm()));
//            Object o = url.openConnection();
//            Log.v(TAG, "Connection class is: " + o.getClass().toString());
//        } catch (MalformedURLException e) {
//            fail("TouchDB URL handler not properly installed");
//        } catch (IOException e) {
//            fail("Got IOException");
//        }
//
//        try {
//            url = new URL("http://google.com/");
//            Log.v(TAG, String.format("URL is %s", url.toExternalForm()));
//        } catch(MalformedURLException e) {
//            fail("Broke handling of HTTP");
//        }
//    }

}
