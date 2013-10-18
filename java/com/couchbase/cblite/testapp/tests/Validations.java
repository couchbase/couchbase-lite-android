package com.couchbase.cblite.testapp.tests;

import android.util.Log;

import com.couchbase.cblite.CBLStatus;
import com.couchbase.cblite.CBLValidationBlock;
import com.couchbase.cblite.CBLValidationContext;
import com.couchbase.cblite.CBLiteException;
import com.couchbase.cblite.internal.CBLRevisionInternal;

import junit.framework.Assert;

import java.util.HashMap;
import java.util.Map;

public class Validations extends CBLiteTestCase {

    public static final String TAG = "Validations";

    boolean validationCalled = false;

    public void testValidations() throws CBLiteException {

        CBLValidationBlock validationBlock = new CBLValidationBlock() {

            @Override
            public boolean validate(CBLRevisionInternal newRevision, CBLValidationContext context) {

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

        database.defineValidation("hoopy", validationBlock);

        // POST a valid new document:
        Map<String, Object> props = new HashMap<String,Object>();
        props.put("name","Zaphod Beeblebrox");
        props.put("towel", "velvet");
        CBLRevisionInternal rev = new CBLRevisionInternal(props, database);
        CBLStatus status = new CBLStatus();
        validationCalled = false;
        rev = database.putRevision(rev, null, false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        // PUT a valid update:
        props.put("head_count", 3);
        rev.setProperties(props);
        validationCalled = false;
        rev = database.putRevision(rev, rev.getRevId(), false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());

        // PUT an invalid update:
        props.remove("towel");
        rev.setProperties(props);
        validationCalled = false;
        rev = database.putRevision(rev, rev.getRevId(), false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(CBLStatus.FORBIDDEN, status.getCode());

        // POST an invalid new document:
        props = new HashMap<String,Object>();
        props.put("name","Vogon");
        props.put("poetry", true);
        rev = new CBLRevisionInternal(props, database);
        validationCalled = false;
        rev = database.putRevision(rev, null, false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(CBLStatus.FORBIDDEN, status.getCode());

        // PUT a valid new document with an ID:
        props = new HashMap<String,Object>();
        props.put("_id", "ford");
        props.put("name","Ford Prefect");
        props.put("towel", "terrycloth");
        rev = new CBLRevisionInternal(props, database);
        validationCalled = false;
        rev = database.putRevision(rev, null, false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(CBLStatus.CREATED, status.getCode());
        Assert.assertEquals("ford", rev.getDocId());

        // DELETE a document:
        rev = new CBLRevisionInternal(rev.getDocId(), rev.getRevId(), true, database);
        Assert.assertTrue(rev.isDeleted());
        validationCalled = false;
        rev = database.putRevision(rev, rev.getRevId(), false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(CBLStatus.OK, status.getCode());

        // PUT an invalid new document:
        props = new HashMap<String,Object>();
        props.put("_id", "petunias");
        props.put("name","Pot of Petunias");
        rev = new CBLRevisionInternal(props, database);
        validationCalled = false;
        rev = database.putRevision(rev, null, false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(CBLStatus.FORBIDDEN, status.getCode());
    }

}
