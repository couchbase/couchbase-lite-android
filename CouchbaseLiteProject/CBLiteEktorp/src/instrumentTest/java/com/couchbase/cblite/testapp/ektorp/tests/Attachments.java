package com.couchbase.cblite.testapp.ektorp.tests;

import com.couchbase.cblite.ektorp.CBLiteHttpClient;

import junit.framework.Assert;

import org.ektorp.AttachmentInputStream;
import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

public class Attachments extends CBLiteEktorpTestCase {

    public void testAttachments() throws IOException {

        HttpClient httpClient = new CBLiteHttpClient(server);
        CouchDbInstance dbInstance = new StdCouchDbInstance(httpClient);

        CouchDbConnector dbConnector = dbInstance.createConnector(DEFAULT_TEST_DB, true);

        TestObject test = new TestObject(1, false, "ektorp");

        //create a document
        dbConnector.create(test);

        //attach file to it
        byte[] attach1 = "This is the body of attach1".getBytes();
        ByteArrayInputStream b = new ByteArrayInputStream(attach1);
        AttachmentInputStream a = new AttachmentInputStream("attach", b, "text/plain");
        dbConnector.createAttachment(test.getId(), test.getRevision(), a);

        AttachmentInputStream readAttachment = dbConnector.getAttachment(test.getId(), "attach");
        Assert.assertEquals("text/plain", readAttachment.getContentType());
        Assert.assertEquals("attach", readAttachment.getId());

        BufferedReader br = new BufferedReader(new InputStreamReader(readAttachment));
        Assert.assertEquals("This is the body of attach1", br.readLine());
    }

}
