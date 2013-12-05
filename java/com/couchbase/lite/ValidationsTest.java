package com.couchbase.lite;


import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.util.Log;

import java.util.HashMap;
import java.util.Map;


public class ValidationsTest extends LiteTestCase {

    public static final String TAG = "Validations";

    boolean validationCalled = false;

    public void testValidations() throws CouchbaseLiteException {

        ValidationBlock validationBlock = new ValidationBlock() {

            @Override
            public boolean validate(RevisionInternal newRevision, ValidationContext context) {

                assertNotNull(newRevision);
                assertNotNull(context);
                assertTrue(newRevision.getProperties() != null || newRevision.isDeleted());
                validationCalled = true;
                boolean hoopy = newRevision.isDeleted()  || (newRevision.getProperties().get("towel") != null);
                Log.v(TAG, String.format("--- Validating %s --> %b", newRevision.getProperties(), hoopy));
                if(!hoopy) {
                    context.setErrorMessage("Where's your towel?");
                }
                return hoopy;
            }
        };

        database.setValidation("hoopy", validationBlock);

        // POST a valid new document:
        Map<String, Object> props = new HashMap<String,Object>();
        props.put("name","Zaphod Beeblebrox");
        props.put("towel", "velvet");
        RevisionInternal rev = new RevisionInternal(props, database);
        Status status = new Status();
        validationCalled = false;
        rev = database.putRevision(rev, null, false, status);
        assertTrue(validationCalled);
        assertEquals(Status.CREATED, status.getCode());

        // PUT a valid update:
        props.put("head_count", 3);
        rev.setProperties(props);
        validationCalled = false;
        rev = database.putRevision(rev, rev.getRevId(), false, status);
        assertTrue(validationCalled);
        assertEquals(Status.CREATED, status.getCode());

        // PUT an invalid update:
        props.remove("towel");
        rev.setProperties(props);
        validationCalled = false;
        boolean gotExpectedError = false;
        try {
            rev = database.putRevision(rev, rev.getRevId(), false, status);
        } catch (CouchbaseLiteException e) {
            gotExpectedError = (e.getCBLStatus().getCode() == Status.FORBIDDEN);
        }
        assertTrue(validationCalled);
        assertTrue(gotExpectedError);

        // POST an invalid new document:
        props = new HashMap<String,Object>();
        props.put("name","Vogon");
        props.put("poetry", true);
        rev = new RevisionInternal(props, database);
        validationCalled = false;
        gotExpectedError = false;
        try {
            rev = database.putRevision(rev, null, false, status);
        } catch (CouchbaseLiteException e) {
            gotExpectedError = (e.getCBLStatus().getCode() == Status.FORBIDDEN);
        }
        assertTrue(validationCalled);
        assertTrue(gotExpectedError);

        // PUT a valid new document with an ID:
        props = new HashMap<String,Object>();
        props.put("_id", "ford");
        props.put("name","Ford Prefect");
        props.put("towel", "terrycloth");
        rev = new RevisionInternal(props, database);
        validationCalled = false;
        rev = database.putRevision(rev, null, false, status);
        assertTrue(validationCalled);
        assertEquals("ford", rev.getDocId());

        // DELETE a document:
        rev = new RevisionInternal(rev.getDocId(), rev.getRevId(), true, database);
        assertTrue(rev.isDeleted());
        validationCalled = false;
        rev = database.putRevision(rev, rev.getRevId(), false, status);
        assertTrue(validationCalled);

        // PUT an invalid new document:
        props = new HashMap<String,Object>();
        props.put("_id", "petunias");
        props.put("name","Pot of Petunias");
        rev = new RevisionInternal(props, database);
        validationCalled = false;
        gotExpectedError = false;
        try {
            rev = database.putRevision(rev, null, false, status);
        } catch (CouchbaseLiteException e) {
            gotExpectedError = (e.getCBLStatus().getCode() == Status.FORBIDDEN);
        }
        assertTrue(validationCalled);
        assertTrue(gotExpectedError);
    }

}
