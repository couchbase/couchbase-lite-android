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

import com.couchbase.lite.Document;
import com.couchbase.lite.TransactionalTask;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.util.Log;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashMap;

public class Test4_CreateAttachments extends PerformanceTestCase {
    public static final String TAG = "CreateAttachmentsPerformance";

    @Override
    protected String getTestTag() {
        return TAG;
    }

    public void testCreateAttachmentsPerformance() throws Exception {
        if (!performanceTestsEnabled())
            return;

        char[] chars = new char[getSizeOfAttachment()];
        Arrays.fill(chars, 'a');
        final String content = new String(chars);
        final byte[] bytes = content.toString().getBytes();

        long start = System.currentTimeMillis();
        boolean success = database.runInTransaction(new TransactionalTask() {
            public boolean run() {
                try {
                    for (int i = 0; i < getNumberOfDocuments(); i++) {
                        Document doc = database.createDocument();
                        UnsavedRevision unsaved = doc.createRevision();
                        unsaved.setProperties(new HashMap<String, Object>());
                        unsaved.setAttachment("attach", "text/plain", new ByteArrayInputStream(bytes));
                        unsaved.save();
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Document create with attachment failed", e);
                    return false;
                }
                return true;
            }
        });
        assertTrue(success);

        long end = System.currentTimeMillis();
        logPerformanceStats((end - start), getNumberOfDocuments() + ", " + getSizeOfAttachment());
    }

    private int getSizeOfAttachment() {
        return Integer.parseInt(System.getProperty("test4.sizeOfAttachment"));
    }

    private int getNumberOfDocuments() {
        return Integer.parseInt(System.getProperty("test4.numberOfDocuments"));
    }
}
