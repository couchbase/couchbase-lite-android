package com.couchbase.cblite.testapp.tests;

import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;
import android.util.Log;

import com.couchbase.cblite.TDRevision;
import com.couchbase.cblite.TDStatus;
import com.couchbase.cblite.TDValidationBlock;
import com.couchbase.cblite.TDValidationContext;

public class Validations extends TouchDBTestCase {

    public static final String TAG = "Validations";

    boolean validationCalled = false;

    public void testValidations() {

        TDValidationBlock validationBlock = new TDValidationBlock() {

            @Override
            public boolean validate(TDRevision newRevision, TDValidationContext context) {

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
        TDRevision rev = new TDRevision(props);
        TDStatus status = new TDStatus();
        validationCalled = false;
        rev = database.putRevision(rev, null, false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        // PUT a valid update:
        props.put("head_count", 3);
        rev.setProperties(props);
        validationCalled = false;
        rev = database.putRevision(rev, rev.getRevId(), false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());

        // PUT an invalid update:
        props.remove("towel");
        rev.setProperties(props);
        validationCalled = false;
        rev = database.putRevision(rev, rev.getRevId(), false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(TDStatus.FORBIDDEN, status.getCode());

        // POST an invalid new document:
        props = new HashMap<String,Object>();
        props.put("name","Vogon");
        props.put("poetry", true);
        rev = new TDRevision(props);
        validationCalled = false;
        rev = database.putRevision(rev, null, false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(TDStatus.FORBIDDEN, status.getCode());

        // PUT a valid new document with an ID:
        props = new HashMap<String,Object>();
        props.put("_id", "ford");
        props.put("name","Ford Prefect");
        props.put("towel", "terrycloth");
        rev = new TDRevision(props);
        validationCalled = false;
        rev = database.putRevision(rev, null, false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(TDStatus.CREATED, status.getCode());
        Assert.assertEquals("ford", rev.getDocId());

        // DELETE a document:
        rev = new TDRevision(rev.getDocId(), rev.getRevId(), true);
        Assert.assertTrue(rev.isDeleted());
        validationCalled = false;
        rev = database.putRevision(rev, rev.getRevId(), false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(TDStatus.OK, status.getCode());

        // PUT an invalid new document:
        props = new HashMap<String,Object>();
        props.put("_id", "petunias");
        props.put("name","Pot of Petunias");
        rev = new TDRevision(props);
        validationCalled = false;
        rev = database.putRevision(rev, null, false, status);
        Assert.assertTrue(validationCalled);
        Assert.assertEquals(TDStatus.FORBIDDEN, status.getCode());
    }

}
