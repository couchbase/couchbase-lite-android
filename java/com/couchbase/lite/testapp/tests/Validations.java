package com.couchbase.lite.testapp.tests;


import com.couchbase.lite.Status;
import com.couchbase.lite.ValidationBlock;
import com.couchbase.lite.ValidationContext;
import com.couchbase.lite.CBLiteException;
import com.couchbase.lite.internal.CBLRevisionInternal;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import java.util.HashMap;
import java.util.Map;


public class Validations extends CBLiteTestCase {

    public static final String TAG = "Validations";

    boolean validationCalled = false;

    public void testValidations() throws CBLiteException {

        ValidationBlock validationBlock = new ValidationBlock() {

            @Override
            public boolean validate(CBLRevisionInternal newRevision, ValidationContext context) {

                Assert.assertNotNull(newRevision);
                Assert.assertNotNull(context);
                Assert.assertTrue(newRevision.getProperties() != null || newRevision.isDeleted());
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
        CBLRevisionInternal rev = new CBLRevisionInternal(props, database);
        Status status = new Status();
        validationCalled = false;
        rev = database.putRevision(rev, null, false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(Status.CREATED, status.getCode());

        // PUT a valid update:
        props.put("head_count", 3);
        rev.setProperties(props);
        validationCalled = false;
        rev = database.putRevision(rev, rev.getRevId(), false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(Status.CREATED, status.getCode());

        // PUT an invalid update:
        props.remove("towel");
        rev.setProperties(props);
        validationCalled = false;
        boolean gotExpectedError = false;
        try {
            rev = database.putRevision(rev, rev.getRevId(), false, status);
        } catch (CBLiteException e) {
            gotExpectedError = (e.getCBLStatus().getCode() == Status.FORBIDDEN);
        }
        Assert.assertTrue(validationCalled);
        Assert.assertTrue(gotExpectedError);

        // POST an invalid new document:
        props = new HashMap<String,Object>();
        props.put("name","Vogon");
        props.put("poetry", true);
        rev = new CBLRevisionInternal(props, database);
        validationCalled = false;
        gotExpectedError = false;
        try {
            rev = database.putRevision(rev, null, false, status);
        } catch (CBLiteException e) {
            gotExpectedError = (e.getCBLStatus().getCode() == Status.FORBIDDEN);
        }
        Assert.assertTrue(validationCalled);
        Assert.assertTrue(gotExpectedError);

        // PUT a valid new document with an ID:
        props = new HashMap<String,Object>();
        props.put("_id", "ford");
        props.put("name","Ford Prefect");
        props.put("towel", "terrycloth");
        rev = new CBLRevisionInternal(props, database);
        validationCalled = false;
        rev = database.putRevision(rev, null, false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals("ford", rev.getDocId());

        // DELETE a document:
        rev = new CBLRevisionInternal(rev.getDocId(), rev.getRevId(), true, database);
        Assert.assertTrue(rev.isDeleted());
        validationCalled = false;
        rev = database.putRevision(rev, rev.getRevId(), false, status);
        Assert.assertTrue(validationCalled);

        // PUT an invalid new document:
        props = new HashMap<String,Object>();
        props.put("_id", "petunias");
        props.put("name","Pot of Petunias");
        rev = new CBLRevisionInternal(props, database);
        validationCalled = false;
        gotExpectedError = false;
        try {
            rev = database.putRevision(rev, null, false, status);
        } catch (CBLiteException e) {
            gotExpectedError = (e.getCBLStatus().getCode() == Status.FORBIDDEN);
        }
        Assert.assertTrue(validationCalled);
        Assert.assertTrue(gotExpectedError);
    }

}
