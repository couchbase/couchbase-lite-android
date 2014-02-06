package com.couchbase.lite;

import com.couchbase.lite.internal.Body;
import com.couchbase.lite.internal.RevisionInternal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ChangesTest extends LiteTestCase {

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

    public void testLocalChangesAreNotExternal() throws CouchbaseLiteException {
        changeNotifications = 0;
        Database.ChangeListener changeListener = new Database.ChangeListener() {
            @Override public void changed(Database.ChangeEvent event) {
                changeNotifications++;
                assertFalse(event.isExternal());
            }
        };
        database.addChangeListener(changeListener);

        // Insert a document locally.
        Document document = database.createDocument();
        document.createRevision().save();

        // Make sure that the assertion in changeListener was called.
        assertEquals(1, changeNotifications);
    }

    public void testPulledChangesAreExternal() throws CouchbaseLiteException {
        changeNotifications = 0;
        Database.ChangeListener changeListener = new Database.ChangeListener() {
            @Override public void changed(Database.ChangeEvent event) {
                changeNotifications++;
                assertTrue(event.isExternal());
            }
        };
        database.addChangeListener(changeListener);

        // Insert a document as if it came from a remote source.
        RevisionInternal rev = new RevisionInternal("docId", "1-rev", false, database);
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("_id", rev.getDocId());
        properties.put("_rev", rev.getRevId());
        rev.setProperties(properties);
        database.forceInsert(rev, Arrays.asList(rev.getRevId()), getReplicationURL());

        // Make sure that the assertion in changeListener was called.
        assertEquals(1, changeNotifications);
    }

}
