/**
 * Created by Pasin Suriyentrakorn on 10/6/15
 *
 * Copyright (c) 2015 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.lite.performance;

import com.couchbase.lite.Attachment;
import com.couchbase.lite.Document;
import com.couchbase.lite.TransactionalTask;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Test5_ReadAttachments extends PerformanceTestCase {
    public static final String TAG = "ReadAttachmentsPerformance";
    private Document[] docs;

    @Override
    protected String getTestTag() {
        return TAG;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (!performanceTestsEnabled())
            return;

        // Populate documents and attachments:
        char[] chars = new char[getSizeOfAttachment()];
        Arrays.fill(chars, 'a');
        final String content = new String(chars);
        final byte[] bytes = content.toString().getBytes();

        docs = new Document[getNumberOfDocuments()];

        boolean success = database.runInTransaction(new TransactionalTask() {
            public boolean run() {
                try {
                    for (int i = 0; i < getNumberOfDocuments(); i++) {
                        Map<String, Object> properties = new HashMap<String, Object>();
                        properties.put("foo", "bar");

                        Document doc = database.createDocument();
                        UnsavedRevision unsaved = doc.createRevision();
                        unsaved.setProperties(properties);
                        unsaved.setAttachment("attach", "text/plain", new ByteArrayInputStream(bytes));
                        unsaved.save();

                        docs[i] = doc;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Document create with attachment failed", e);
                    return false;
                }
                return true;
            }
        });
        assertTrue(success);
    }

    public void testReadAttachmentsPerformance() throws Exception {
        if (!performanceTestsEnabled())
            return;

        long start = System.currentTimeMillis();
        for (Document doc : docs) {
            Attachment att = doc.getCurrentRevision().getAttachment("attach");
            assertNotNull(att);

            InputStream is = att.getContent();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = is.read(data, 0, data.length)) != -1)
                buffer.write(data, 0, nRead);
            buffer.flush();
            byte[] bytes = buffer.toByteArray();
            assertEquals(getSizeOfAttachment(), bytes.length);
            logPerformanceStats(bytes.length, "Size");
        }
        long end = System.currentTimeMillis();
        logPerformanceStats((end - start), getNumberOfDocuments() + ", " + getSizeOfAttachment());
    }

    private int getSizeOfAttachment() {
        return Integer.parseInt(System.getProperty("test5.sizeOfAttachment"));
    }

    private int getNumberOfDocuments() {
        return Integer.parseInt(System.getProperty("test5.numberOfDocuments"));
    }
}
