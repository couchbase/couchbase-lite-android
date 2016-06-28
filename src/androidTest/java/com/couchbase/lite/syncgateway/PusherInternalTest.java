//
// Copyright (c) 2016 Couchbase, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.syncgateway;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.TransactionalTask;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.util.Log;

import java.util.HashMap;
import java.util.Map;


/**
 * Created by hideki on 6/27/16.
 */
public class PusherInternalTest extends LiteTestCaseWithDB {
    public void testPushWithLargeDataSet() throws Exception {
        if (!syncgatewayTestsEnabled())
            return;
        if (!isSQLiteDB())
            return;

        // creates 25K docs
        createDocs(database, 250);
        // make sure 25K docs are created
        assertEquals(250 * 100, database.getDocumentCount());
        // start push replication
        Replication push = database.createPushReplication(getReplicationURL());
        assertNotNull(push);
        runReplication(push);
        // make sure 25K docs are pushed
        assertEquals(250 * 100, push.getChangesCount());
    }

    // creates xK * 100 docs with 10KB/doc
    private static void createDocs(final Database db, int xK) {
        // prepare 10K data for doc
        final StringBuffer sb = new StringBuffer();
        for (int i = 0; i < 1000; i++) {
            sb.append("1234567890");
        }

        // create xK * 100 docs
        for (int i = 0; i < xK; i++) {
            final int j = i;
            assertTrue(db.runInTransaction(new TransactionalTask() {
                @Override
                public boolean run() {
                    for (int k = 0; k < 100; k++) {
                        Map<String, Object> props = new HashMap<String, Object>();
                        props.put("key", j * 1000 + k);
                        props.put("value", sb.toString());
                        Document doc = db.createDocument();
                        try {
                            doc.putProperties(props);
                        } catch (CouchbaseLiteException e) {
                            Log.e(TAG, "Error in Document.putProperperties() props=%s", e, props);
                            return false;
                        }
                    }
                    return true;
                }
            }));
        }
    }
}
