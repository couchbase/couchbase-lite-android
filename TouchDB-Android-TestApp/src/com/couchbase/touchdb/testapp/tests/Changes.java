package com.couchbase.touchdb.testapp.tests;

import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import junit.framework.Assert;
import android.test.AndroidTestCase;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDStatus;

public class Changes extends AndroidTestCase {

    private int changeNotifications = 0;

    public void testChangeNotification() {

        String filesDir = getContext().getFilesDir().getAbsolutePath();

        TDDatabase db = TDDatabase.createEmptyDBAtPath(filesDir + "/touch_couch_test.sqlite3");

        // define a listener
        Observer changeListener = new Observer() {

            @Override
            public void update(Observable observable, Object data) {
                changeNotifications++;
            }

        };

        // add listener to database
        db.addObserver(changeListener);

        // create a document
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);
        documentProperties.put("baz", "touch");

        TDBody body = new TDBody(documentProperties);
        TDRevision rev1 = new TDRevision(body);

        TDStatus status = new TDStatus();
        rev1 = db.putRevision(rev1, null, status);

        Assert.assertEquals(1, changeNotifications);

        db.close();
    }

}
