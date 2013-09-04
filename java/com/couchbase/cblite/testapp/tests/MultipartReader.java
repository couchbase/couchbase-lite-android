package com.couchbase.cblite.testapp.tests;

import android.test.InstrumentationTestCase;

import com.couchbase.cblite.support.CBLMultipartReader;
import com.couchbase.cblite.support.CBLMultipartReaderDelegate;
import com.couchbase.cblite.support.Range;

import junit.framework.Assert;

import org.apache.http.util.ByteArrayBuffer;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MultipartReader extends InstrumentationTestCase {

    class TestMultipartReaderDelegate implements CBLMultipartReaderDelegate {

        private ByteArrayBuffer currentPartData;
        private List<Map<String, String>> headersList;
        private List<ByteArrayBuffer> partList;

        public void startedPart(Map<String, String> headers) {
            Assert.assertNull(currentPartData);
            if (partList == null) {
                partList = new ArrayList<ByteArrayBuffer>();
            }
            currentPartData = new ByteArrayBuffer(1024);
            partList.add(currentPartData);
            if (headersList == null) {
                headersList = new ArrayList<Map<String, String>>();
            }
            headersList.add(headers);
        }

        public void appendToPart(byte[] data) {
            Assert.assertNotNull(currentPartData);
            currentPartData.append(data, 0, data.length);
        }

        public void finishedPart() {
            Assert.assertNotNull(currentPartData);
            currentPartData = null;
        }

    }

    public void testParseContentType() {

        Charset utf8 = Charset.forName("UTF-8");
        HashMap<String, byte[]> contentTypes = new HashMap<String, byte[]>();
        contentTypes.put("multipart/related; boundary=\"BOUNDARY\"", new String("\r\n--BOUNDARY").getBytes(utf8));
        contentTypes.put("multipart/related; boundary=BOUNDARY", new String("\r\n--BOUNDARY").getBytes(utf8));
        contentTypes.put("multipart/related;boundary=X", new String("\r\n--X").getBytes(utf8));

        for (String contentType : contentTypes.keySet()) {
            CBLMultipartReaderDelegate delegate = null;
            CBLMultipartReader reader = new CBLMultipartReader(contentType, delegate);
            byte[] expectedBoundary = (byte[]) contentTypes.get(contentType);
            byte[] boundary = reader.getBoundary();
            Assert.assertTrue(Arrays.equals(boundary, expectedBoundary));
        }

        try {
            CBLMultipartReaderDelegate delegate = null;
            CBLMultipartReader reader = new CBLMultipartReader("multipart/related; boundary=\"BOUNDARY", delegate);
            Assert.assertTrue("Should not have gotten here, above lines should have thrown exception", false);
        } catch (Exception e) {
            // expected exception
        }

    }

    public void testParseHeaders() {
        String testString = new String("\r\nFoo: Bar\r\n Header : Val ue ");
        CBLMultipartReader reader = new CBLMultipartReader("multipart/related;boundary=X", null);
        reader.parseHeaders(testString);
        Assert.assertEquals(reader.headers.keySet().size(), 2);
    }

    public void testSearchFor() throws Exception {
        String testString = new String("\r\n\r\n");
        byte[] testStringBytes = testString.getBytes(Charset.forName("UTF-8"));
        CBLMultipartReader reader = new CBLMultipartReader("multipart/related;boundary=X", null);
        reader.appendData(testStringBytes);
        Range r = reader.searchFor(testStringBytes, 0);
        Assert.assertEquals(0, r.getLocation());
        Assert.assertEquals(4, r.getLength());

        Range r2 = reader.searchFor(new String("nomatch").getBytes(Charset.forName("UTF-8")), 0);
        Assert.assertEquals(-1, r2.getLocation());
        Assert.assertEquals(0, r2.getLength());

    }

    public void testReaderOperation() {

        Charset utf8 = Charset.forName("UTF-8");

        byte[] mime = new String("--BOUNDARY\r\nFoo: Bar\r\n Header : Val ue \r\n\r\npart the first\r\n--BOUNDARY  \r\n\r\n2nd part\r\n--BOUNDARY--").getBytes(utf8);
        readerOperationWithMime(mime);

        byte[] mime2 = new String("--BOUNDARY\r\nFoo: Bar\r\n Header : Val ue \r\n\r\npart the first\r\n--BOUNDARY\r\n\r\n2nd part\r\n--BOUNDARY--").getBytes(utf8);
        readerOperationWithMime(mime2);


    }

    private void readerOperationWithMime(byte[] mime) {

        Charset utf8 = Charset.forName("UTF-8");

        for (int chunkSize=1; chunkSize <= mime.length; ++chunkSize) {
            ByteArrayInputStream mimeInputStream = new ByteArrayInputStream(mime);
            TestMultipartReaderDelegate delegate = new TestMultipartReaderDelegate();
            String contentType = "multipart/related; boundary=\"BOUNDARY\"";
            CBLMultipartReader reader = new CBLMultipartReader(contentType, delegate);
            Assert.assertFalse(reader.finished());

            int location = 0;
            int length = 0;

            do {
                Assert.assertTrue("Parser didn't stop at end", location < mime.length);
                length = Math.min(chunkSize, (mime.length - location));
                byte[] bytesRead = new byte[length];
                mimeInputStream.read(bytesRead, 0, length);
                reader.appendData(bytesRead);
                location += chunkSize;
            } while (!reader.finished());

            Assert.assertEquals(delegate.partList.size(), 2);
            Assert.assertEquals(delegate.headersList.size(), 2);

            byte[] part1Expected = new String("part the first").getBytes(utf8);
            byte[] part2Expected = new String("2nd part").getBytes(utf8);
            ByteArrayBuffer part1 = delegate.partList.get(0);
            ByteArrayBuffer part2 = delegate.partList.get(1);
            Assert.assertTrue(Arrays.equals(part1.toByteArray(), part1Expected));
            Assert.assertTrue(Arrays.equals(part2.toByteArray(), part2Expected));

            Map<String, String> headers1 = delegate.headersList.get(0);
            Assert.assertTrue(headers1.containsKey("Foo"));
            Assert.assertEquals(headers1.get("Foo"), "Bar");

            Assert.assertTrue(headers1.containsKey("Header"));
            Assert.assertEquals(headers1.get("Header"), "Val ue");

        }
    }

}
