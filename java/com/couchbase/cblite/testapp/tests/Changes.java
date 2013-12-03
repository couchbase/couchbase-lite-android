package com.couchbase.cblite.testapp.tests;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLStatus;
import com.couchbase.cblite.CBLiteException;
import com.couchbase.cblite.internal.CBLBody;
import com.couchbase.cblite.internal.CBLRevisionInternal;
import com.couchbase.cblite.util.Log;

import junit.framework.Assert;

import java.util.HashMap;
import java.util.Map;

public class Changes extends CBLiteTestCase {

    private int changeNotifications = 0;

    public void testChangeNotification() throws CBLiteException {

        CBLDatabase.ChangeListener changeListener = new CBLDatabase.ChangeListener() {
            @Override
            public void changed(CBLDatabase.ChangeEvent event) {
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
