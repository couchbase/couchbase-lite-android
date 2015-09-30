/**
 * Original iOS version by  Jens Alfke
 * Ported to Android by Marty Schoch
 * <p/>
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.lite.performance;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.Status;
import com.couchbase.lite.TransactionalTask;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import java.util.HashMap;
import java.util.Map;

public class Test3_CreateDocsWithAttachments extends LiteTestCaseWithDB {

    public static final String TAG = "CreateDocsWithAttachmentsPerformance";

    private static final String _testAttachmentName = "test_attachment";

    public void testCreateDocsWithAttachmentsPerformance() throws CouchbaseLiteException {

        if (!performanceTestsEnabled())
            return;

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < getSizeOfAttachment(); i++) {
            sb.append('1');
        }
        final byte[] attach1 = sb.toString().getBytes();

        long startMillis = System.currentTimeMillis();

        boolean success = database.runInTransaction(new TransactionalTask() {

            public boolean run() {
                try {

                    Status status = new Status();

                    for (int i = 0; i < getNumberOfDocuments(); i++) {
                        Map<String, Object> rev1Properties = new HashMap<String, Object>();
                        rev1Properties.put("foo", 1);
                        rev1Properties.put("bar", false);
                        rev1Properties.put("_attachments", getAttachmentsDict(attach1, "attach", "text/plain"));
                        RevisionInternal rev1 = database.putRevision(new RevisionInternal(rev1Properties), null, false, status);
                        assertNotNull(rev1);
                        Assert.assertEquals(Status.CREATED, status.getCode());
                    }

                } catch (Throwable t) {
                    Log.e(TAG, "Document create with attachment failed", t);
                    return false;
                }

                return true;
            }
        });
        assertTrue(success);

        Log.e("PerformanceStats", TAG + "," + Long.valueOf(System.currentTimeMillis() - startMillis).toString() + "," + getNumberOfDocuments() + "," + getSizeOfAttachment());
    }

    /**
     * NOTE: gzipped attachment is not supported yet
     */
    private static Map<String, Map<String, Object>> getAttachmentsDict(byte[] data, String name, String type) {
        Map<String, Object> att = new HashMap<String, Object>();
        att.put("content_type", type);
        att.put("data", data);
        Map<String, Map<String, Object>> atts = new HashMap<String, Map<String, Object>>();
        atts.put(name, att);
        return atts;
    }

    private int getSizeOfAttachment() {
        return Integer.parseInt(System.getProperty("Test3_sizeOfAttachment"));
    }

    private int getNumberOfDocuments() {
        return Integer.parseInt(System.getProperty("Test3_numberOfDocuments"));
    }

}
