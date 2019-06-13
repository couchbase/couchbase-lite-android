//
// LiveQueryTest.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


public class LiveQueryTest extends BaseTest {
    private Query query;
    private ListenerToken token;
    private QueryChangeListener listener;
    private CountDownLatch latch1;
    private CountDownLatch latch2;
    private CountDownLatch latch3;
    private AtomicInteger value;

    // Null query is illegal
    @Test(expected = IllegalArgumentException.class)
    public void testIllegalArgumentException() { new LiveQuery(null); }

    // Creating a document that a query can see should cause an update
    @Test
    public void testBasicLiveQuery() throws CouchbaseLiteException, InterruptedException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(Expression.property("number1").greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property("number1").ascending());

        final CountDownLatch latch = new CountDownLatch(1);
        query.addChangeListener(executor, change -> latch.countDown());

        createDocNumbered(10);
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    // All listeners should hear an update
    @Test
    public void testLiveQueryWith2Listeners() throws CouchbaseLiteException, InterruptedException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(Expression.property("number1").greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property("number1").ascending());

        final CountDownLatch latch = new CountDownLatch(2);
        query.addChangeListener(executor, change -> latch.countDown());
        query.addChangeListener(executor, change -> latch.countDown());

        createDocNumbered(11);
        assertTrue(latch.await(10, TimeUnit.SECONDS));
    }

    // Multiple changes in a short time should cause only a single update
    @Test
    public void testLiveQueryDelay() throws CouchbaseLiteException, InterruptedException {
        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(Expression.property("number1").greaterThanOrEqualTo(Expression.intValue(0)))
            .orderBy(Ordering.property("number1").ascending());

        value = new AtomicInteger(0);
        query.addChangeListener(executor, change -> value.incrementAndGet());

        createDocNumbered(12);
        createDocNumbered(13);
        createDocNumbered(14);

        // Ya, I know...
        Thread.sleep(1000);

        assertEquals(1, value.get());
    }

    // Changing query parameters should cause an update.
    @Test
    public void testChangeParameters() throws CouchbaseLiteException, InterruptedException {
        createDocNumbered(1);

        final Query query = QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(Expression.property("number1").greaterThanOrEqualTo(Expression.parameter("VALUE")))
            .orderBy(Ordering.property("number1").ascending());
        Parameters params = new Parameters();
        params.setInt("VALUE", 2);

        query.addChangeListener(executor, change -> latch1.countDown());

        latch1 = new CountDownLatch(1);
        createDocNumbered(2);
        assertTrue(latch1.await(10, TimeUnit.SECONDS));

        params = new Parameters();
        params.setInt("VALUE", 1);

        latch1 = new CountDownLatch(1);
        query.setParameters(params);
        assertTrue(latch1.await(10, TimeUnit.SECONDS));
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1606
    @Test
    public void testRemovingLiveQuery() throws CouchbaseLiteException, InterruptedException {
        latch1 = new CountDownLatch(1);
        latch2 = new CountDownLatch(1);
        latch3 = new CountDownLatch(1);
        value = new AtomicInteger(1);

        // setup initial
        query = generateQuery();
        listener = generateQueryChangeListener();
        token = query.addChangeListener(executor, listener);

        // creates doc1 -> first query match
        createDocNumbered(1);
        assertTrue(latch1.await(10, TimeUnit.SECONDS));

        // create doc2 -> update query match
        createDocNumbered(2);
        assertTrue(latch2.await(10, TimeUnit.SECONDS));

        // create doc3 -> update query match
        createDocNumbered(3);
        assertTrue(latch3.await(10, TimeUnit.SECONDS));
    }

    // create test docs
    private void createDocNumbered(int i) throws CouchbaseLiteException {
        String docID = String.format(Locale.ENGLISH, "doc%d", i);
        MutableDocument doc = new MutableDocument(docID);
        doc.setValue("number1", i);
        save(doc);
    }

    // generate query
    // NOTE: value variable is updated in QueryChangeListener.
    private Query generateQuery() {
        return QueryBuilder
            .select(SelectResult.expression(Meta.id))
            .from(DataSource.database(db))
            .where(Expression.property("number1").greaterThanOrEqualTo(Expression.intValue(value.intValue())))
            .orderBy(Ordering.property("number1").ascending());
    }

    private QueryChangeListener generateQueryChangeListener() {
        return change -> {
            List<Result> list = change.getResults().allResults();
            if (list.size() <= 0) { return; }

            query.removeChangeListener(token);

            int val = value.getAndIncrement();
            if (val >= 3) {
                latch3.countDown();
                return;
            }

            // update query and listener.
            query = generateQuery();
            listener = generateQueryChangeListener();
            token = query.addChangeListener(executor, listener);

            // notify to main thread to continue
            switch (val) {
                case 1:
                    latch1.countDown();
                    break;
                case 2:
                    latch2.countDown();
                    break;
            }
        };
    }
}
