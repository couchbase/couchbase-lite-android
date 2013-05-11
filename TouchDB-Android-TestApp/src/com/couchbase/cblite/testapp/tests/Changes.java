package com.couchbase.cblite.testapp.tests;

import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import junit.framework.Assert;

import com.couchbase.cblite.CBLBody;
import com.couchbase.cblite.CBLRevision;
import com.couchbase.cblite.CBLStatus;

public class Changes extends CBLiteTestCase {

    private int changeNotifications = 0;

    public void testChangeNotification() {

        // define a listener
        Observer changeListener = new Observer() {

            @Override
            public void update(Observable observable, Object data) {
                changeNotifications++;
            }

        };

        // add listener to database
        database.addObserver(changeListener);

        // create a document
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);
        documentProperties.put("baz", "touch");

        CBLBody body = new CBLBody(documentProperties);
        CBLRevision rev1 = new CBLRevision(body);

        CBLStatus status = new CBLStatus();
        rev1 = database.putRevision(rev1, null, false, status);

        Assert.assertEquals(1, changeNotifications);
    }

}
