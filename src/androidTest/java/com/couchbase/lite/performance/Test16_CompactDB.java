/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
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
 */package com.couchbase.lite.performance;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Document;
import com.couchbase.lite.TransactionalTask;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.util.Log;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Created by hideki on 12/22/15.
 */
public class Test16_CompactDB extends PerformanceTestCase {
    public static final String TAG = "CompactDBPerformance";

    @Override
    protected String getTestTag() {
        return TAG;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (!performanceTestsEnabled())
            return;

        // Populate documents into the database:
        final int docSize = getSizeOfDocument();
        char[] chars = new char[docSize];
        Arrays.fill(chars, 'a');
        final String content = new String(chars);

        final int attSize = getSizeOfAttachment();
        if (attSize > 0) {
            chars = new char[attSize];
            Arrays.fill(chars, 'b');
        }
        final byte[] attachmentBytes = attSize > 0 ? new String(chars).getBytes() : null;

        boolean success = database.runInTransaction(new TransactionalTask() {
            public boolean run() {
                for (int i = 0; i < getNumberOfDocuments(); i++) {
                    try {
                        Document document = database.createDocument();
                        UnsavedRevision unsaved = document.createRevision();

                        if (docSize > 0) {
                            Map<String, Object> properties = new HashMap<String, Object>();
                            properties.put("content", content);
                            unsaved.setProperties(properties);
                        }

                        boolean hasAttachments = false;
                        int numAtts = getNumberOfAttacment();
                        if (attSize > 0 && numAtts > 0) {
                            hasAttachments = true;
                            for (int j = 0; j < getNumberOfAttacment(); j++) {
                                String prefix = i + "-" + j;
                                byte[] prefixBytes = prefix.getBytes();
                                byte[] bytes = new byte[attachmentBytes.length + prefixBytes.length];
                                System.arraycopy(prefixBytes, 0, bytes, 0, prefixBytes.length);
                                System.arraycopy(attachmentBytes, 0, bytes, prefixBytes.length, attachmentBytes.length);

                                String name = String.format(Locale.ENGLISH, "attach_%d", j);
                                unsaved.setAttachment(name, "text/plain", new ByteArrayInputStream(bytes));
                            }
                        }
                        assertNotNull(unsaved.save());

                        // create some revisions
                        for (int k = 0; k < getNumberOfRevisions(); k++) {
                            Map<String, Object> updateProps = new HashMap<String, Object>();
                            updateProps.putAll(document.getProperties());
                            updateProps.put("update", k);
                            document.putProperties(updateProps);
                        }

                        // deleting attachments
                        if (deleteAttachment() && hasAttachments) {
                            unsaved = document.createRevision();
                            for (int j = 0; j < getNumberOfAttacment(); j++) {
                                String name = String.format(Locale.ENGLISH, "attach_%d", j);
                                unsaved.removeAttachment(name);
                            }
                            unsaved.save();
                        }

                    } catch (CouchbaseLiteException e) {
                        Log.e(TAG, "Error when creating a document", e);
                        return false;
                    }
                }
                return true;
            }
        });
        assertTrue(success);
    }

    public void testCompactDBPerformance() throws CouchbaseLiteException {
        if (!performanceTestsEnabled())
            return;

        long start = System.currentTimeMillis();

        database.compact();

        long end = System.currentTimeMillis();
        logPerformanceStats((end - start), getNumberOfDocuments() + ", " + getSizeOfDocument());
    }

    private int getSizeOfDocument() {
        return Integer.parseInt(System.getProperty("test16.sizeOfDocument"));
    }

    private int getNumberOfDocuments() {
        return Integer.parseInt(System.getProperty("test16.numberOfDocuments"));
    }

    private int getSizeOfAttachment() {
        return Integer.parseInt(System.getProperty("test16.sizeOfAttachment"));
    }

    private int getNumberOfAttacment() {
        return Integer.parseInt(System.getProperty("test16.numOfAttachment"));
    }

    private int getNumberOfRevisions() {
        return Integer.parseInt(System.getProperty("test16.numOfRevisions"));
    }

    private boolean deleteAttachment() {
        return Boolean.parseBoolean(System.getProperty("test16.deleteAttachment"));
    }
}
