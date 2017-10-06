//
// Copyright (c) 2017 Couchbase, Inc. All rights reserved.
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
package com.couchbase.lite.api;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.couchbase.lite.Array;
import com.couchbase.lite.BaseTest;
import com.couchbase.lite.Collation;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DataSource;
import com.couchbase.lite.Dictionary;
import com.couchbase.lite.Document;
import com.couchbase.lite.Expression;
import com.couchbase.lite.FTSIndexItem;
import com.couchbase.lite.Function;
import com.couchbase.lite.Index;
import com.couchbase.lite.Join;
import com.couchbase.lite.LiveQuery;
import com.couchbase.lite.LiveQueryChange;
import com.couchbase.lite.LiveQueryChangeListener;
import com.couchbase.lite.Log;
import com.couchbase.lite.Ordering;
import com.couchbase.lite.Query;
import com.couchbase.lite.ReadOnlyDictionary;
import com.couchbase.lite.Result;
import com.couchbase.lite.ResultSet;
import com.couchbase.lite.SelectResult;

import org.junit.Before;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class QueryApiTest extends BaseTest {
    public static final String TAG = QueryApiTest.class.getSimpleName();

    private static Expression EXPR_DOCID = Expression.meta().getId();
    private static Expression EXPR_SEQUENCE = Expression.meta().getSequence();
    private static Expression EXPR_NUMBER1 = Expression.property("number1");
    private static Expression EXPR_NUMBER2 = Expression.property("number2");

    private static SelectResult SR_DOCID = SelectResult.expression(EXPR_DOCID);
    private static SelectResult SR_SEQUENCE = SelectResult.expression(EXPR_SEQUENCE);
    private static SelectResult SR_ALL = SelectResult.all();
    private static SelectResult SR_NUMBER1 = SelectResult.expression(EXPR_NUMBER1);

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
            Query q = Query.select(SR_DOCID).from(DataSource.database(db)).where(w);
            int rows = verifyQuery(q, new QueryResult() {
                @Override
                public void check(int n, Result result) throws Exception {
                    String docID = result.getString(0);
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

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testNoWhereQuery() throws Exception {
        loadJSONResource("names_100.json");
        Query q = Query.select(SR_DOCID, SR_SEQUENCE).from(DataSource.database(db));
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                String docID = result.getString(0);
                String expectedID = String.format(Locale.ENGLISH, "doc-%03d", n);
                assertEquals(expectedID, docID);

                int sequence = result.getInt(1);
                assertEquals(n, sequence);

                Document doc = db.getDocument(docID);
                assertEquals(expectedID, doc.getId());
                assertEquals(n, doc.getSequence());
            }
        }, true);
        assertEquals(100, numRows);
    }

    @Test
    public void testWhereComparison() throws Exception {
        Object[][] cases = {
                {EXPR_NUMBER1.lessThan(3), $docids(1, 2)},
                {EXPR_NUMBER1.notLessThan(3), $docids(3, 4, 5, 6, 7, 8, 9, 10)},
                {EXPR_NUMBER1.lessThanOrEqualTo(3), $docids(1, 2, 3)},
                {EXPR_NUMBER1.notLessThanOrEqualTo(3), $docids(4, 5, 6, 7, 8, 9, 10)},
                {EXPR_NUMBER1.greaterThan(6), $docids(7, 8, 9, 10)},
                {EXPR_NUMBER1.notGreaterThan(6), $docids(1, 2, 3, 4, 5, 6)},
                {EXPR_NUMBER1.greaterThanOrEqualTo(6), $docids(6, 7, 8, 9, 10)},
                {EXPR_NUMBER1.notGreaterThanOrEqualTo(6), $docids(1, 2, 3, 4, 5)},
                {EXPR_NUMBER1.equalTo(7), $docids(7)},
                {EXPR_NUMBER1.notEqualTo(7), $docids(1, 2, 3, 4, 5, 6, 8, 9, 10)}
        };
        List<Map<String, Object>> numbers = loadNumbers(10);
        runTestWithNumbers(numbers, cases);
    }

    @Test
    public void testWhereArithmetic() throws Exception {
        Object[][] cases = {
                {EXPR_NUMBER1.multiply(2).greaterThan(3), $docids(2, 3, 4, 5, 6, 7, 8, 9, 10)},
                {EXPR_NUMBER1.divide(2).greaterThan(3), $docids(8, 9, 10)},
                {EXPR_NUMBER1.modulo(2).equalTo(0), $docids(2, 4, 6, 8, 10)},
                {EXPR_NUMBER1.add(5).greaterThan(10), $docids(6, 7, 8, 9, 10)},
                {EXPR_NUMBER1.subtract(5).greaterThan(0), $docids(6, 7, 8, 9, 10)},
                {EXPR_NUMBER1.multiply(EXPR_NUMBER2).greaterThan(10), $docids(2, 3, 4, 5, 6, 7, 8)},
                {EXPR_NUMBER2.divide(EXPR_NUMBER1).greaterThan(3), $docids(1, 2)},
                {EXPR_NUMBER2.modulo(EXPR_NUMBER1).equalTo(0), $docids(1, 2, 5, 10)},
                {EXPR_NUMBER1.add(EXPR_NUMBER2).equalTo(10), $docids(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)},
                {EXPR_NUMBER1.subtract(EXPR_NUMBER2).greaterThan(0), $docids(6, 7, 8, 9, 10)}
        };
        List<Map<String, Object>> numbers = loadNumbers(10);
        runTestWithNumbers(numbers, cases);
    }

    @Test
    public void testWhereAndOr() throws Exception {
        Object[][] cases = {
                {EXPR_NUMBER1.greaterThan(3).and(EXPR_NUMBER2.greaterThan(3)), $docids(4, 5, 6)},
                {EXPR_NUMBER1.lessThan(3).or(EXPR_NUMBER2.lessThan(3)), $docids(1, 2, 8, 9, 10)}
        };
        List<Map<String, Object>> numbers = loadNumbers(10);
        runTestWithNumbers(numbers, cases);
    }

    @Test
    public void testWhereCheckNull() throws Exception {
        // https://github.com/couchbase/couchbase-lite-ios/issues/1670
        Document doc1 = createDocument("doc1");
        doc1.setObject("name", "Scott");
        doc1.setObject("address", null);
        save(doc1);

        Document doc2 = createDocument("doc2");
        doc2.setObject("name", "Tiger");
        doc2.setObject("address", "123 1st ave.");
        doc2.setObject("age", 20);
        save(doc2);

        Expression name = Expression.property("name");
        Expression address = Expression.property("address");
        Expression age = Expression.property("age");
        Expression work = Expression.property("work");

        Object[][] cases = {
                {name.isNullOrMissing(), $docids()},
                {name.notNullOrMissing(), $docids(1, 2)},
                {address.isNullOrMissing(), $docids(1)},
                {address.notNullOrMissing(), $docids(2)},
                {age.isNullOrMissing(), $docids(1)},
                {age.notNullOrMissing(), $docids(2)},
                {work.isNullOrMissing(), $docids(1, 2)},
                {work.notNullOrMissing(), $docids()},
        };

        for (Object[] c : cases) {
            Expression exp = (Expression) c[0];
            final String[] documentIDs = (String[]) c[1];
            Query q = Query.select(SR_DOCID).from(DataSource.database(db)).where(exp);
            int numRows = verifyQuery(q, new QueryResult() {
                @Override
                public void check(int n, Result result) throws Exception {
                    if (n < documentIDs.length) {
                        String docID = documentIDs[(int) n - 1];
                        assertEquals(docID, result.getString(0));
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
                .select(SR_DOCID)
                .from(DataSource.database(db))
                .where(Expression.property("string").is("string"));
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                String docID = result.getString(0);
                assertEquals(doc1.getId(), docID);
                Document doc = db.getDocument(docID);
                assertEquals(doc1.getObject("string"), doc.getObject("string"));
            }
        }, true);
        assertEquals(1, numRows);

        // Test IS NOT:
        q = Query
                .select(SR_DOCID)
                .from(DataSource.database(db))
                .where(Expression.property("string").isNot("string1"));
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                String docID = result.getString(0);
                assertEquals(doc1.getId(), docID);
                Document doc = db.getDocument(docID);
                assertEquals(doc1.getObject("string"), doc.getObject("string"));
            }
        }, true);
        assertEquals(1, numRows);
    }

    @Test
    public void testBetween() throws Exception {
        Object[][] cases = {
                {EXPR_NUMBER1.between(3, 7), $docids(3, 4, 5, 6, 7)}
        };
        List<Map<String, Object>> numbers = loadNumbers(10);
        runTestWithNumbers(numbers, cases);
    }

    @Test
    public void testWhereIn() throws Exception {
        loadJSONResource("names_100.json");

        final Object[] expected = {"Marcy", "Margaretta", "Margrett", "Marlen", "Maryjo"};

        DataSource ds = DataSource.database(db);
        Expression exprFirstName = Expression.property("name.first");
        SelectResult srFirstName = SelectResult.expression(exprFirstName);
        Ordering orderByFirstName = Ordering.property("name.first");
        Query q = Query.select(srFirstName)
                .from(ds)
                .where(exprFirstName.in(expected))
                .orderBy(orderByFirstName);
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                String name = result.getString(0);
                assertEquals(expected[n - 1], name);
                Log.e(TAG, "n -> %d name -> %s", n, name);
            }
        });
        assertEquals(expected.length, numRows);
    }

    @Test
    public void testWhereLike() throws Exception {
        loadJSONResource("names_100.json");

        Expression w = Expression.property("name.first").like("%Mar%");
        Query q = Query
                .select(SR_DOCID)
                .from(DataSource.database(db))
                .where(w)
                .orderBy(Ordering.property("name.first").ascending());

        final List<String> firstNames = new ArrayList<>();
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                String docID = result.getString(0);
                Document doc = db.getDocument(docID);
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
                .select(SR_DOCID)
                .from(DataSource.database(db))
                .where(w)
                .orderBy(Ordering.property("name.first").ascending());

        final List<String> firstNames = new ArrayList<>();
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                Log.v(TAG, "check() n -> " + n);
                String docID = result.getString(0);
                Document doc = db.getDocument(docID);
                Map<String, Object> name = doc.getDictionary("name").toMap();
                if (name != null) {
                    String firstName = (String) name.get("first");
                    if (firstName != null) {
                        firstNames.add(firstName);
                        Log.v(TAG, "firstName -> " + firstName);
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

        Index index = Index.ftsIndex().on(FTSIndexItem.expression(Expression.property("sentence")));
        db.createIndex("sentence", index);

        Expression SENTENCE = Expression.property("sentence");
        SelectResult S_SENTENCE = SelectResult.expression(SENTENCE);

        Expression w = SENTENCE.match("'Dummie woman'");
        Ordering o = Ordering.expression(Function.rank(SENTENCE)).descending();
        Query q = Query.select(SR_DOCID, S_SENTENCE).from(DataSource.database(db)).where(w).orderBy(o);
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                //assertNotNull(result.getString(0));
                //assertNotNull(result.getString(1));
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

            Query q = Query.select(SR_DOCID).from(DataSource.database(db)).orderBy(o);

            final List<String> firstNames = new ArrayList<String>();
            int numRows = verifyQuery(q, new QueryResult() {
                @Override
                public void check(int n, Result result) throws Exception {
                    String docID = result.getString(0);
                    Document doc = db.getDocument(docID);
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

    @Test
    public void testSelectDistinct() throws Exception {
        // https://github.com/couchbase/couchbase-lite-ios/issues/1669
        // https://github.com/couchbase/couchbase-lite-core/issues/81
        final Document doc1 = new Document();
        doc1.setObject("number", 20);
        save(doc1);

        Document doc2 = new Document();
        doc2.setObject("number", 20);
        save(doc2);

        Expression NUMBER = Expression.property("number");
        SelectResult S_NUMBER = SelectResult.expression(NUMBER);
        Query q = Query.selectDistinct(S_NUMBER).from(DataSource.database(db));
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertEquals(20, result.getInt(0));
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

        DataSource mainDS = DataSource.database(this.db).as("main");
        DataSource secondaryDS = DataSource.database(this.db).as("secondary");

        Expression mainPropExpr = Expression.property("number1").from("main");
        Expression secondaryExpr = Expression.property("theone").from("secondary");
        Expression joinExpr = mainPropExpr.equalTo(secondaryExpr);
        Join join = Join.join(secondaryDS).on(joinExpr);

        SelectResult MAIN_DOC_ID = SelectResult.expression(Expression.meta().getId().from("main"));

        Query q = Query.select(MAIN_DOC_ID).from(mainDS).join(join);

        assertNotNull(q);
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                String docID = result.getString(0);
                Document doc = db.getDocument(docID);
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
            public void check(int n, Result result) throws Exception {
                String state = (String) result.getObject(0);
                long count = (long) result.getObject(1);
                String maxZip = (String) result.getObject(2);
                Log.v(TAG, "state=%s, count=%d, maxZip=%s", state, count, maxZip);
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

        DataSource dataSource = DataSource.database(this.db);


        Expression paramN1 = Expression.parameter("num1");
        Expression paramN2 = Expression.parameter("num2");

        Ordering ordering = Ordering.expression(EXPR_NUMBER1);

        Query q = Query
                .select(SR_NUMBER1)
                .from(dataSource)
                .where(EXPR_NUMBER1.between(paramN1, paramN2))
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

        DataSource dataSource = DataSource.database(this.db);

        Query q = Query
                .select(SR_DOCID, SR_SEQUENCE, SR_NUMBER1)
                .from(dataSource)
                .orderBy(Ordering.expression(EXPR_SEQUENCE));

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

        DataSource dataSource = DataSource.database(this.db);

        Query q = Query
                .select(SR_NUMBER1)
                .from(dataSource)
                .orderBy(Ordering.expression(EXPR_NUMBER1))
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
                .select(SR_NUMBER1)
                .from(dataSource)
                .orderBy(Ordering.expression(EXPR_NUMBER1))
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

        DataSource dataSource = DataSource.database(this.db);

        Query q = Query
                .select(SR_NUMBER1)
                .from(dataSource)
                .orderBy(Ordering.expression(EXPR_NUMBER1))
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
                .select(SR_NUMBER1)
                .from(dataSource)
                .orderBy(Ordering.expression(EXPR_NUMBER1))
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

        DataSource DS = DataSource.database(db);

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

        DataSource DS = DataSource.database(db);

        Expression AVG = Function.avg(EXPR_NUMBER1);
        Expression CNT = Function.count(EXPR_NUMBER1);
        Expression MIN = Function.min(EXPR_NUMBER1);
        Expression MAX = Function.max(EXPR_NUMBER1);
        Expression SUM = Function.sum(EXPR_NUMBER1);

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
    public void testQuantifiedOperators() throws Exception {
        loadJSONResource("names_100.json");

        DataSource ds = DataSource.database(db);

        Expression exprLikes = Expression.property("likes");
        Expression exprVarLike = Expression.variable("LIKE");

        // ANY:
        Query q = Query.select(SR_DOCID).from(ds).where(
                Expression.any("LIKE").in(exprLikes).satisfies(exprVarLike.equalTo("climbing")));

        final AtomicInteger i = new AtomicInteger(0);
        final String[] expected = {"doc-017", "doc-021", "doc-023", "doc-045", "doc-060"};
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertEquals(expected[i.getAndIncrement()], result.getString(0));
            }
        }, false);
        assertEquals(expected.length, numRows);

        // EVERY:
        q = Query.select(SR_DOCID).from(ds).where(
                Expression.every("LIKE").in(exprLikes).satisfies(exprVarLike.equalTo("taxes")));
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                if (n == 1)
                    assertEquals("doc-007", result.getString(0));
            }
        }, false);
        assertEquals(42, numRows);

        // ANY AND EVERY:
        q = Query.select(SR_DOCID).from(ds).where(
                Expression.anyAndEvery("LIKE").in(exprLikes).satisfies(exprVarLike.equalTo("taxes")));
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
            }
        }, false);
        assertEquals(0, numRows);
    }

    @Test
    public void testAggregateFunctions() throws Exception {
        loadNumbers(100);

        DataSource ds = DataSource.database(this.db);

        Function avg = Function.avg(EXPR_NUMBER1);
        Function cnt = Function.count(EXPR_NUMBER1);
        Function min = Function.min(EXPR_NUMBER1);
        Function max = Function.max(EXPR_NUMBER1);
        Function sum = Function.sum(EXPR_NUMBER1);

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

        DataSource ds = DataSource.database(db);
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
                Function.atan2(num, 90.0),
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
            Log.v(TAG, "index -> " + index.intValue());
            Query q = Query.select(SelectResult.expression(f))
                    .from(DataSource.database(db));
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
                assertEquals(str.toLowerCase(Locale.ENGLISH), result.getString(0));
                assertEquals(str.replaceAll("^\\s+", ""), result.getString(1));
                assertEquals(str.replaceAll("\\s+$", ""), result.getString(2));
                assertEquals(str.trim(), result.getString(3));
                assertEquals(str.toUpperCase(Locale.ENGLISH), result.getString(4));
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
    public void testSelectAll() throws Exception {
        loadNumbers(100);

        DataSource.As ds = DataSource.database(db);

        Expression exprTestDBNum1 = Expression.property("number1").from("testdb");
        SelectResult srTestDBNum1 = SelectResult.expression(exprTestDBNum1);
        SelectResult srTestDBAll = SelectResult.all().from("testdb");

        Query q;
        int numRows;

        // SELECT *
        q = Query.select(SR_ALL).from(ds);
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertEquals(1, result.count());
                ReadOnlyDictionary a1 = result.getDictionary(0);
                ReadOnlyDictionary a2 = result.getDictionary(db.getName());
                assertEquals(n, a1.getInt("number1"));
                assertEquals(100 - n, a1.getInt("number2"));
                assertEquals(n, a2.getInt("number1"));
                assertEquals(100 - n, a2.getInt("number2"));
            }
        }, true);
        assertEquals(100, numRows);

        // SELECT *, number1
        q = Query.select(SR_ALL, SR_NUMBER1).from(ds);
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertEquals(2, result.count());
                ReadOnlyDictionary a1 = result.getDictionary(0);
                ReadOnlyDictionary a2 = result.getDictionary(db.getName());
                assertEquals(n, a1.getInt("number1"));
                assertEquals(100 - n, a1.getInt("number2"));
                assertEquals(n, a2.getInt("number1"));
                assertEquals(100 - n, a2.getInt("number2"));
                assertEquals(n, result.getInt(1));
                assertEquals(n, result.getInt("number1"));
            }
        }, true);
        assertEquals(100, numRows);

        // SELECT testdb.*
        q = Query.select(srTestDBAll).from(ds.as("testdb"));
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertEquals(1, result.count());
                ReadOnlyDictionary a1 = result.getDictionary(0);
                ReadOnlyDictionary a2 = result.getDictionary("testdb");
                assertEquals(n, a1.getInt("number1"));
                assertEquals(100 - n, a1.getInt("number2"));
                assertEquals(n, a2.getInt("number1"));
                assertEquals(100 - n, a2.getInt("number2"));
            }
        }, true);
        assertEquals(100, numRows);

        // SELECT testdb.*, testdb.number1
        q = Query.select(srTestDBAll, srTestDBNum1).from(ds.as("testdb"));
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertEquals(2, result.count());
                ReadOnlyDictionary a1 = result.getDictionary(0);
                ReadOnlyDictionary a2 = result.getDictionary("testdb");
                assertEquals(n, a1.getInt("number1"));
                assertEquals(100 - n, a1.getInt("number2"));
                assertEquals(n, a2.getInt("number1"));
                assertEquals(100 - n, a2.getInt("number2"));
                assertEquals(n, result.getInt(1));
                assertEquals(n, result.getInt("number1"));
            }
        }, true);
        assertEquals(100, numRows);
    }

    @Test
    public void testUnicodeCollationWithLocale() throws Exception {
        String[] letters = {"B", "A", "Z", "Å"};
        for (String letter : letters) {
            Document doc = createDocument();
            doc.setObject("string", letter);
            save(doc);
        }

        Expression STRING = Expression.property("string");
        SelectResult S_STRING = SelectResult.expression(STRING);

        // Without locale:
        Collation NO_LOCALE = Collation.unicode()
                .locale(null)
                .ignoreCase(false)
                .ignoreAccents(false);

        Query q = Query.select(S_STRING)
                .from(DataSource.database(db))
                .where(null)
                .orderBy(Ordering.expression(STRING.collate(NO_LOCALE)));

        final String[] expected = {"A", "Å", "B", "Z"};
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertEquals(expected[n - 1], result.getString(0));
            }
        }, true);
        assertEquals(expected.length, numRows);

        // Spanish
        {
            // With locale:
            Collation WITH_LOCALE = Collation.unicode()
                    .locale("es")
                    .ignoreCase(false)
                    .ignoreAccents(false);

            q = Query.select(S_STRING)
                    .from(DataSource.database(db))
                    .where(null)
                    .orderBy(Ordering.expression(STRING.collate(WITH_LOCALE)));

            final String[] expected2 = {"A", "Å", "B", "Z"};
            numRows = verifyQuery(q, new QueryResult() {
                @Override
                public void check(int n, Result result) throws Exception {
                    assertEquals(expected2[n - 1], result.getString(0));
                }
            }, true);
            assertEquals(expected2.length, numRows);
        }

        // NOTE: API 16 - 22, ICU does not support Locale "se"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // With locale:
            Collation WITH_LOCALE = Collation.unicode()
                    .locale("se")
                    .ignoreCase(false)
                    .ignoreAccents(false);

            q = Query.select(S_STRING)
                    .from(DataSource.database(db))
                    .where(null)
                    .orderBy(Ordering.expression(STRING.collate(WITH_LOCALE)));

            final String[] expected2 = {"A", "B", "Z", "Å"};
            numRows = verifyQuery(q, new QueryResult() {
                @Override
                public void check(int n, Result result) throws Exception {
                    assertEquals(expected2[n - 1], result.getString(0));
                }
            }, true);
            assertEquals(expected2.length, numRows);
        }
    }

    //TODO - Implement once LiteCore support ICU for Android
    // @Test
    public void testCompareWithUnicodeCollation() {

    }

    @Test
    public void testLiveQuery() throws Exception {
        loadNumbers(100);

        LiveQuery query = Query
                .select(SR_DOCID)
                .from(DataSource.database(db))
                .where(EXPR_NUMBER1.lessThan(10))
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
                        if (count == 0) {
                            Document doc = db.getDocument(result.getString(0));
                            assertEquals(-1L, doc.getObject("number1"));
                        }
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
                .from(DataSource.database(db))
                .where(EXPR_NUMBER1.lessThan(10))
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

    // https://github.com/couchbase/couchbase-lite-android/issues/1356
    @Test
    public void testCountFunctions() throws Exception {
        loadNumbers(100);

        DataSource ds = DataSource.database(this.db);
        Function cnt = Function.count(EXPR_NUMBER1);

        SelectResult rsCnt = SelectResult.expression(cnt);
        Query q = Query.select(rsCnt).from(ds);
        assertNotNull(q);
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                assertEquals(100L, (long) result.getObject(0));
            }
        }, true);
        assertEquals(1, numRows);
    }

    @Test
    public void testJoinWithArrayContains() throws Exception {
        // Data preparation

        // Hotels
        Document hotel1 = new Document("hotel1");
        hotel1.setString("type", "hotel");
        hotel1.setString("name", "Hilton");
        db.save(hotel1);

        Document hotel2 = new Document("hotel2");
        hotel2.setString("type", "hotel");
        hotel2.setString("name", "Sheraton");
        db.save(hotel2);

        Document hotel3 = new Document("hotel2");
        hotel3.setString("type", "hotel");
        hotel3.setString("name", "Marriott");
        db.save(hotel3);

        // Bookmark
        Document bookmark1 = new Document("bookmark1");
        bookmark1.setString("type", "bookmark");
        bookmark1.setString("title", "Bookmark For Hawaii");
        Array hotels1 = new Array();
        hotels1.addString("hotel1");
        hotels1.addString("hotel2");
        bookmark1.setArray("hotels", hotels1);
        db.save(bookmark1);

        Document bookmark2 = new Document("bookmark2");
        bookmark2.setString("type", "bookmark");
        bookmark2.setString("title", "Bookmark for New York");
        Array hotels2 = new Array();
        hotels2.addString("hotel3");
        bookmark2.setArray("hotels", hotels2);
        db.save(bookmark2);

        // Join Query
        DataSource mainDS = DataSource.database(this.db).as("main");
        DataSource secondaryDS = DataSource.database(this.db).as("secondary");

        Expression typeExpr = Expression.property("type").from("main");
        Expression hotelsExpr = Expression.property("hotels").from("main");
        Expression hotelIdExpr = Expression.meta().getId().from("secondary");
        Expression joinExpr = Function.arrayContains(hotelsExpr, hotelIdExpr);
        Join join = Join.join(secondaryDS).on(joinExpr);

        SelectResult srMainAll = SelectResult.all().from("main");
        SelectResult srSecondaryAll = SelectResult.all().from("secondary");
        Query q = Query.select(srMainAll, srSecondaryAll).from(mainDS).join(join).where(typeExpr.equalTo("bookmark"));
        ResultSet rs = q.run();
        for (Result r : rs)
            Log.e(TAG, "RESULT: " + r.toMap());
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1385
    @Test
    public void testQueryDeletedDocument() throws CouchbaseLiteException {

        // STEP 1: Insert two documents
        Document task1 = createTaskDocument("Task 1", false);
        Document task2 = createTaskDocument("Task 2", false);
        assertEquals(2, db.getCount());

        // STEP 2: query documents before deletion
        Query q = Query.select(SR_DOCID, SR_ALL).from(DataSource.database(this.db)).where(Expression.property("type").equalTo("task"));
        ResultSet rs = q.run();
        Result r;

        // Test: ResultSet.next()
        int counter = 0;
        while ((r = rs.next()) != null) {
            Log.i(TAG, "Round 1: [next] Result -> " + r.toMap());
            counter++;
        }
        assertEquals(2, counter);

        // Test: Iterator
        counter = 0;
        for (Result result : rs) {
            Log.i(TAG, "Round 1: [Iterator] Result -> " + result.toMap());
            counter++;
        }
        assertEquals(2, counter);

        // STEP 3: delete task 1
        assertFalse(task1.isDeleted());
        db.delete(task1);
        assertEquals(1, db.getCount());
        assertTrue(task1.isDeleted());

        // STEP 4: query documents again after deletion
        rs = q.run();

        // Test: ResultSet.next()
        counter = 0;
        while ((r = rs.next()) != null) {
            assertEquals(task2.getId(), r.getString(0));
            Log.i(TAG, "Round 2: [next()] Result -> " + r.toMap());
            counter++;
        }
        assertEquals(1, counter);

        // Test: Iterator
        counter = 0;
        for (Result result : rs) {
            assertEquals(task2.getId(), result.getString(0));
            Log.i(TAG, "Round 2: [Iterator] Result -> " + result.toMap());
            counter++;
        }
        assertEquals(1, counter);
    }

    private Document createTaskDocument(String title, boolean complete) throws CouchbaseLiteException {
        Document doc = createDocument();
        doc.setString("type", "task");
        doc.setString("title", title);
        doc.setBoolean("complete", complete);
        return save(doc);
    }

    // https://github.com/couchbase/couchbase-lite-android/issues/1389
    @Test
    public void testQueryWhereBooleanExpresion() throws Exception {
        // STEP 1: Insert two documents
        Document task1 = createTaskDocument("Task 1", false);
        Document task2 = createTaskDocument("Task 2", true);
        Document task3 = createTaskDocument("Task 3", true);
        assertEquals(3, db.getCount());

        Expression exprType = Expression.property("type");
        Expression exprComplete = Expression.property("complete");
        SelectResult srCount = SelectResult.expression(Function.count(1));

        // regular query - true
        Query q = Query.select(SR_ALL)
                .from(DataSource.database(db))
                .where(exprType.equalTo("task").and(exprComplete.equalTo(true)));
        int numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                Log.i(TAG, "res -> " + result.toMap());
                ReadOnlyDictionary dict = result.getDictionary(db.getName());
                assertTrue(dict.getBoolean("complete"));
                assertEquals("task", dict.getString("type"));
                assertTrue(dict.getString("title").startsWith("Task "));
            }
        }, false);
        assertEquals(2, numRows);

        // regular query - false
        q = Query.select(SR_ALL)
                .from(DataSource.database(db))
                .where(exprType.equalTo("task").and(exprComplete.equalTo(false)));
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                Log.i(TAG, "res -> " + result.toMap());
                ReadOnlyDictionary dict = result.getDictionary(db.getName());
                assertFalse(dict.getBoolean("complete"));
                assertEquals("task", dict.getString("type"));
                assertTrue(dict.getString("title").startsWith("Task "));
            }
        }, false);
        assertEquals(1, numRows);

        // aggregation query - true
        q = Query.select(srCount)
                .from(DataSource.database(db))
                .where(exprType.equalTo("task").and(exprComplete.equalTo(true)));
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                Log.i(TAG, "res -> " + result.toMap());
                assertEquals(2, result.getInt(0));
            }
        }, false);
        assertEquals(1, numRows);

        // aggregation query - false
        q = Query.select(srCount)
                .from(DataSource.database(db))
                .where(exprType.equalTo("task").and(exprComplete.equalTo(false)));
        numRows = verifyQuery(q, new QueryResult() {
            @Override
            public void check(int n, Result result) throws Exception {
                Log.i(TAG, "res -> " + result.toMap());
                assertEquals(1, result.getInt(0));
            }
        }, false);
        assertEquals(1, numRows);
    }
}
