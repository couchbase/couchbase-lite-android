package com.couchbase.touchdb.testapp.tests;

import java.io.File;

import com.couchbase.touchdb.testapp.CopySampleAttachmentsActivity;

import junit.framework.Assert;

import android.app.Activity;
import android.test.ActivityInstrumentationTestCase2;

public class CopySampleAttachmentsActivityTest extends
ActivityInstrumentationTestCase2 {
    public static final String TAG = "CopySampleAttachmentsActivityTest";
    public Activity mActivity;

    public CopySampleAttachmentsActivityTest() {
        super("com.couchbase.touchdb.testapp", CopySampleAttachmentsActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        mActivity = this.getActivity();
        super.setUp();
    }

    public void testCopySampleAttachmentsToSDCARD() throws Throwable {
        Assert.assertNotNull(mActivity);

        File sampleImage1 = new File(mActivity.getFilesDir()
                + "/sample_attachment_image1.jpg");
        Assert.assertTrue(sampleImage1.exists());

        File sampleImage2 = new File(mActivity.getFilesDir()
                + "/sample_attachment_image2.jpg");
        Assert.assertTrue(sampleImage2.exists());

        mActivity.finish();
    }
}
