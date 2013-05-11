package com.couchbase.cblite.router;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import android.util.Log;

import com.couchbase.cblite.CBLBody;
import com.couchbase.cblite.CBLDatabase;

public class CBLURLConnection extends HttpURLConnection {

    private Header resHeader;
    private boolean sentRequest = false;
    private ByteArrayOutputStream os;
    private CBLBody responseBody;
    private boolean chunked = false;

    private HashMap<String, List<String>> requestProperties = new HashMap<String, List<String>>();

    private static final String POST = "POST";
    private static final String GET = "GET";
    private static final String PUT = "PUT";
    private static final String HEAD = "HEAD";

    private OutputStream responseOutputStream;
    private InputStream responseInputStream;

    private InputStream requestInputStream;

    public CBLURLConnection(URL url) {
        super(url);
        responseInputStream = new PipedInputStream();
        try {
            responseOutputStream = new PipedOutputStream((PipedInputStream)responseInputStream);
        } catch (IOException e) {
            Log.e(CBLDatabase.TAG, "Exception creating piped output stream", e);
        }
    }

    @Override
    public void connect() throws IOException {

    }

    @Override
    public void disconnect() {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean usingProxy() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public Map<String, List<String>> getRequestProperties() {
        HashMap<String, List<String>> map = new HashMap<String, List<String>>();
        for (String key : requestProperties.keySet()) {
            map.put(key, Collections.unmodifiableList(requestProperties.get(key)));
        }
        return Collections.unmodifiableMap(map);
    }

    @Override
    public String getRequestProperty(String field) {
        List<String> valuesList = requestProperties.get(field);
        if (valuesList == null) {
            return null;
        }
        return valuesList.get(0);
    }

    @Override
    public void setRequestProperty(String field, String newValue) {
        List<String> valuesList = new ArrayList<String>();
        valuesList.add(newValue);
        requestProperties.put(field, valuesList);
    }

    @Override
    public String getHeaderField(int pos) {
        try {
            getInputStream();
        } catch (IOException e) {
            // ignore
        }
        if (null == resHeader) {
            return null;
        }
        return resHeader.get(pos);
    }

    @Override
    public String getHeaderField(String key) {
        try {
            getInputStream();
        } catch (IOException e) {
            // ignore
        }
        if (null == resHeader) {
            return null;
        }
        return resHeader.get(key);
    }

    @Override
    public String getHeaderFieldKey(int pos) {
        try {
            getInputStream();
        } catch (IOException e) {
            // ignore
        }
        if (null == resHeader) {
            return null;
        }
        return resHeader.getKey(pos);
    }

    @Override
    public Map<String, List<String>> getHeaderFields() {
        try {
            // ensure that resHeader exists
            getInputStream();
        } catch (IOException e) {
            // ignore
        }
        if (null == resHeader) {
            return null;
        }
        return resHeader.getFieldMap();
    }

    Header getResHeader() {
        if(resHeader == null) {
            resHeader = new Header();
        }
        return resHeader;
    }

    @Override
    public int getResponseCode() {
        return responseCode;
    }

    void setResponseCode(int responseCode) {
        this.responseCode = responseCode;
    }

    void setResponseBody(CBLBody responseBody) {
        this.responseBody = responseBody;
    }

    public CBLBody getResponseBody() {
        return this.responseBody;
    }

    String getBaseContentType() {
        String type = resHeader.get("Content-Type");
        if(type == null) {
            return null;
        }
        int delimeterPos = type.indexOf(';');
        if(delimeterPos > 0) {
            type = type.substring(delimeterPos);
        }
        return type;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (!doOutput) {
            throw new ProtocolException("Must set doOutput");
        }

        // you can't write after you read
        if (sentRequest) {
            throw new ProtocolException("Can't write after you read");
        }

        if (os != null) {
            return os;
        }

        // they are requesting a stream to write to. This implies a POST method
        if (method == GET) {
            method = POST;
        }

        // If the request method is neither PUT or POST, then you're not writing
        if (method != PUT && method != POST) {
            throw new ProtocolException("Can only write to PUT or POST");
        }

        if (!connected) {
            // connect and see if there is cache available.
            connect();
        }
        return os = new ByteArrayOutputStream();

    }

    public void setChunked(boolean chunked) {
        this.chunked = chunked;
    }

    public boolean isChunked() {
        return chunked;
    }

    public void setResponseInputStream(InputStream responseInputStream) {
        this.responseInputStream = responseInputStream;
    }

    public InputStream getResponseInputStream() {
        return responseInputStream;
    }

    public void setResponseOutputStream(OutputStream responseOutputStream) {
        this.responseOutputStream = responseOutputStream;
    }

    public OutputStream getResponseOutputStream() {
        return responseOutputStream;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return responseInputStream;
    }

    public InputStream getRequestInputStream() {
        return requestInputStream;
    }

    public void setRequestInputStream(InputStream requestInputStream) {
        this.requestInputStream = requestInputStream;
    }

}

/**
 * Heavily borrowed from Apache Harmony
 * https://github.com/apache/harmony/blob/trunk/classlib/modules/luni/src/main/java/org/apache/harmony/luni/internal/net/www/protocol/http/Header.java
 * Under Apache License Version 2.0
 *
 */
class Header {

