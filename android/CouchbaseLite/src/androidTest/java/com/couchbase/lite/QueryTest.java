/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite;

import android.os.Handler;
import android.os.Looper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class QueryTest extends BaseTest {

    private interface QueryResult {
        void check(int n, QueryRow row) throws Exception;
    }

    private int verifyQuery(Query query, QueryResult result) throws Exception {
        int n = 0;
        QueryRow row;
        ResultSet rs = query.run();
        while ((row = rs.next()) != null) {
            n += 1;
            result.check(n, row);
        }
        return n;
    }

    private Document createDocNumbered(int i, int num) {
        String docID = String.format(Locale.ENGLISH, "doc%d", i);
        Document doc = createDocument(docID);
        doc.set("number1", i);
        doc.set("number2", num - i);
        return save(doc);
    }

    private List<Map<String, Object>> loadNumbers(final int num) throws Exception {
        final List<Map<String, Object>> numbers = new ArrayList<Map<String, Object>>();
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                for (int i = 1; i <= num; i++) {
                    Document doc = createDocNumbered(i, num);
                    numbers.add(doc.toMap());
                }
            }
        });
        return numbers;
    }

    private void runTestWithNumbers(List<Map<String, Object>> numbers, Object[][] cases)
            throws Exception {
        for (Object[] c : cases) {
            Expression w = (Expression) c[0];
            String[] documentIDs = (String[]) c[1];
            final List<String> docIDList = new ArrayList<String>(Arrays.asList(documentIDs));
            Query q = Query.select().from(DataSource.database(db)).where(w);
            int rows = verifyQuery(q, new QueryResult() {
                @Override
                public void check(int n, QueryRow row) throws Exception {
                    String docID = row.getDocumentID();
                    if (docIDList.contains(docID))
                        docIDList.remove(docID);
                }
            });
            assertEquals(0, docIDList.size());
            assertEquals(documentIDs.length, rows);
        }
    }

    private String[] $docids(int... numbers) {
        String[] documentIDs = new String[numbers.length];
        for (int i = 0; i < numbers.length; i++) {
            documentIDs[i] = "doc" + numbers[i];
        }
        return documentIDs;
    }

    @Test
    public void testNoWhereQuery() throws Exception {
        loadJSONResource("names_100.json");
        Query q = Query.select().from(DataSource.database(db));
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, QueryRow row) throws Exception {
                String expectedID = String.format(Locale.ENGLISH, "doc-%03d", n);
                assertEquals(expectedID, row.getDocumentID());
                assertEquals(n, row.getSequence());
                Document doc = row.getDocument();
                assertEquals(expectedID, doc.getId());
                assertEquals(n, doc.getSequence());
            }
        });
    }

    @Test
    public void testWhereComparison() throws Exception {
        Expression n1 = Expression.property("number1");
        Object[][] cases = {
                {n1.lessThan(3), $docids(1, 2)},
                {n1.notLessThan(3), $docids(3, 4, 5, 6, 7, 8, 9, 10)},
                {n1.lessThanOrEqualTo(3), $docids(1, 2, 3)},
                {n1.notLessThanOrEqualTo(3), $docids(4, 5, 6, 7, 8, 9, 10)},
                {n1.greaterThan(6), $docids(7, 8, 9, 10)},
                {n1.notGreaterThan(6), $docids(1, 2, 3, 4, 5, 6)},
                {n1.greaterThanOrEqualTo(6), $docids(6, 7, 8, 9, 10)},
                {n1.notGreaterThanOrEqualTo(6), $docids(1, 2, 3, 4, 5)},
                {n1.equalTo(7), $docids(7)},
                {n1.notEqualTo(7), $docids(1, 2, 3, 4, 5, 6, 8, 9, 10)}
        };
        List<Map<String, Object>> numbers = loadNumbers(10);
        runTestWithNumbers(numbers, cases);
    }

    @Test
    public void testWhereArithmetic() throws Exception {
        Expression n1 = Expression.property("number1");
        Expression n2 = Expression.property("number2");
        Object[][] cases = {
                {n1.multiply(2).greaterThan(3), $docids(2, 3, 4, 5, 6, 7, 8, 9, 10)},
                {n1.divide(2).greaterThan(3), $docids(8, 9, 10)},
                {n1.modulo(2).equalTo(0), $docids(2, 4, 6, 8, 10)},
                {n1.add(5).greaterThan(10), $docids(6, 7, 8, 9, 10)},
                {n1.subtract(5).greaterThan(0), $docids(6, 7, 8, 9, 10)},
                {n1.multiply(n2).greaterThan(10), $docids(2, 3, 4, 5, 6, 7, 8)},
                {n2.divide(n1).greaterThan(3), $docids(1, 2)},
                {n2.modulo(n1).equalTo(0), $docids(1, 2, 5, 10)},
                {n1.add(n2).equalTo(10), $docids(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)},
                {n1.subtract(n2).greaterThan(0), $docids(6, 7, 8, 9, 10)}
        };
        List<Map<String, Object>> numbers = loadNumbers(10);
        runTestWithNumbers(numbers, cases);
    }

    @Test
    public void testWhereAndOr() throws Exception {
        Expression n1 = Expression.property("number1");
        Expression n2 = Expression.property("number2");
        Object[][] cases = {
                {n1.greaterThan(3).and(n2.greaterThan(3)), $docids(4, 5, 6)},
                {n1.lessThan(3).or(n2.lessThan(3)), $docids(1, 2, 8, 9, 10)}
        };
        List<Map<String, Object>> numbers = loadNumbers(10);
        runTestWithNumbers(numbers, cases);
    }

    //TODO @Test
    public void testWhereCheckNull() throws Exception {
        // https://github.com/couchbase/couchbase-lite-ios/issues/1670
        Document doc1 = createDocument("doc1");
        doc1.set("name", "Scott");
        doc1.set("address", null);
        save(doc1);

        Document doc2 = createDocument("doc2");
        doc2.set("name", "Tiger");
        doc2.set("address", "123 1st ave.");
        save(doc2);

        Expression name = Expression.property("name");
        Expression address = Expression.property("address");
        Expression age = Expression.property("age");
        Expression work = Expression.property("work");

        Object[][] cases = {
                {name.notNull(), $docids(1, 2)},
                {name.isNull(), $docids()},
                {address.notNull(), $docids(2)},
                {address.isNull(), $docids(1)},
                {age.notNull(), $docids(2)},
                {age.isNull(), $docids(1)},
                {work.notNull(), $docids()},
                {work.isNull(), $docids(1, 2)},
        };

        for (Object[] c : cases) {
            Expression exp = (Expression) c[0];
            final String[] documentIDs = (String[]) c[1];
            Query q = Query.select().from(DataSource.database(db)).where(exp);
            int numRows = verifyQuery(q, new QueryResult() {
                @Override
                public void check(int n, QueryRow row) throws Exception {
                    if (n < documentIDs.length) {
                        String docID = documentIDs[(int) n - 1];
                        assertEquals(docID, row.getDocumentID());
                    }
                }
            });
            assertEquals(documentIDs.length, numRows);
        }
    }

    @Test
    public void testWhereIs() throws Exception {
        final Document doc1 = new Document();
        doc1.set("string", "string");
        save(doc1);

        Query q;
        int numRows;

        // Test IS:
        q = Query
                .select()
                .from(DataSource.database(db))
                .where(Expression.property("string").is("string"));
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, QueryRow row) throws Exception {
                Document doc = row.getDocument();
                assertEquals(doc1.getId(), doc.getId());
                assertEquals(doc1.getObject("string"), doc.getObject("string"));
            }
        });
        assertEquals(1, numRows);

        // Test IS NOT:
        q = Query
                .select()
                .from(DataSource.database(db))
                .where(Expression.property("string").isNot("string1"));
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, QueryRow row) throws Exception {
                Document doc = row.getDocument();
                assertEquals(doc1.getId(), doc.getId());
                assertEquals(doc1.getObject("string"), doc.getObject("string"));
            }
        });
        assertEquals(1, numRows);
    }

    @Test
    public void testBetween() throws Exception {
        Expression n1 = Expression.property("number1");
        Object[][] cases = {
                {n1.between(3, 7), $docids(3, 4, 5, 6, 7)}
        };
        List<Map<String, Object>> numbers = loadNumbers(10);
        runTestWithNumbers(numbers, cases);
    }

    @Test
    public void testWhereLike() throws Exception {
        loadJSONResource("names_100.json");

        Expression w = Expression.property("name.first").like("%Mar%");
        Query q = Query
                .select()
                .from(DataSource.database(db))
                .where(w)
                .orderBy(Ordering.property("name.first").ascending());

        final List<String> firstNames = new ArrayList<>();
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, QueryRow row) throws Exception {
                Document doc = row.getDocument();
                Map<String, Object> name = doc.getDictionary("name").toMap();
                if (name != null) {
                    String firstName = (String) name.get("first");
                    if (firstName != null) {
                        firstNames.add(firstName);
                    }
                }
            }
        });
        assertEquals(5, numRows);
        assertEquals(5, firstNames.size());
    }

    @Test
    public void testWhereRegex() throws Exception {
        loadJSONResource("names_100.json");

        Expression w = Expression.property("name.first").regex("^Mar.*");
        Query q = Query
                .select()
                .from(DataSource.database(db))
                .where(w)
                .orderBy(Ordering.property("name.first").ascending());

        final List<String> firstNames = new ArrayList<>();
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, QueryRow row) throws Exception {
                Log.e(TAG, "check() n -> " + n);
                Document doc = row.getDocument();
                Map<String, Object> name = doc.getDictionary("name").toMap();
                if (name != null) {
                    String firstName = (String) name.get("first");
                    if (firstName != null) {
                        firstNames.add(firstName);
                        Log.e(TAG, "firstName -> " + firstName);
                    }
                }
            }
        });
        assertEquals(5, numRows);
        assertEquals(5, firstNames.size());
    }

    @Test
    public void testWhereMatch() throws Exception {
        loadJSONResource("sentences.json");

        Expression[] exps = new Expression[]{Expression.property("sentence")};
        db.createIndex(Arrays.asList(exps), IndexType.FullText, null);

        Expression w = Expression.property("sentence").match("'Dummie woman'");
        Ordering o = Ordering.property("rank(sentence)").descending();
        Query q = Query.select().from(DataSource.database(db)).where(w).orderBy(o);
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, QueryRow row) throws Exception {
                FullTextQueryRow ftsRow = (FullTextQueryRow) row;
                String text = ftsRow.getFullTextMatched();
                assertNotNull(text);
                assertTrue(text.contains("Dummie"));
                assertTrue(text.contains("woman"));
                assertEquals(2, ftsRow.getMatchCount());
            }
        });
        assertEquals(2, numRows);
    }

    @Test
    public void testOrderBy() throws Exception {
        loadJSONResource("names_100.json");

        boolean[] cases = {true, false};
        for (final boolean ascending : cases) {
            Ordering o;
            if (ascending)
                o = Ordering.expression(Expression.property("name.first")).ascending();
            else
                o = Ordering.expression(Expression.property("name.first")).descending();
            Query q = Query.select().from(DataSource.database(db)).orderBy(o);

            final List<String> firstNames = new ArrayList<String>();
            int numRows = verifyQuery(q, new QueryResult() {
                @Override
                public void check(int n, QueryRow row) throws Exception {
                    Document doc = row.getDocument();
                    Map<String, Object> name = doc.getDictionary("name").toMap();
                    String firstName = (String) name.get("first");
                    firstNames.add(firstName);
                }
            });
            assertEquals(100, numRows);

            List<String> sorted = new ArrayList<>(firstNames);
            Collections.sort(sorted, new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    return ascending ? o1.compareTo(o2) : o2.compareTo(o1);
                }
            });
            String[] array1 = firstNames.toArray(new String[firstNames.size()]);
            String[] array2 = firstNames.toArray(new String[sorted.size()]);
            assertTrue(Arrays.equals(array1, array2));
        }
    }

    //TODO @Test
    public void testSelectDistinct() throws Exception {
        // https://github.com/couchbase/couchbase-lite-ios/issues/1669
        // https://github.com/couchbase/couchbase-lite-core/issues/81
        final Document doc1 = new Document();
        doc1.set("number", 1);
        save(doc1);

        Document doc2 = new Document();
        doc2.set("number", 1);
        save(doc2);

        Query q = Query.selectDistinct().from(DataSource.database(db));
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, QueryRow row) throws Exception {
                assertEquals(doc1.getId(), row.getDocumentID());
            }
        });
        assertEquals(1, numRows);
    }

    @Test
    public void testJoin() throws Exception {
        loadNumbers(100);

        final Document doc1 = new Document("joinme");
        doc1.set("theone", 42);
        save(doc1);

        DataSource mainDS = DataSource.database(this.db).as("main");
        DataSource secondaryDS = DataSource.database(this.db).as("secondary");
        Expression mainPropExpr = Expression.property("main", "number1");
        Expression secondaryExpr = Expression.property("secondary", "theone");
        Expression joinExpr = mainPropExpr.equalTo(secondaryExpr);
        Join join = Join.join(secondaryDS).on(joinExpr);
        Query q = Query.select().from(mainDS).join(join);

        assertNotNull(q);
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, QueryRow row) throws Exception {
                Document doc = row.getDocument();
                assertEquals(42, doc.getInt("number1"));
            }
        });
        assertEquals(1, numRows);
    }

    @Test
    public void testAggregateFunctions() throws Exception {
        loadNumbers(100);

        DataSource ds = DataSource.database(this.db);
        Expression exprNum1 = Expression.property("number1");

        Function avg = Function.avg(exprNum1);
        Function cnt = Function.count(exprNum1);
        Function min = Function.min(exprNum1);
        Function max = Function.max(exprNum1);
        Function sum = Function.sum(exprNum1);

        SelectResult rsAvg = SelectResult.expression(avg);
        SelectResult rsCnt = SelectResult.expression(cnt);
        SelectResult rsMin = SelectResult.expression(min);
        SelectResult rsMax = SelectResult.expression(max);
        SelectResult rsSum = SelectResult.expression(sum);

        Query q = Query.select(rsAvg, rsCnt, rsMin, rsMax, rsSum).from(ds);
        assertNotNull(q);
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, QueryRow row) throws Exception {
                assertEquals(50.5F, (float) row.getObject(0), 0.0F);
                assertEquals(100L, (long) row.getObject(1));
                assertEquals(1L, (long) row.getObject(2));
                assertEquals(100L, (long) row.getObject(3));
                assertEquals(5050L, (long) row.getObject(4));
            }
        });
        assertEquals(1, numRows);
    }

    @Test
    public void testGroupBy() throws Exception {
        loadJSONResource("names_100.json");

        final List<String> expectedStates = Arrays.asList("AL", "CA", "CO", "FL", "IA");
        final List<Integer> expectedCounts = Arrays.asList(1, 6, 1, 1, 3);
        final List<String> expectedMaxZips = Arrays.asList("35243", "94153", "81223", "33612", "50801");

        DataSource ds = DataSource.database(this.db);

        Expression state = Expression.property("contact.address.state");
        Expression count = Function.count(1);
        Expression zip = Expression.property("contact.address.zip");
        Expression maxZip = Function.max(zip);
        Expression gender = Expression.property("gender");

        SelectResult rsState = SelectResult.expression(state);
        SelectResult rsCount = SelectResult.expression(count);
        SelectResult rsMaxZip = SelectResult.expression(maxZip);

        Expression groupByExpr = state;
        Ordering ordering = Ordering.expression(state);

        Query q = Query
                .select(rsState, rsCount, rsMaxZip)
                .from(ds)
                .where(gender.equalTo("female"))
                .groupBy(groupByExpr)
                .having(null)
                .orderBy(ordering);
        assertNotNull(q);
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, QueryRow row) throws Exception {
                String state = (String) row.getObject(0);
                long count = (long) row.getObject(1);
                String maxZip = (String) row.getObject(2);
                Log.e(TAG, "state=%s, count=%d, maxZip=%s", state, count, maxZip);
                if (n - 1 < expectedStates.size()) {
                    assertEquals(expectedStates.get(n - 1), state);
                    assertEquals((int) expectedCounts.get(n - 1), count);
                    assertEquals(expectedMaxZips.get(n - 1), maxZip);
                }
            }
        });
        assertEquals(31, numRows);

        // With HAVING:
        final List<String> expectedStates2 = Arrays.asList("CA", "IA", "IN");
        final List<Integer> expectedCounts2 = Arrays.asList(6, 3, 2);
        final List<String> expectedMaxZips2 = Arrays.asList("94153", "50801", "47952");

        Expression havingExpr = count.greaterThan(1);

        q = Query
                .select(rsState, rsCount, rsMaxZip)
                .from(ds)
                .where(gender.equalTo("female"))
                .groupBy(groupByExpr)
                .having(havingExpr)
                .orderBy(ordering);
        assertNotNull(q);
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, QueryRow row) throws Exception {
                String state = (String) row.getObject(0);
                long count = (long) row.getObject(1);
                String maxZip = (String) row.getObject(2);
                if (n - 1 < expectedStates2.size()) {
                    assertEquals(expectedStates2.get(n - 1), state);
                    assertEquals((long) expectedCounts2.get(n - 1), count);
                    assertEquals(expectedMaxZips2.get(n - 1), maxZip);
                }
            }
        });
        assertEquals(15, numRows);
    }

    @Test
    public void testLiveQuery() throws Exception {
        loadNumbers(100);

        LiveQuery query = Query
                .select()
                .from(DataSource.database(db))
                .where(Expression.property("number1").lessThan(10))
                .orderBy(Ordering.property("number1").ascending())
                .toLive();

        final CountDownLatch latch = new CountDownLatch(2);
        LiveQueryChangeListener listener = new LiveQueryChangeListener() {
            @Override
            public void changed(LiveQueryChange change) {
                assertNotNull(change);
                ResultSet rs = change.getRows();
                assertNotNull(rs);
                if (latch.getCount() == 2) {
                    int count = 0;
                    while (rs.next() != null)
                        count++;
                    assertEquals(9, count);
                } else if (latch.getCount() == 1) {
                    QueryRow queryRow;
                    int count = 0;
                    while ((queryRow = rs.next()) != null) {
                        if (count == 0)
                            assertEquals(-1L, queryRow.getDocument().getObject("number1"));
                        count++;
                    }
                    assertEquals(10, count);
                }
                latch.countDown();
            }
        };
        query.addChangeListener(listener);

        query.run();
        try {
            // create one doc
            new Handler(Looper.getMainLooper())
                    .postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            createDocNumbered(-1, 100);
                        }
                    }, 500); // 500ms

            // wait till listener is called
            assertTrue(latch.await(2, TimeUnit.SECONDS));
        } finally {
            query.removeChangeListener(listener);
            query.stop();
        }
    }

    @Test
    public void testLiveQueryNoUpdate() throws Exception {
        _testLiveQueryNoUpdate(false);
    }

    @Test
    public void testLiveQueryNoUpdateConsumeAll() throws Exception {
        _testLiveQueryNoUpdate(true);
    }

    private void _testLiveQueryNoUpdate(final boolean consumeAll) throws Exception {
        loadNumbers(100);

        LiveQuery query = Query
                .select()
                .from(DataSource.database(db))
                .where(Expression.property("number1").lessThan(10))
                .orderBy(Ordering.property("number1").ascending())
                .toLive();

        final CountDownLatch latch = new CountDownLatch(2);
        LiveQueryChangeListener listener = new LiveQueryChangeListener() {
            @Override
            public void changed(LiveQueryChange change) {
                if (consumeAll) {
                    ResultSet rs = change.getRows();
                    while (rs.next() != null) ;
                }
                latch.countDown();
                // should come only once!
            }
        };
        query.addChangeListener(listener);

        query.run();
        try {
            // create one doc
            new Handler(Looper.getMainLooper())
                    .postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            createDocNumbered(111, 100);
                        }
                    }, 500); // 500ms
            // wait till listener is called
            assertFalse(latch.await(5, TimeUnit.SECONDS));
            assertEquals(1, latch.getCount());
        } finally {
            query.removeChangeListener(listener);
            query.stop();
        }
    }
}
