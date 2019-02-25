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

import com.couchbase.lite.internal.support.Log;

import org.junit.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LiveQueryTest extends BaseTest {
    private static Expression EXPR_NUMBER1 = Expression.property("number1");
    private static Expression EXPR_NUMBER2 = Expression.property("number2");

    private static SelectResult SR_DOCID = SelectResult.expression(Meta.id);
    private static SelectResult SR_SEQUENCE = SelectResult.expression(Meta.sequence);
    private static SelectResult SR_ALL = SelectResult.all();
    private static SelectResult SR_NUMBER1 = SelectResult.property("number1");

    @Test
    public void testIllegalArgumentException() {
        try {
            new LiveQuery(null);
            fail();
        } catch (IllegalArgumentException ex) {
            // ok
        }
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1606
    Query query;
    ListenerToken token;
    QueryChangeListener listener;
    CountDownLatch latch1;
    CountDownLatch latch2;
    CountDownLatch latch3;
    AtomicInteger value;

    @Test
    public void testRemovingLiveQuery() throws Exception {
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
    Document createDocNumbered(int i) throws CouchbaseLiteException {
        String docID = String.format(Locale.ENGLISH, "doc%d", i);
        MutableDocument doc = createMutableDocument(docID);
        doc.setValue("number1", i);
        return save(doc);
    }

    // generate query
    Query generateQuery() {
        // NOTE: value variable is updated in QueryChangeListener.
        return QueryBuilder
                .select(SR_DOCID)
                .from(DataSource.database(db))
                .where(EXPR_NUMBER1.greaterThanOrEqualTo(Expression.intValue(value.intValue())))
                .orderBy(Ordering.property("number1").ascending());
    }

    QueryChangeListener generateQueryChangeListener() {
        return new QueryChangeListener() {
            @Override
            public void changed(QueryChange change) {
                List<Result> list = change.getResults().allResults();
                // no document match
                if (list.size() <= 0)
                    return;

                // remove current listener
                query.removeChangeListener(token);
                listener = null;

                // increment value
                value.incrementAndGet();

                // stop if value reaches 4.
                if (value.get() > 3) {
                    latch3.countDown();
                    return;
                }

                // update query and listener.
                query = generateQuery();
                listener = generateQueryChangeListener();
                token = query.addChangeListener(executor, listener);

                // notify to main thread to create next document
                if (value.get() == 2)
                    latch1.countDown();
                else if (value.get() == 3)
                    latch2.countDown();
            }
        };
    }
}