    private ArrayList<String> props;
    private SortedMap<String, LinkedList<String>> keyTable;

    public Header() {
        super ();
        this .props = new ArrayList<String>(20);
        this .keyTable = new TreeMap<String, LinkedList<String>>(
                String.CASE_INSENSITIVE_ORDER);
    }

    public Header(Map<String, List<String>> map) {
        this (); // initialize fields
        for (Entry<String, List<String>> next : map.entrySet()) {
            String key = next.getKey();
            List<String> value = next.getValue();
            LinkedList<String> linkedList = new LinkedList<String>();
            for (String element : value) {
                linkedList.add(element);
                props.add(key);
                props.add(element);
            }
            keyTable.put(key, linkedList);
        }
    }

    public void add(String key, String value) {
        if (key == null) {
            throw new NullPointerException();
        }
        if (value == null) {
            return;
        }
        LinkedList<String> list = keyTable.get(key);
        if (list == null) {
            list = new LinkedList<String>();
            keyTable.put(key, list);
        }
        list.add(value);
        props.add(key);
        props.add(value);
    }

    public void removeAll(String key) {
        keyTable.remove(key);

        for (int i = 0; i < props.size(); i += 2) {
            if (key.equals(props.get(i))) {
                props.remove(i); // key
                props.remove(i); // value
            }
        }
    }

    public void addAll(String key, List<String> headers) {
        for (String header : headers) {
            add(key, header);
        }
    }

    public void addIfAbsent(String key, String value) {
        if (get(key) == null) {
            add(key, value);
        }
    }

    public void set(String key, String value) {
        removeAll(key);
        add(key, value);
    }

    public Map<String, List<String>> getFieldMap() {
        Map<String, List<String>> result = new TreeMap<String, List<String>>(
                String.CASE_INSENSITIVE_ORDER); // android-changed
        for (Map.Entry<String, LinkedList<String>> next : keyTable
                .entrySet()) {
            List<String> v = next.getValue();
            result.put(next.getKey(), Collections.unmodifiableList(v));
        }
        return Collections.unmodifiableMap(result);
    }

    public String get(int pos) {
        if (pos >= 0 && pos < props.size() / 2) {
            return props.get(pos * 2 + 1);
        }
        return null;
    }

    public String getKey(int pos) {
        if (pos >= 0 && pos < props.size() / 2) {
            return props.get(pos * 2);
        }
        return null;
    }

    public String get(String key) {
        LinkedList<String> result = keyTable.get(key);
        if (result == null) {
            return null;
        }
        return result.getLast();
    }

    public int length() {
        return props.size() / 2;
    }

}
