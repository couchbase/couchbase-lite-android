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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.couchbase.lite.DataSource.database;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class QueryTest extends BaseTest {

    private interface QueryResult {
        void check(int n, Result result) throws Exception;
    }

    private int verifyQuery(Query query, QueryResult queryResult) throws Exception {
        int n = 0;
        Result result;
        ResultSet rs = query.run();
        while ((result = rs.next()) != null) {
            n += 1;
            queryResult.check(n, result);
        }
        return n;
    }

    private int verifyQueryWithIterable(Query query, QueryResult queryResult) throws Exception {
        int n = 0;
        for (Result result : query.run()) {
            n += 1;
            queryResult.check(n, result);
        }
        return n;
    }

    private int verifyQuery(Query query, QueryResult result, boolean runBoth) throws Exception {
        int counter1 = verifyQuery(query, result);
        if (runBoth) {
            int counter2 = verifyQueryWithIterable(query, result);
            assertEquals(counter1, counter2);
        }
        return counter1;
    }

    private Document createDocNumbered(int i, int num) throws CouchbaseLiteException {
        String docID = String.format(Locale.ENGLISH, "doc%d", i);
        Document doc = createDocument(docID);
        doc.setObject("number1", i);
        doc.setObject("number2", num - i);
        return save(doc);
    }

    private List<Map<String, Object>> loadNumbers(final int num) throws Exception {
        final List<Map<String, Object>> numbers = new ArrayList<Map<String, Object>>();
        db.inBatch(new Runnable() {
            @Override
            public void run() {
                for (int i = 1; i <= num; i++) {
                    Document doc = null;
                    try {
                        doc = createDocNumbered(i, num);
                    } catch (CouchbaseLiteException e) {
                        throw new RuntimeException(e);
                    }
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
            Query q = Query.select().from(database(db)).where(w);
            int rows = verifyQuery(q, new QueryResult() {
                @Override
                public void check(int n, Result result) throws Exception {
                    String docID = result.getDocumentID();
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
        Query q = Query.select().from(database(db));
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                String expectedID = String.format(Locale.ENGLISH, "doc-%03d", n);
                assertEquals(expectedID, result.getDocumentID());
                assertEquals(n, result.getSequence());
                Document doc = result.getDocument();
                assertEquals(expectedID, doc.getId());
                assertEquals(n, doc.getSequence());
            }
        }, true);
        assertEquals(100, numRows);
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
        doc1.setObject("name", "Scott");
        doc1.setObject("address", null);
        save(doc1);

        Document doc2 = createDocument("doc2");
        doc2.setObject("name", "Tiger");
        doc2.setObject("address", "123 1st ave.");
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
            Query q = Query.select().from(database(db)).where(exp);
            int numRows = verifyQuery(q, new QueryResult() {
                @Override
                public void check(int n, Result result) throws Exception {
                    if (n < documentIDs.length) {
                        String docID = documentIDs[(int) n - 1];
                        assertEquals(docID, result.getDocumentID());
                    }
                }
            }, true);
            assertEquals(documentIDs.length, numRows);
        }
    }

    @Test
    public void testWhereIs() throws Exception {
        final Document doc1 = new Document();
        doc1.setObject("string", "string");
        save(doc1);

        Query q;
        int numRows;

        // Test IS:
        q = Query
                .select()
                .from(database(db))
                .where(Expression.property("string").is("string"));
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                Document doc = result.getDocument();
                assertEquals(doc1.getId(), doc.getId());
                assertEquals(doc1.getObject("string"), doc.getObject("string"));
            }
        }, true);
        assertEquals(1, numRows);

        // Test IS NOT:
        q = Query
                .select()
                .from(database(db))
                .where(Expression.property("string").isNot("string1"));
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                Document doc = result.getDocument();
                assertEquals(doc1.getId(), doc.getId());
                assertEquals(doc1.getObject("string"), doc.getObject("string"));
            }
        }, true);
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
                .from(database(db))
                .where(w)
                .orderBy(Ordering.property("name.first").ascending());

        final List<String> firstNames = new ArrayList<>();
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                Document doc = result.getDocument();
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
                .from(database(db))
                .where(w)
                .orderBy(Ordering.property("name.first").ascending());

        final List<String> firstNames = new ArrayList<>();
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                Log.e(TAG, "check() n -> " + n);
                Document doc = result.getDocument();
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
        Query q = Query.select().from(database(db)).where(w).orderBy(o);
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                FullTextResult ftsRow = (FullTextResult) result;
                String text = ftsRow.getFullTextMatched();
                assertNotNull(text);
                assertTrue(text.contains("Dummie"));
                assertTrue(text.contains("woman"));
                assertEquals(2, ftsRow.getMatchCount());
            }
        }, true);
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
            Query q = Query.select().from(database(db)).orderBy(o);

            final List<String> firstNames = new ArrayList<String>();
            int numRows = verifyQuery(q, new QueryResult() {
                @Override
                public void check(int n, Result result) throws Exception {
                    Document doc = result.getDocument();
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
        doc1.setObject("number", 1);
        save(doc1);

        Document doc2 = new Document();
        doc2.setObject("number", 1);
        save(doc2);

        Query q = Query.selectDistinct().from(database(db));
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertEquals(doc1.getId(), result.getDocumentID());
            }
        }, true);
        assertEquals(1, numRows);
    }

    @Test
    public void testJoin() throws Exception {
        loadNumbers(100);

        final Document doc1 = new Document("joinme");
        doc1.setObject("theone", 42);
        save(doc1);

        DataSource mainDS = database(this.db).as("main");
        DataSource secondaryDS = database(this.db).as("secondary");
        Expression mainPropExpr = Expression.property("number1").from("main");
        Expression secondaryExpr = Expression.property("theone").from("secondary");
        Expression joinExpr = mainPropExpr.equalTo(secondaryExpr);
        Join join = Join.join(secondaryDS).on(joinExpr);
        Query q = Query.select().from(mainDS).join(join);

        assertNotNull(q);
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                Document doc = result.getDocument();
                assertEquals(42, doc.getInt("number1"));
            }
        }, true);
        assertEquals(1, numRows);
    }

    @Test
    public void testGroupBy() throws Exception {
        loadJSONResource("names_100.json");

        final List<String> expectedStates = Arrays.asList("AL", "CA", "CO", "FL", "IA");
        final List<Integer> expectedCounts = Arrays.asList(1, 6, 1, 1, 3);
        final List<String> expectedMaxZips = Arrays.asList("35243", "94153", "81223", "33612", "50801");

        DataSource ds = database(this.db);

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
            public void check(int n, Result result) throws Exception {
                String state = (String) result.getObject(0);
                long count = (long) result.getObject(1);
                String maxZip = (String) result.getObject(2);
                Log.e(TAG, "state=%s, count=%d, maxZip=%s", state, count, maxZip);
                if (n - 1 < expectedStates.size()) {
                    assertEquals(expectedStates.get(n - 1), state);
                    assertEquals((int) expectedCounts.get(n - 1), count);
                    assertEquals(expectedMaxZips.get(n - 1), maxZip);
                }
            }
        }, true);
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
            public void check(int n, Result result) throws Exception {
                String state = (String) result.getObject(0);
                long count = (long) result.getObject(1);
                String maxZip = (String) result.getObject(2);
                if (n - 1 < expectedStates2.size()) {
                    assertEquals(expectedStates2.get(n - 1), state);
                    assertEquals((long) expectedCounts2.get(n - 1), count);
                    assertEquals(expectedMaxZips2.get(n - 1), maxZip);
                }
            }
        }, true);
        assertEquals(15, numRows);
    }

    @Test
    public void testParameters() throws Exception {
        loadNumbers(100);

        DataSource dataSource = database(this.db);

        Expression number1 = Expression.property("number1");
        Expression paramN1 = Expression.parameter("num1");
        Expression paramN2 = Expression.parameter("num2");

        SelectResult selectResult = SelectResult.expression(number1);

        Ordering ordering = Ordering.expression(number1);

        Query q = Query
                .select(selectResult)
                .from(dataSource)
                .where(number1.between(paramN1, paramN2))
                .orderBy(ordering);

        q.parameters().setObject("num1", 2).setObject("num2", 5);

        final long[] expectedNumbers = {2, 3, 4, 5};
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                long number = (long) result.getObject(0);
                assertEquals(expectedNumbers[n - 1], number);
            }
        }, true);
        assertEquals(4, numRows);
    }

    @Test
    public void testMeta() throws Exception {
        loadNumbers(5);

        DataSource dataSource = database(this.db);

        Expression metaDocID = Expression.meta().getId();
        Expression metaDocSeq = Expression.meta().getSequence();
        Expression propNumber1 = Expression.property("number1");

        SelectResult srDocID = SelectResult.expression(metaDocID);
        SelectResult srDocSeq = SelectResult.expression(metaDocSeq);
        SelectResult srNumber1 = SelectResult.expression(propNumber1);

        Query q = Query
                .select(srDocID, srDocSeq, srNumber1)
                .from(dataSource)
                .orderBy(Ordering.expression(metaDocSeq));

        final String[] expectedDocIDs = {"doc1", "doc2", "doc3", "doc4", "doc5"};
        final long[] expectedSeqs = {1, 2, 3, 4, 5};
        final long[] expectedNumbers = {1, 2, 3, 4, 5};

        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                String docID = (String) result.getObject(0);
                long seq = (long) result.getObject(1);
                long number = (long) result.getObject(2);

                assertEquals(expectedDocIDs[n - 1], docID);
                assertEquals(expectedSeqs[n - 1], seq);
                assertEquals(expectedNumbers[n - 1], number);
            }
        }, true);
        assertEquals(5, numRows);
    }

    @Test
    public void testLimit() throws Exception {
        loadNumbers(10);

        DataSource dataSource = database(this.db);

        Expression propNumber1 = Expression.property("number1");
        SelectResult srNumber1 = SelectResult.expression(propNumber1);
        Query q = Query
                .select(srNumber1)
                .from(dataSource)
                .orderBy(Ordering.expression(propNumber1))
                .limit(5);

        final long[] expectedNumbers = {1, 2, 3, 4, 5};
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                long number = (long) result.getObject(0);
                assertEquals(expectedNumbers[n - 1], number);
            }
        }, true);
        assertEquals(5, numRows);

        Expression paramExpr = Expression.parameter("LIMIT_NUM");
        q = Query
                .select(srNumber1)
                .from(dataSource)
                .orderBy(Ordering.expression(propNumber1))
                .limit(paramExpr);
        q.parameters().setObject("LIMIT_NUM", 3);

        final long[] expectedNumbers2 = {1, 2, 3};
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                long number = (long) result.getObject(0);
                assertEquals(expectedNumbers2[n - 1], number);
            }
        }, true);
        assertEquals(3, numRows);
    }

    @Test
    public void testLimitOffset() throws Exception {
        loadNumbers(10);

        DataSource dataSource = database(this.db);

        Expression propNumber1 = Expression.property("number1");
        SelectResult srNumber1 = SelectResult.expression(propNumber1);
        Query q = Query
                .select(srNumber1)
                .from(dataSource)
                .orderBy(Ordering.expression(propNumber1))
                .limit(5, 3);

        final long[] expectedNumbers = {4, 5, 6, 7, 8};
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                long number = (long) result.getObject(0);
                assertEquals(expectedNumbers[n - 1], number);
            }
        }, true);
        assertEquals(5, numRows);

        Expression paramLimitExpr = Expression.parameter("LIMIT_NUM");
        Expression paramOffsetExpr = Expression.parameter("OFFSET_NUM");
        q = Query
                .select(srNumber1)
                .from(dataSource)
                .orderBy(Ordering.expression(propNumber1))
                .limit(paramLimitExpr, paramOffsetExpr);
        q.parameters().setObject("LIMIT_NUM", 3).setObject("OFFSET_NUM", 5);

        final long[] expectedNumbers2 = {6, 7, 8};
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                long number = (long) result.getObject(0);
                assertEquals(expectedNumbers2[n - 1], number);
            }
        }, true);
        assertEquals(3, numRows);
    }

    @Test
    public void testQueryResult() throws Exception {
        loadJSONResource("names_100.json");

        Expression FNAME = Expression.property("name.first");
        Expression LNAME = Expression.property("name.last");
        Expression GENDER = Expression.property("gender");
        Expression CITY = Expression.property("contact.address.city");

        SelectResult RES_FNAME = SelectResult.expression(FNAME).as("firstname");
        SelectResult RES_LNAME = SelectResult.expression(LNAME).as("lastname");
        SelectResult RES_GENDER = SelectResult.expression(GENDER);
        SelectResult RES_CITY = SelectResult.expression(CITY);

        DataSource DS = database(db);

        Query q = Query
                .select(RES_FNAME, RES_LNAME, RES_GENDER, RES_CITY)
                .from(DS);

        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertEquals(4, result.count());
                assertEquals(result.getObject(0), result.getObject("firstname"));
                assertEquals(result.getObject(1), result.getObject("lastname"));
                assertEquals(result.getObject(2), result.getObject("gender"));
                assertEquals(result.getObject(3), result.getObject("city"));
            }
        }, true);
        assertEquals(100, numRows);
    }

    @Test
    public void testQueryProjectingKeys() throws Exception {
        loadNumbers(100);

        DataSource DS = database(db);

        Expression NUM1 = Expression.property("number1");

        Expression AVG = Function.avg(NUM1);
        Expression CNT = Function.count(NUM1);
        Expression MIN = Function.min(NUM1);
        Expression MAX = Function.max(NUM1);
        Expression SUM = Function.sum(NUM1);

        SelectResult RES_AVG = SelectResult.expression(AVG);
        SelectResult RES_CNT = SelectResult.expression(CNT);
        SelectResult RES_MIN = SelectResult.expression(MIN).as("min");
        SelectResult RES_MAX = SelectResult.expression(MAX);
        SelectResult RES_SUM = SelectResult.expression(SUM).as("sum");

        Query q = Query
                .select(RES_AVG, RES_CNT, RES_MIN, RES_MAX, RES_SUM)
                .from(DS);

        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertEquals(5, result.count());
                assertEquals(result.getObject(0), result.getObject("$1"));
                assertEquals(result.getObject(1), result.getObject("$2"));
                assertEquals(result.getObject(2), result.getObject("min"));
                assertEquals(result.getObject(3), result.getObject("$3"));
                assertEquals(result.getObject(4), result.getObject("sum"));
            }
        }, true);
        assertEquals(1, numRows);
    }

    @Test
    public void testAggregateFunctions() throws Exception {
        loadNumbers(100);

        DataSource ds = database(this.db);
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
            public void check(int n, Result result) throws Exception {
                assertEquals(50.5F, (float) result.getObject(0), 0.0F);
                assertEquals(100L, (long) result.getObject(1));
                assertEquals(1L, (long) result.getObject(2));
                assertEquals(100L, (long) result.getObject(3));
                assertEquals(5050L, (long) result.getObject(4));
            }
        }, true);
        assertEquals(1, numRows);
    }

    @Test
    public void testArrayFunctions() throws Exception {
        Document doc = createDocument("doc1");
        Array array = new Array();
        array.addObject("650-123-0001");
        array.addObject("650-123-0002");
        doc.setObject("array", array);
        save(doc);

        DataSource ds = database(db);
        Expression exprArray = Expression.property("array");
        Expression exprArrayLength = Function.arrayLength(exprArray);
        SelectResult srArrayLength = SelectResult.expression(exprArrayLength);
        Query q = Query.select(srArrayLength).from(ds);
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertEquals(2, result.getInt(0));
            }
        }, true);
        assertEquals(1, numRows);

        Expression exArrayContains1 = Function.arrayContains(exprArray, "650-123-0001");
        Expression exArrayContains2 = Function.arrayContains(exprArray, "650-123-0003");
        SelectResult srArrayContains1 = SelectResult.expression(exArrayContains1);
        SelectResult srArrayContains2 = SelectResult.expression(exArrayContains2);

        q = Query.select(srArrayContains1, srArrayContains2).from(ds);
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertEquals(true, result.getBoolean(0));
                assertEquals(false, result.getBoolean(1));
            }
        }, true);
        assertEquals(1, numRows);
    }

    @Test
    public void testMathFunctions() throws Exception {
        double num = 0.6;

        Document doc = createDocument("doc1");
        doc.setObject("number", num);
        save(doc);

        final Double[] expectedValues = {
                Math.abs(num),
                Math.acos(num),
                Math.asin(num),
                Math.atan(num),
                Math.atan2(num, 90.0), // NOTE: Math.atan2(double y, double x);
                Math.ceil(num),
                Math.cos(num),
                num * 180.0 / Math.PI,
                Math.exp(num),
                Math.floor(num),
                Math.log(num),
                Math.log10(num),
                Math.pow(num, 2),
                num * Math.PI / 180.0,
                (double) Math.round(num),
                Math.round(num * 10.0) / 10.0,
                (double) 1,
                Math.sin(num),
                Math.sqrt(num),
                Math.tan(num),
                (double) 0,
                0.6
        };

        Expression p = Expression.property("number");
        List<Function> functions = Arrays.asList(
                Function.abs(p),
                Function.acos(p),
                Function.asin(p),
                Function.atan(p),
                Function.atan2(p, 90), // NOTE: Function atan2(Object y, Object x)
                Function.ceil(p),
                Function.cos(p),
                Function.degrees(p),
                Function.exp(p),
                Function.floor(p),
                Function.ln(p),
                Function.log(p),
                Function.power(p, 2),
                Function.radians(p),
                Function.round(p),
                Function.round(p, 1),
                Function.sign(p),
                Function.sin(p),
                Function.sqrt(p),
                Function.tan(p),
                Function.trunc(p),
                Function.trunc(p, 1)
        );
        final AtomicInteger index = new AtomicInteger(0);
        for (Function f : functions) {
            Log.e(TAG, "index -> " + index.intValue());
            Query q = Query.select(SelectResult.expression(f))
                    .from(database(db));
            int numRows = verifyQuery(q, new QueryResult() {
                @Override
                public void check(int n, Result result) throws Exception {
                    double expected = expectedValues[index.intValue()];
                    assertEquals(expected, result.getDouble(0), 0.0);
                }
            }, true);
            assertEquals(1, numRows);
            index.incrementAndGet();
        }
    }

    @Test
    public void testStringFunctions() throws Exception {
        final String str = "  See you 18r  ";
        Document doc = createDocument("doc1");
        doc.setObject("greeting", str);
        save(doc);

        DataSource ds = DataSource.database(db);

        Expression prop = Expression.property("greeting");

        // Contains:
        Function fnContains1 = Function.contains(prop, "8");
        Function fnContains2 = Function.contains(prop, "9");
        SelectResult srFnContains1 = SelectResult.expression(fnContains1);
        SelectResult srFnContains2 = SelectResult.expression(fnContains2);

        Query q = Query.select(srFnContains1, srFnContains2).from(ds);
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertTrue(result.getBoolean(0));
                assertFalse(result.getBoolean(1));
            }
        }, true);
        assertEquals(1, numRows);

        // Length
        Function fnLength = Function.length(prop);
        q = Query.select(SelectResult.expression(fnLength)).from(ds);
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertEquals(str.length(), result.getInt(0));
            }
        }, true);
        assertEquals(1, numRows);

        // Lower, Ltrim, Rtrim, Trim, Upper:
        Function fnLower = Function.lower(prop);
        Function fnLTrim = Function.ltrim(prop);
        Function fnRTrim = Function.rtrim(prop);
        Function fnTrim = Function.trim(prop);
        Function fnUpper = Function.upper(prop);

        q = Query.select(
                SelectResult.expression(fnLower),
                SelectResult.expression(fnLTrim),
                SelectResult.expression(fnRTrim),
                SelectResult.expression(fnTrim),
                SelectResult.expression(fnUpper))
                .from(ds);
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertEquals(str.toLowerCase(), result.getString(0));
                assertEquals(str.replaceAll("^\\s+", ""), result.getString(1));
                assertEquals(str.replaceAll("\\s+$", ""), result.getString(2));
                assertEquals(str.trim(), result.getString(3));
                assertEquals(str.toUpperCase(), result.getString(4));
            }
        }, true);
        assertEquals(1, numRows);
    }

    @Test
    public void testTypeFunctions() throws Exception {
        Document doc = createDocument("doc1");
        doc.setObject("array", new Array(Arrays.<Object>asList("a", "b")));
        doc.setObject("dictionary", new Dictionary(new HashMap<String, Object>() {{
            put("foo", "bar");
        }}));
        doc.setObject("number", 3.14);
        doc.setObject("string", "string");
        save(doc);

        Expression exprArray = Expression.property("array");
        Expression exprDict = Expression.property("dictionary");
        Expression exprNum = Expression.property("number");
        Expression exprStr = Expression.property("string");

        Function fnIsArray = Function.isArray(exprArray);
        Function fnIsDict = Function.isDictionary(exprDict);
        Function fnIsNum = Function.isNumber(exprNum);
        Function fnIsStr = Function.isString(exprStr);

        Query q = Query.select(
                SelectResult.expression(fnIsArray),
                SelectResult.expression(fnIsDict),
                SelectResult.expression(fnIsNum),
                SelectResult.expression(fnIsStr))
                .from(DataSource.database(db));
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertTrue(result.getBoolean(0));
                assertTrue(result.getBoolean(1));
                assertTrue(result.getBoolean(2));
                assertTrue(result.getBoolean(3));
            }
        }, true);
        assertEquals(1, numRows);
    }

    @Test
    public void testLiveQuery() throws Exception {
        loadNumbers(100);

        LiveQuery query = Query
                .select()
                .from(database(db))
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
                    Result result;
                    int count = 0;
                    while ((result = rs.next()) != null) {
                        if (count == 0)
                            assertEquals(-1L, result.getDocument().getObject("number1"));
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
                            try {
                                createDocNumbered(-1, 100);
                            } catch (CouchbaseLiteException e) {
                                throw new RuntimeException(e);
                            }
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
                .from(database(db))
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
                            try {
                                createDocNumbered(111, 100);
                            } catch (CouchbaseLiteException e) {
                                throw new RuntimeException(e);
                            }
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
