package com.couchbase.lite;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BlobTest extends BaseTest {
    final static String kBlobTestBlob1 = "i'm blob";
    final static String kBlobTestBlob2 = "i'm blob2";

    @Test
    public void testEquals() throws CouchbaseLiteException {

        byte[] content1a = kBlobTestBlob1.getBytes();
        byte[] content1b = kBlobTestBlob1.getBytes();
        byte[] content2a = kBlobTestBlob2.getBytes();

        // store blob
        Blob data1a = new Blob("text/plain", content1a);
        Blob data1b = new Blob("text/plain", content1b);
        Blob data1c = new Blob("text/plain", content1a); // not store in db
        Blob data2a = new Blob("text/plain", content2a);

        assertTrue(data1a.equals(data1b));
        assertTrue(data1b.equals(data1a));
        assertFalse(data1a.equals(data2a));
        assertFalse(data1b.equals(data2a));
        assertFalse(data2a.equals(data1a));
        assertFalse(data2a.equals(data1b));

        MutableDocument mDoc = new MutableDocument();
        mDoc.setBlob("blob1a", data1a);
        mDoc.setBlob("blob1b", data1b);
        mDoc.setBlob("blob2a", data2a);
        Document doc = save(mDoc);

        Blob blob1a = doc.getBlob("blob1a");
        Blob blob1b = doc.getBlob("blob1b");
        Blob blob2a = doc.getBlob("blob2a");

        assertTrue(blob1a.equals(blob1b));
        assertTrue(blob1b.equals(blob1a));
        assertFalse(blob1a.equals(blob2a));
        assertFalse(blob1b.equals(blob2a));
        assertFalse(blob2a.equals(blob1a));
        assertFalse(blob2a.equals(blob1b));

        // TODO - confirm spec
        assertFalse(blob1a.equals(data1c));
        assertFalse(data1c.equals(blob1a));
    }
    @Test
    public void testHashCode() throws CouchbaseLiteException {
        byte[] content1a = kBlobTestBlob1.getBytes();
        byte[] content1b = kBlobTestBlob1.getBytes();
        byte[] content2a = kBlobTestBlob2.getBytes();

        // store blob
        Blob data1a = new Blob("text/plain", content1a);
        Blob data1b = new Blob("text/plain", content1b);
        Blob data1c = new Blob("text/plain", content1a); // not store in db
        Blob data2a = new Blob("text/plain", content2a);

        assertTrue(data1a.hashCode() == data1b.hashCode());
        assertTrue(data1b.hashCode() == data1a.hashCode());
        assertFalse(data1a.hashCode() == data2a.hashCode());
        assertFalse(data1b.hashCode() == data2a.hashCode());
        assertFalse(data2a.hashCode() == data1a.hashCode());
        assertFalse(data2a.hashCode() == data1b.hashCode());

        MutableDocument mDoc = new MutableDocument();
        mDoc.setBlob("blob1a", data1a);
        mDoc.setBlob("blob1b", data1b);
        mDoc.setBlob("blob2a", data2a);
        Document doc = save(mDoc);

        Blob blob1a = doc.getBlob("blob1a");
        Blob blob1b = doc.getBlob("blob1b");
        Blob blob2a = doc.getBlob("blob2a");

        assertTrue(blob1a.hashCode() == blob1b.hashCode());
        assertTrue(blob1b.hashCode() == blob1a.hashCode());
        assertFalse(blob1a.hashCode() == blob2a.hashCode());
        assertFalse(blob1b.hashCode() == blob2a.hashCode());
        assertFalse(blob2a.hashCode() == blob1a.hashCode());
        assertFalse(blob2a.hashCode() == blob1b.hashCode());

        // TODO - confirm spec
        assertFalse(blob1a.hashCode() == data1c.hashCode());
        assertFalse(data1c.hashCode() == blob1a.hashCode());
    }
}