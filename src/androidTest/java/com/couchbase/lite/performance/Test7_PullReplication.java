/**
 * Original iOS version by  Jens Alfke
 * Ported to Android by Marty Schoch
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
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

import com.couchbase.lite.Document;
import com.couchbase.lite.TransactionalTask;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.util.Log;

import java.io.ByteArrayInputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Test7_PullReplication extends PerformanceTestCase {
    public static final String TAG = "PullReplicationPerformance";

    @Override
    protected String getTestTag() {
        return TAG;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (!performanceTestsEnabled()) {
            return;
        }

        // Prepare and populate documents into the database:
        char[] chars = new char[getSizeOfDocument()];
        Arrays.fill(chars, 'a');
        final String content = new String(chars);

        int attSize = getSizeOfAttachment();
        if (attSize > 0) {
            chars = new char[getSizeOfDocument()];
            Arrays.fill(chars, 'b');
        }
        final byte[] attachment = attSize > 0 ? new String(chars).getBytes() : null;

        boolean success = database.runInTransaction(new TransactionalTask() {
            @Override
            public boolean run() {
                for (int i = 0; i < getNumberOfDocuments(); i++) {
                    try {
                        Map<String, Object> properties = new HashMap<String, Object>();
                        properties.put("content", content);
                        Document document = database.createDocument();
                        UnsavedRevision unsaved = document.createRevision();
                        unsaved.setProperties(properties);
                        if (attachment != null)
                            unsaved.setAttachment("attach", "text/plain",
                                    new ByteArrayInputStream(attachment));
                        assertNotNull(unsaved.save());
                    } catch (Exception e) {
                        Log.e(TAG, "Error when creating documents", e);
                        return false;
                    }
                }
                return true;
            }
        });
        assertTrue(success);

        // Run push replication
        URL remote = getReplicationUrl();
        Replication repl = database.createPushReplication(remote);
        repl.setContinuous(false);
        runReplication(repl);

        // Pause a little to let SyncGateway calm down:
        Thread.sleep(5000);
    }

    public void testPullReplicationPerformance() throws Exception {
        if (!performanceTestsEnabled())
            return;

        URL remote = getReplicationUrl();
        long start = System.currentTimeMillis();
        Replication repl = (Replication) database.createPullReplication(remote);
        repl.setContinuous(false);
        runReplication(repl);
        long end = System.currentTimeMillis();
        logPerformanceStats((end-start), getNumberOfDocuments() + ", " +
                getSizeOfDocument() + ", " + getSizeOfAttachment());
    }

    private int getSizeOfDocument() {
        return Integer.parseInt(System.getProperty("test7.sizeOfDocument"));
    }

    private int getSizeOfAttachment() {
        return Integer.parseInt(System.getProperty("test7.sizeOfAttachment"));
    }

    private int getNumberOfDocuments() {
        return Integer.parseInt(System.getProperty("test7.numberOfDocuments"));
    }
}
