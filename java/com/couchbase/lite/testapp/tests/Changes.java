package com.couchbase.lite.testapp.tests;

import com.couchbase.lite.Database;
import com.couchbase.lite.CBLStatus;
import com.couchbase.lite.CBLiteException;
import com.couchbase.lite.internal.CBLBody;
import com.couchbase.lite.internal.CBLRevisionInternal;

import junit.framework.Assert;

import java.util.HashMap;
import java.util.Map;

public class Changes extends CBLiteTestCase {

    private int changeNotifications = 0;

    public void testChangeNotification() throws CBLiteException {

        Database.ChangeListener changeListener = new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                changeNotifications++;
            }
        };

        // add listener to database
        database.addChangeListener(changeListener);

        // create a document
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);
        documentProperties.put("baz", "touch");

        CBLBody body = new CBLBody(documentProperties);
        CBLRevisionInternal rev1 = new CBLRevisionInternal(body, database);

        CBLStatus status = new CBLStatus();
        rev1 = database.putRevision(rev1, null, false, status);

        Assert.assertEquals(1, changeNotifications);
    }

}
