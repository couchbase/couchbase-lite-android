package com.couchbase.lite;

import com.couchbase.lite.internal.Body;
import com.couchbase.lite.internal.RevisionInternal;

import java.util.HashMap;
import java.util.Map;

public class Changes extends CBLiteTestCase {

    private int changeNotifications = 0;

    public void testChangeNotification() throws CouchbaseLiteException {

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

        Body body = new Body(documentProperties);
        RevisionInternal rev1 = new RevisionInternal(body, database);

        Status status = new Status();
        rev1 = database.putRevision(rev1, null, false, status);

        assertEquals(1, changeNotifications);
    }

}
