//
// ResultTest.java
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

import com.couchbase.lite.internal.utils.DateUtils;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ResultTest extends BaseTest {
    final static String kDocumentTestDate = "2017-01-01T00:00:00.000Z";
    final static String kDocumentTestBlob = "i'm blob";

    final static SelectResult SR_NULL = SelectResult.property("null");
    final static SelectResult SR_TRUE = SelectResult.property("true");
    final static SelectResult SR_FALSE = SelectResult.property("false");
    final static SelectResult SR_STRING = SelectResult.property("string");
    final static SelectResult SR_ZERO = SelectResult.property("zero");
    final static SelectResult SR_ONE = SelectResult.property("one");
    final static SelectResult SR_MINUS_ONE = SelectResult.property("minus_one");
    final static SelectResult SR_ONE_DOT_ONE = SelectResult.property("one_dot_one");
    final static SelectResult SR_DATE = SelectResult.property("date");
    final static SelectResult SR_DICT = SelectResult.property("dict");
    final static SelectResult SR_ARRAY = SelectResult.property("array");
    final static SelectResult SR_BLOB = SelectResult.property("blob");
    final static SelectResult SR_NO_KEY = SelectResult.property("non_existing_key");

    private static Query generateQuery(Database db, String docID) {
        Expression exDocID = Expression.string(docID);
        return QueryBuilder.select(
                SR_NULL,
                SR_TRUE,
                SR_FALSE,
                SR_STRING,
                SR_ZERO,
                SR_ONE,
                SR_MINUS_ONE,
                SR_ONE_DOT_ONE,
                SR_DATE,
                SR_DICT,
                SR_ARRAY,
                SR_BLOB,
                SR_NO_KEY)
                .from(DataSource.database(db))
                .where(Meta.id.equalTo(exDocID));
    }

    private String prepareData(int i) throws CouchbaseLiteException {
        String docID = String.format(Locale.ENGLISH, "doc%d", i);
        MutableDocument mDoc = new MutableDocument(docID);
        if (i % 2 == 1)
            DocumentTest.populateData(mDoc);
        else
            DocumentTest.populateDataByTypedSetter(mDoc);
        save(mDoc);
        return docID;
    }

    @Test
    public void testGetValueByKey() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertNull(r.getValue("null"));
                    assertEquals(1L, r.getValue("true"));
                    assertEquals(0L, r.getValue("false"));
                    assertEquals("string", r.getValue("string"));
                    assertEquals(0L, r.getValue("zero"));
                    assertEquals(1L, r.getValue("one"));
                    assertEquals(-1L, r.getValue("minus_one"));
                    assertEquals(1.1, r.getValue("one_dot_one"));
                    assertEquals(kDocumentTestDate, r.getValue("date"));
                    assertTrue(r.getValue("dict") instanceof Dictionary);
                    assertTrue(r.getValue("array") instanceof Array);
                    assertTrue(r.getValue("blob") instanceof Blob);
                    assertNull(r.getValue("non_existing_key"));

                    thrown.expect(IllegalArgumentException.class);
                    r.getValue(null);

                    assertNull(r.getValue("not_in_query_select"));
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetValue() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertNull(r.getValue(0));
                    assertEquals(1L, r.getValue(1));
                    assertEquals(0L, r.getValue(2));
                    assertEquals("string", r.getValue(3));
                    assertEquals(0L, r.getValue(4));
                    assertEquals(1L, r.getValue(5));
                    assertEquals(-1L, r.getValue(6));
                    assertEquals(1.1, r.getValue(7));
                    assertEquals(kDocumentTestDate, r.getValue(8));
                    assertTrue(r.getValue(9) instanceof Dictionary);
                    assertTrue(r.getValue(10) instanceof Array);
                    assertTrue(r.getValue(11) instanceof Blob);
                    assertNull(r.getValue(12));

                    try {
                        r.getValue(-1);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                    try {
                        r.getValue(100);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetStringByKey() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertNull(r.getString("null"));
                    assertNull(r.getString("true"));
                    assertNull(r.getString("false"));
                    assertEquals("string", r.getString("string"));
                    assertNull(r.getString("zero"));
                    assertNull(r.getString("one"));
                    assertNull(r.getString("minus_one"));
                    assertNull(r.getString("one_dot_one"));
                    assertEquals(kDocumentTestDate, r.getString("date"));
                    assertNull(r.getString("dict"));
                    assertNull(r.getString("array"));
                    assertNull(r.getString("blob"));
                    assertNull(r.getString("non_existing_key"));

                    thrown.expect(IllegalArgumentException.class);
                    r.getString(null);

                    assertNull(r.getString("not_in_query_select"));
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetString() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertNull(r.getString(0));
                    assertNull(r.getString(1));
                    assertNull(r.getString(2));
                    assertEquals("string", r.getString(3));
                    assertNull(r.getString(4));
                    assertNull(r.getString(5));
                    assertNull(r.getString(6));
                    assertNull(r.getString(7));
                    assertEquals(kDocumentTestDate, r.getString(8));
                    assertNull(r.getString(9));
                    assertNull(r.getString(10));
                    assertNull(r.getString(11));
                    assertNull(r.getString(12));

                    try {
                        r.getString(-1);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                    try {
                        r.getString(100);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                }
            });
            assertEquals(1, rows);
        }
    }


    @Test
    public void testGetNumberByKey() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertNull(r.getNumber("null"));
                    assertEquals(1, r.getNumber("true").intValue());
                    assertEquals(0, r.getNumber("false").intValue());
                    assertNull(r.getNumber("string"));
                    assertEquals(0, r.getNumber("zero").intValue());
                    assertEquals(1, r.getNumber("one").intValue());
                    assertEquals(-1, r.getNumber("minus_one").intValue());
                    assertEquals(1.1, r.getNumber("one_dot_one"));
                    assertNull(r.getNumber("date"));
                    assertNull(r.getNumber("dict"));
                    assertNull(r.getNumber("array"));
                    assertNull(r.getNumber("blob"));
                    assertNull(r.getNumber("non_existing_key"));

                    thrown.expect(IllegalArgumentException.class);
                    r.getNumber(null);

                    assertNull(r.getNumber("not_in_query_select"));
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetNumber() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertNull(r.getNumber(0));  // null
                    assertEquals(1, r.getNumber(1).intValue());  // true
                    assertEquals(0, r.getNumber(2).intValue());  // false
                    assertNull(r.getNumber(3));  // string
                    assertEquals(0, r.getNumber(4).intValue());
                    assertEquals(1, r.getNumber(5).intValue());
                    assertEquals(-1, r.getNumber(6).intValue());
                    assertEquals(1.1, r.getNumber(7));
                    assertNull(r.getNumber(8));
                    assertNull(r.getNumber(9));
                    assertNull(r.getNumber(10));
                    assertNull(r.getNumber(11));
                    assertNull(r.getNumber(12));

                    try {
                        r.getNumber(-1);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                    try {
                        r.getNumber(100);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }

                }
            });
            assertEquals(1, rows);
        }
    }


    @Test
    public void testGetIntegerByKey() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertEquals(0, r.getInt("null"));
                    assertEquals(1, r.getInt("true"));
                    assertEquals(0, r.getInt("false"));
                    assertEquals(0, r.getInt("string"));
                    assertEquals(0, r.getInt("zero"));
                    assertEquals(1, r.getInt("one"));
                    assertEquals(-1, r.getInt("minus_one"));
                    assertEquals(1, r.getInt("one_dot_one"));
                    assertEquals(0, r.getInt("date"));
                    assertEquals(0, r.getInt("dict"));
                    assertEquals(0, r.getInt("array"));
                    assertEquals(0, r.getInt("blob"));
                    assertEquals(0, r.getInt("non_existing_key"));

                    thrown.expect(IllegalArgumentException.class);
                    r.getInt(null);

                    assertEquals(0, r.getInt("not_in_query_select"));

                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetInteger() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertEquals(0, r.getInt(0));
                    assertEquals(1, r.getInt(1));
                    assertEquals(0, r.getInt(2));
                    assertEquals(0, r.getInt(3));
                    assertEquals(0, r.getInt(4));
                    assertEquals(1, r.getInt(5));
                    assertEquals(-1, r.getInt(6));
                    assertEquals(1, r.getInt(7));
                    assertEquals(0, r.getInt(8));
                    assertEquals(0, r.getInt(9));
                    assertEquals(0, r.getInt(10));
                    assertEquals(0, r.getInt(11));
                    assertEquals(0, r.getInt(12));

                    try {
                        r.getInt(-1);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                    try {
                        r.getInt(100);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                }
            });
            assertEquals(1, rows);
        }
    }


    @Test
    public void testGetLongByKey() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertEquals(0, r.getLong("null"));
                    assertEquals(1, r.getLong("true"));
                    assertEquals(0, r.getLong("false"));
                    assertEquals(0, r.getLong("string"));
                    assertEquals(0, r.getLong("zero"));
                    assertEquals(1, r.getLong("one"));
                    assertEquals(-1, r.getLong("minus_one"));
                    assertEquals(1, r.getLong("one_dot_one"));
                    assertEquals(0, r.getLong("date"));
                    assertEquals(0, r.getLong("dict"));
                    assertEquals(0, r.getLong("array"));
                    assertEquals(0, r.getLong("blob"));
                    assertEquals(0, r.getLong("non_existing_key"));

                    thrown.expect(IllegalArgumentException.class);
                    r.getLong(null);

                    assertEquals(0, r.getLong("not_in_query_select"));
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetLong() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertEquals(0, r.getLong(0));
                    assertEquals(1, r.getLong(1));
                    assertEquals(0, r.getLong(2));
                    assertEquals(0, r.getLong(3));
                    assertEquals(0, r.getLong(4));
                    assertEquals(1, r.getLong(5));
                    assertEquals(-1, r.getLong(6));
                    assertEquals(1, r.getLong(7));
                    assertEquals(0, r.getLong(8));
                    assertEquals(0, r.getLong(9));
                    assertEquals(0, r.getLong(10));
                    assertEquals(0, r.getLong(11));
                    assertEquals(0, r.getLong(12));

                    try {
                        r.getLong(-1);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                    try {
                        r.getLong(100);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetFloatByKey() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertEquals(0.0f, r.getFloat("null"), 0.0f);
                    assertEquals(1.0f, r.getFloat("true"), 0.0f);
                    assertEquals(0.0f, r.getFloat("false"), 0.0f);
                    assertEquals(0.0f, r.getFloat("string"), 0.0f);
                    assertEquals(0.0f, r.getFloat("zero"), 0.0f);
                    assertEquals(1.0f, r.getFloat("one"), 0.0f);
                    assertEquals(-1.0f, r.getFloat("minus_one"), 0.0f);
                    assertEquals(1.1f, r.getFloat("one_dot_one"), 0.0f);
                    assertEquals(0.0f, r.getFloat("date"), 0.0f);
                    assertEquals(0.0f, r.getFloat("dict"), 0.0f);
                    assertEquals(0.0f, r.getFloat("array"), 0.0f);
                    assertEquals(0.0f, r.getFloat("blob"), 0.0f);
                    assertEquals(0.0f, r.getFloat("non_existing_key"), 0.0f);

                    thrown.expect(IllegalArgumentException.class);
                    r.getFloat(null);

                    assertEquals(0.0f, r.getFloat("not_in_query_select"), 0.0f);
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetFloat() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertEquals(0.0f, r.getFloat(0), 0.0f);
                    assertEquals(1.0f, r.getFloat(1), 0.0f);
                    assertEquals(0.0f, r.getFloat(2), 0.0f);
                    assertEquals(0.0f, r.getFloat(3), 0.0f);
                    assertEquals(0.0f, r.getFloat(4), 0.0f);
                    assertEquals(1.0f, r.getFloat(5), 0.0f);
                    assertEquals(-1.0f, r.getFloat(6), 0.0f);
                    assertEquals(1.1f, r.getFloat(7), 0.0f);
                    assertEquals(0.0f, r.getFloat(8), 0.0f);
                    assertEquals(0.0f, r.getFloat(9), 0.0f);
                    assertEquals(0.0f, r.getFloat(10), 0.0f);
                    assertEquals(0.0f, r.getFloat(11), 0.0f);
                    assertEquals(0.0f, r.getFloat(12), 0.0f);

                    try {
                        r.getFloat(-1);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                    try {
                        r.getFloat(100);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetDoubleByKey() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertEquals(0.0, r.getDouble("null"), 0.0);
                    assertEquals(1.0, r.getDouble("true"), 0.0);
                    assertEquals(0.0, r.getDouble("false"), 0.0);
                    assertEquals(0.0, r.getDouble("string"), 0.0);
                    assertEquals(0.0, r.getDouble("zero"), 0.0);
                    assertEquals(1.0, r.getDouble("one"), 0.0);
                    assertEquals(-1.0, r.getDouble("minus_one"), 0.0);
                    assertEquals(1.1, r.getDouble("one_dot_one"), 0.0);
                    assertEquals(0.0, r.getDouble("date"), 0.0);
                    assertEquals(0.0, r.getDouble("dict"), 0.0);
                    assertEquals(0.0, r.getDouble("array"), 0.0);
                    assertEquals(0.0, r.getDouble("blob"), 0.0);
                    assertEquals(0.0, r.getDouble("non_existing_key"), 0.0);

                    thrown.expect(IllegalArgumentException.class);
                    r.getDouble(null);

                    assertEquals(0.0, r.getDouble("not_in_query_select"), 0.0);
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetDouble() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertEquals(0.0, r.getDouble(0), 0.0);
                    assertEquals(1.0, r.getDouble(1), 0.0);
                    assertEquals(0.0, r.getDouble(2), 0.0);
                    assertEquals(0.0, r.getDouble(3), 0.0);
                    assertEquals(0.0, r.getDouble(4), 0.0);
                    assertEquals(1.0, r.getDouble(5), 0.0);
                    assertEquals(-1.0, r.getDouble(6), 0.0);
                    assertEquals(1.1, r.getDouble(7), 0.0);
                    assertEquals(0.0, r.getDouble(8), 0.0);
                    assertEquals(0.0, r.getDouble(9), 0.0);
                    assertEquals(0.0, r.getDouble(10), 0.0);
                    assertEquals(0.0, r.getDouble(11), 0.0);
                    assertEquals(0.0, r.getDouble(12), 0.0);

                    try {
                        r.getDouble(-1);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                    try {
                        r.getDouble(100);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetBooleanByKey() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertEquals(false, r.getBoolean("null"));
                    assertEquals(true, r.getBoolean("true"));
                    assertEquals(false, r.getBoolean("false"));
                    assertEquals(true, r.getBoolean("string"));
                    assertEquals(false, r.getBoolean("zero"));
                    assertEquals(true, r.getBoolean("one"));
                    assertEquals(true, r.getBoolean("minus_one"));
                    assertEquals(true, r.getBoolean("one_dot_one"));
                    assertEquals(true, r.getBoolean("date"));
                    assertEquals(true, r.getBoolean("dict"));
                    assertEquals(true, r.getBoolean("array"));
                    assertEquals(true, r.getBoolean("blob"));
                    assertEquals(false, r.getBoolean("non_existing_key"));

                    thrown.expect(IllegalArgumentException.class);
                    r.getBoolean(null);

                    assertEquals(false, r.getBoolean("not_in_query_select"));
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetBoolean() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertEquals(false, r.getBoolean(0));
                    assertEquals(true, r.getBoolean(1));
                    assertEquals(false, r.getBoolean(2));
                    assertEquals(true, r.getBoolean(3));
                    assertEquals(false, r.getBoolean(4));
                    assertEquals(true, r.getBoolean(5));
                    assertEquals(true, r.getBoolean(6));
                    assertEquals(true, r.getBoolean(7));
                    assertEquals(true, r.getBoolean(8));
                    assertEquals(true, r.getBoolean(9));
                    assertEquals(true, r.getBoolean(10));
                    assertEquals(true, r.getBoolean(11));
                    assertEquals(false, r.getBoolean(12));

                    try {
                        r.getBoolean(-1);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                    try {
                        r.getBoolean(100);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetDateByKey() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertNull(r.getDate("null"));
                    assertNull(r.getDate("true"));
                    assertNull(r.getDate("false"));
                    assertNull(r.getDate("string"));
                    assertNull(r.getDate("zero"));
                    assertNull(r.getDate("one"));
                    assertNull(r.getDate("minus_one"));
                    assertNull(r.getDate("one_dot_one"));
                    assertEquals(kDocumentTestDate, DateUtils.toJson(r.getDate("date")));
                    assertNull(r.getDate("dict"));
                    assertNull(r.getDate("array"));
                    assertNull(r.getDate("blob"));
                    assertNull(r.getDate("non_existing_key"));

                    thrown.expect(IllegalArgumentException.class);
                    r.getDate(null);

                    assertNull(r.getDate("not_in_query_select"));
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetDate() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertNull(r.getDate(0));
                    assertNull(r.getDate(1));
                    assertNull(r.getDate(2));
                    assertNull(r.getDate(3));
                    assertNull(r.getDate(4));
                    assertNull(r.getDate(5));
                    assertNull(r.getDate(6));
                    assertNull(r.getDate(7));
                    assertEquals(kDocumentTestDate, DateUtils.toJson(r.getDate(8)));
                    assertNull(r.getDate(9));
                    assertNull(r.getDate(10));
                    assertNull(r.getDate(11));
                    assertNull(r.getDate(12));

                    try {
                        r.getDate(-1);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                    try {
                        r.getDate(100);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetBlobByKey() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertNull(r.getBlob("null"));
                    assertNull(r.getBlob("true"));
                    assertNull(r.getBlob("false"));
                    assertNull(r.getBlob("string"));
                    assertNull(r.getBlob("zero"));
                    assertNull(r.getBlob("one"));
                    assertNull(r.getBlob("minus_one"));
                    assertNull(r.getBlob("one_dot_one"));
                    assertNull(r.getBlob("date"));
                    assertNull(r.getBlob("dict"));
                    assertNull(r.getBlob("array"));
                    assertEquals(kDocumentTestBlob, new String(r.getBlob("blob").getContent()));
                    assertTrue(Arrays.equals(kDocumentTestBlob.getBytes(),
                            r.getBlob("blob").getContent()));
                    assertNull(r.getBlob("non_existing_key"));

                    thrown.expect(IllegalArgumentException.class);
                    r.getBlob(null);

                    assertNull(r.getBlob("not_in_query_select"));
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetBlob() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertNull(r.getBlob(0));
                    assertNull(r.getBlob(1));
                    assertNull(r.getBlob(2));
                    assertNull(r.getBlob(3));
                    assertNull(r.getBlob(4));
                    assertNull(r.getBlob(5));
                    assertNull(r.getBlob(6));
                    assertNull(r.getBlob(7));
                    assertNull(r.getBlob(8));
                    assertNull(r.getBlob(9));
                    assertNull(r.getBlob(10));
                    assertEquals(kDocumentTestBlob, new String(r.getBlob(11).getContent()));
                    assertTrue(Arrays.equals(kDocumentTestBlob.getBytes(),
                            r.getBlob(11).getContent()));
                    assertNull(r.getBlob(12));

                    try {
                        r.getBlob(-1);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                    try {
                        r.getBlob(100);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetDictionaryByKey() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertNull(r.getDictionary("null"));
                    assertNull(r.getDictionary("true"));
                    assertNull(r.getDictionary("false"));
                    assertNull(r.getDictionary("string"));
                    assertNull(r.getDictionary("zero"));
                    assertNull(r.getDictionary("one"));
                    assertNull(r.getDictionary("minus_one"));
                    assertNull(r.getDictionary("one_dot_one"));
                    assertNull(r.getDictionary("date"));
                    assertNotNull(r.getDictionary("dict"));
                    Map<String, Object> dict = new HashMap<>();
                    dict.put("street", "1 Main street");
                    dict.put("city", "Mountain View");
                    dict.put("state", "CA");
                    assertEquals(dict, r.getDictionary("dict").toMap());
                    assertNull(r.getDictionary("array"));
                    assertNull(r.getDictionary("blob"));
                    assertNull(r.getDictionary("non_existing_key"));

                    thrown.expect(IllegalArgumentException.class);
                    r.getDictionary(null);

                    assertNull(r.getDictionary("not_in_query_select"));
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetDictionary() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertNull(r.getDictionary(0));
                    assertNull(r.getDictionary(1));
                    assertNull(r.getDictionary(2));
                    assertNull(r.getDictionary(3));
                    assertNull(r.getDictionary(4));
                    assertNull(r.getDictionary(5));
                    assertNull(r.getDictionary(6));
                    assertNull(r.getDictionary(7));
                    assertNull(r.getDictionary(8));
                    assertNotNull(r.getDictionary(9));
                    Map<String, Object> dict = new HashMap<>();
                    dict.put("street", "1 Main street");
                    dict.put("city", "Mountain View");
                    dict.put("state", "CA");
                    assertEquals(dict, r.getDictionary(9).toMap());
                    assertNull(r.getDictionary(10));
                    assertNull(r.getDictionary(11));
                    assertNull(r.getDictionary(12));

                    try {
                        r.getDictionary(-1);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                    try {
                        r.getDictionary(100);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetArrayByKey() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertNull(r.getArray("null"));
                    assertNull(r.getArray("true"));
                    assertNull(r.getArray("false"));
                    assertNull(r.getArray("string"));
                    assertNull(r.getArray("zero"));
                    assertNull(r.getArray("one"));
                    assertNull(r.getArray("minus_one"));
                    assertNull(r.getArray("one_dot_one"));
                    assertNull(r.getArray("date"));
                    assertNull(r.getArray("dict"));
                    assertNotNull(r.getArray("array"));
                    List<Object> list = Arrays.asList((Object) "650-123-0001", (Object) "650-123-0002");
                    assertEquals(list, r.getArray("array").toList());
                    assertNull(r.getArray("blob"));
                    assertNull(r.getArray("non_existing_key"));

                    thrown.expect(IllegalArgumentException.class);
                    r.getArray(null);

                    assertNull(r.getArray("not_in_query_select"));
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetArray() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    assertEquals(13, r.count());

                    assertNull(r.getArray(0));
                    assertNull(r.getArray(1));
                    assertNull(r.getArray(2));
                    assertNull(r.getArray(3));
                    assertNull(r.getArray(4));
                    assertNull(r.getArray(5));
                    assertNull(r.getArray(6));
                    assertNull(r.getArray(7));
                    assertNull(r.getArray(8));
                    assertNull(r.getArray(9));
                    assertNotNull(r.getArray(10));
                    List<Object> list = Arrays.asList((Object) "650-123-0001", (Object) "650-123-0002");
                    assertEquals(list, r.getArray(10).toList());
                    assertNull(r.getArray(11));
                    assertNull(r.getArray(12));

                    try {
                        r.getArray(-1);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                    try {
                        r.getArray(100);
                        fail();
                    } catch (ArrayIndexOutOfBoundsException e) {
                    }
                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testGetKeys() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    List<String> keys = r.getKeys();
                    assertNotNull(keys);
                    assertEquals(13, keys.size());
                    Collections.sort(keys);
                    List<String> expected = Arrays.asList("null", "true", "false", "string", "zero", "one", "minus_one", "one_dot_one", "date", "dict", "array", "blob", "non_existing_key");
                    Collections.sort(expected);
                    assertEquals(expected, keys);

                    // Result.iterator() test
                    Iterator<String> itr = r.iterator();
                    int i = 0;
                    while (itr.hasNext()) {
                        assertTrue(expected.contains(itr.next()));
                        i++;
                    }
                    assertEquals(expected.size(), i);

                }
            });
            assertEquals(1, rows);
        }
    }

    @Test
    public void testContains() throws Exception {
        for (int i = 1; i <= 2; i++) {
            String docID = prepareData(i);

            // run query
            int rows = QueryTest.verifyQuery(generateQuery(db, docID), new QueryTest.QueryResult() {
                @Override
                public void check(int n, Result r) throws Exception {
                    // exists -> true
                    List<String> expected = Arrays.asList(
                            "null", "true", "false", "string", "zero", "one",
                            "minus_one", "one_dot_one", "date", "dict", "array",
                            "blob");
                    for (String key : expected) {
                        assertTrue(r.contains(key));
                    }

                    // not exists -> false
                    assertFalse(r.contains("non_existing_key"));

                    thrown.expect(IllegalArgumentException.class);
                    r.contains(null);

                    assertFalse(r.contains("not_in_query_select"));
                }
            });
            assertEquals(1, rows);
        }
    }
}
