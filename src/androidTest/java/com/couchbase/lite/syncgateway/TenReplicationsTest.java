/**
 * Created by hideki on 2/29/16.
 * <p/>
 * Copyright (c) 2015 Couchbase, Inc All rights reserved.
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
 *
 */
package com.couchbase.lite.syncgateway;

import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.replicator.Replication;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TenReplicationsTest extends LiteTestCaseWithDB {
    public static final String TAG = "TenReplicationsTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (!syncgatewayTestsEnabled()) {
            return;
        }
    }

    public void testStopAllReplsAndDeleteDB() throws Exception {
        if (!syncgatewayTestsEnabled()) {
            return;
        }

        // create 10 repls with different filter and wait till all becomes idle state
        final int NUM_REPLS = 10;
        List<Replication> repls = new ArrayList<Replication>(NUM_REPLS);
        for (int i = 0; i < NUM_REPLS; i++) {
            Replication repl;
            if (i % 2 == 0)
                repl = database.createPushReplication(getReplicationURL());
            else
                repl = database.createPullReplication(getReplicationURL());
            repl.setContinuous(true);

            repl.setFilter("foo/bar" + i);
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("value", i);
            repl.setFilterParams(params);

            final CountDownLatch idle = new CountDownLatch(1);
            repl.addChangeListener(new ReplicationIdleObserver(idle));
            repl.start();
            assertTrue(idle.await(10, TimeUnit.SECONDS));
        }

        // make sure if 10 repls are active
        assertEquals(NUM_REPLS, database.getActiveReplications().size());

        // stop all repls by repl.stop()
        for (final Replication repl : repls)
            repl.stop();
        // delete db and measure elapsed time
        long start = System.currentTimeMillis();
        database.delete();
        assertTrue((System.currentTimeMillis() - start) / 1000 < 30);
    }
}