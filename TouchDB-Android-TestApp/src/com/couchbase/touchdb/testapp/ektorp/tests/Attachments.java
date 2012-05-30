package com.couchbase.touchdb.testapp.ektorp.tests;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import junit.framework.Assert;

import org.ektorp.AttachmentInputStream;
import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import android.test.AndroidTestCase;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.ektorp.TouchDBHttpClient;
import com.couchbase.touchdb.router.TDURLStreamHandlerFactory;

public class Attachments extends AndroidTestCase {

    //static inializer to ensure that touchdb:// URLs are handled properly
    {
        TDURLStreamHandlerFactory.registerSelfIgnoreError();
    }

    public void testAttachments() throws IOException {

        String filesDir = getContext().getFilesDir().getAbsolutePath();
        TDServer tdserver = new TDServer(filesDir);

        //ensure the test is repeatable
        TDDatabase old = tdserver.getExistingDatabaseNamed("ektorp_attachments_test");
        if(old != null) {
            old.deleteDatabase();
        }

        HttpClient httpClient = new TouchDBHttpClient(tdserver);
        CouchDbInstance server = new StdCouchDbInstance(httpClient);

        CouchDbConnector db = server.createConnector("ektorp_attachments_test", true);

        TestObject test = new TestObject(1, false, "ektorp");

        //create a document
        db.create(test);

        //attach file to it
        byte[] attach1 = "This is the body of attach1".getBytes();
        ByteArrayInputStream b = new ByteArrayInputStream(attach1);
        AttachmentInputStream a = new AttachmentInputStream("attach", b, "text/plain");
        db.createAttachment(test.getId(), test.getRevision(), a);

        AttachmentInputStream readAttachment = db.getAttachment(test.getId(), "attach");
        Assert.assertEquals("text/plain", readAttachment.getContentType());
        Assert.assertEquals("attach", readAttachment.getId());

        BufferedReader br = new BufferedReader(new InputStreamReader(readAttachment));
        Assert.assertEquals("This is the body of attach1", br.readLine());
    }

}
