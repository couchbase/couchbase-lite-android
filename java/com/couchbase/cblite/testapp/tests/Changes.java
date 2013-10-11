package com.couchbase.cblite.testapp.tests;

import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import junit.framework.Assert;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLDatabaseChangedFunction;
import com.couchbase.cblite.CBLiteException;
import com.couchbase.cblite.internal.CBLBody;
import com.couchbase.cblite.internal.CBLRevisionInternal;
import com.couchbase.cblite.CBLStatus;

public class Changes extends CBLiteTestCase {

    private int changeNotifications = 0;

    public void testChangeNotification() throws CBLiteException {

        CBLDatabaseChangedFunction changeListener = new CBLDatabaseChangedFunction() {
            @Override
            public void onDatabaseChanged(CBLDatabase database, Map<String, Object> changeNotification) {
                changeNotifications++;
            }
            @Override
            public void onFailureDatabaseChanged(CBLiteException exception) {
                Log.e(CBLDatabase.TAG, "onFailureDatabaseChanged", exception);
                Assert.assertTrue(false);
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
