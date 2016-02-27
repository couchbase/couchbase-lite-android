package com.couchbase.lite;


import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class ValidationsTest extends LiteTestCaseWithDB {

    public static final String TAG = "ValidationsTest";

    /**
     * in DatabaseInternal_Tests.m
     * - (void) test05_Validation
     */
    public void testValidations() throws CouchbaseLiteException {
        final AtomicBoolean validationCalled = new AtomicBoolean(false);

        Validator validator = new Validator() {
            @Override
            public void validate(Revision newRevision, ValidationContext context) {
                assertNotNull(newRevision);
                assertNotNull(context);
                assertTrue(newRevision.getProperties() != null || newRevision.isDeletion());

                // Check if following two methods don't throw NPE.
                // https://github.com/couchbase/couchbase-lite-java-core/issues/1061
                context.getChangedKeys();
                newRevision.getDocument().isDeleted();

                validationCalled.set(true);
                boolean hoopy = newRevision.isDeletion() ||
                        (newRevision.getProperties().get("towel") != null);
                Log.v(TAG, "--- Validating %s --> %b", newRevision.getProperties(), hoopy);
                if (!hoopy) {
                    context.reject("Where's your towel?");
                }
            }
        };
        database.setValidation("hoopy", validator);

        // POST a valid new document:
        Map<String, Object> props = new HashMap<String, Object>();
        props.put("name", "Zaphod Beeblebrox");
        props.put("towel", "velvet");
        RevisionInternal rev = new RevisionInternal(props);
        Status status = new Status();
        validationCalled.set(false);
        rev = database.putRevision(rev, null, false, status);
        assertTrue(validationCalled.get());
        assertEquals(Status.CREATED, status.getCode());

        // PUT a valid update:
        props.put("head_count", 3);
        rev.setProperties(props);
        validationCalled.set(false);
        rev = database.putRevision(rev, rev.getRevID(), false, status);
        assertTrue(validationCalled.get());
        assertEquals(Status.CREATED, status.getCode());

        // PUT an invalid update:
        props.remove("towel");
        rev.setProperties(props);
        validationCalled.set(false);
        boolean gotExpectedError = false;
        try {
            database.putRevision(rev, rev.getRevID(), false, status);
        } catch (CouchbaseLiteException e) {
            gotExpectedError = (e.getCBLStatus().getCode() == Status.FORBIDDEN);
        }
        assertTrue(validationCalled.get());
        assertTrue(gotExpectedError);

        // POST an invalid new document:
        props = new HashMap<String, Object>();
        props.put("name", "Vogon");
        props.put("poetry", true);
        rev = new RevisionInternal(props);
        validationCalled.set(false);
        gotExpectedError = false;
        try {
            database.putRevision(rev, null, false, status);
        } catch (CouchbaseLiteException e) {
            gotExpectedError = (e.getCBLStatus().getCode() == Status.FORBIDDEN);
        }
        assertTrue(validationCalled.get());
        assertTrue(gotExpectedError);

        // PUT a valid new document with an ID:
        props = new HashMap<String, Object>();
        props.put("_id", "ford");
        props.put("name", "Ford Prefect");
        props.put("towel", "terrycloth");
        rev = new RevisionInternal(props);
        validationCalled.set(false);
        rev = database.putRevision(rev, null, false, status);
        assertTrue(validationCalled.get());
        assertEquals("ford", rev.getDocID());

        // DELETE a document:
        rev = new RevisionInternal(rev.getDocID(), rev.getRevID(), true);
        assertTrue(rev.isDeleted());
        validationCalled.set(false);
        rev = database.putRevision(rev, rev.getRevID(), false, status);
        assertTrue(validationCalled.get());

        // PUT an invalid new document:
        props = new HashMap<String, Object>();
        props.put("_id", "petunias");
        props.put("name", "Pot of Petunias");
        rev = new RevisionInternal(props);
        validationCalled.set(false);
        gotExpectedError = false;
        try {
            database.putRevision(rev, null, false, status);
        } catch (CouchbaseLiteException e) {
            gotExpectedError = (e.getCBLStatus().getCode() == Status.FORBIDDEN);
        }
        assertTrue(validationCalled.get());
        assertTrue(gotExpectedError);
    }
}
