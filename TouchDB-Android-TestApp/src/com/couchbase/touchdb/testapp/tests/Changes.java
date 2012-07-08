package com.couchbase.touchdb.testapp.tests;

import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import junit.framework.Assert;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDStatus;

public class Changes extends TouchDBTestCase {

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

        TDBody body = new TDBody(documentProperties);
        TDRevision rev1 = new TDRevision(body);

        TDStatus status = new TDStatus();
        rev1 = database.putRevision(rev1, null, false, status);

        Assert.assertEquals(1, changeNotifications);
    }

}
