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

import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.auth.AuthenticatorFactory;
import com.couchbase.lite.internal.RevisionInternal;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.support.RevisionUtils;
import com.couchbase.lite.util.Log;

import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 * Created by hideki on 7/6/16.
 */
public class AutoPruningTest  extends LiteTestCaseWithDB {
    public static final String TAG = "AutoPruningTest";

    @Override
    protected void setUp() throws Exception {
        if (!syncgatewayTestsEnabled()) {
            return;
        }
        super.setUp();
    }

    // https://github.com/couchbase/couchbase-lite-java-core/issues/1319
    public void testPullReplAutoPruning() throws Exception {
        if (!syncgatewayTestsEnabled()) {
            return;
        }

        Database pushDB = manager.getDatabase("pushdb");
        Database pullDB = manager.getDatabase("pulldb");

        // set max rev tree depth 5 to pull database
        pullDB.setMaxRevTreeDepth(5);
        pushDB.setMaxRevTreeDepth(100);
        URL remote = getReplicationURL();
        Replication push = pushDB.createPushReplication(remote);
        Replication pull = pullDB.createPullReplication(remote);
        push.setContinuous(true);
        pull.setContinuous(true);

        final CountDownLatch pushIdle = new CountDownLatch(1);
        final CountDownLatch pullIdle = new CountDownLatch(1);
        push.addChangeListener(new ReplicationIdleObserver(pushIdle));
        pull.addChangeListener(new ReplicationIdleObserver(pullIdle));

        pull.start();
        push.start();

        assertTrue(pushIdle.await(30, TimeUnit.SECONDS));
        assertTrue(pullIdle.await(30, TimeUnit.SECONDS));

        final CountDownLatch pushIdle2 = new CountDownLatch(1);
        final CountDownLatch pullIdle2 = new CountDownLatch(1);
        push.addChangeListener(new ReplicationIdleObserver(pushIdle2));
        pull.addChangeListener(new ReplicationIdleObserver(pullIdle2));

        // create doc with 30 revisions in push db
        Document doc = pushDB.createDocument();
        Map<String, Object> props = new HashMap<>();
        props.put("index", 0);
        doc.putProperties(props);
        String docID = doc.getId();
        for(int i = 1; i < 100; i++){
            doc = pushDB.getDocument(docID);
            props = new HashMap<String, Object>();
            props.putAll(doc.getProperties());
            props.put("index", i);
            doc.putProperties(props);
        }

        assertTrue(pushIdle2.await(30, TimeUnit.SECONDS));
        assertTrue(pullIdle2.await(30, TimeUnit.SECONDS));

        RevisionInternal rev = pullDB.getDocument(docID, null, true);
        Log.e(TAG, "rev [%s]", rev);
        List<RevisionInternal> revs = pullDB.getRevisionHistory(rev);
        Log.e(TAG, "revs [%s]", revs);
        Map<String, Object> historyDict = RevisionUtils.makeRevisionHistoryDict(revs);
        Log.e(TAG, "historyDict [%s]", historyDict);
        assertNotNull(rev);
        assertTrue(rev.getRevID().startsWith("100-"));
        assertNotNull(revs);
        assertEquals(5, revs.size());
        assertNotNull(historyDict);
        assertEquals(100, (int)historyDict.get("start"));
        assertEquals(5, ((List<String>)historyDict.get("ids")).size());


        final CountDownLatch pushDone = new CountDownLatch(1);
        final CountDownLatch pullDone = new CountDownLatch(1);
        push.addChangeListener(new ReplicationFinishedObserver(pushDone));
        pull.addChangeListener(new ReplicationFinishedObserver(pullDone));

        pull.stop();
        push.stop();

        assertTrue(pushDone.await(30, TimeUnit.SECONDS));
        assertTrue(pullDone.await(30, TimeUnit.SECONDS));

    }
}
