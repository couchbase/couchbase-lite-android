//
// C4QueryTest.java
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
package com.couchbase.litecore;

import com.couchbase.litecore.fleece.FLArrayIterator;
import com.couchbase.litecore.fleece.FLDict;
import com.couchbase.litecore.fleece.FLValue;

import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.couchbase.litecore.C4Constants.C4ErrorDomain.LiteCoreDomain;
import static com.couchbase.litecore.C4Constants.C4IndexType.kC4FullTextIndex;
import static com.couchbase.litecore.C4Constants.C4IndexType.kC4ValueIndex;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorInvalidParameter;
import static com.couchbase.litecore.C4Constants.LiteCoreError.kC4ErrorInvalidQuery;
import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLDict;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class C4QueryTest extends C4QueryBaseTest {
    //-------------------------------------------------------------------------
    // public methods
    //-------------------------------------------------------------------------

    @Override
    public void setUp() throws Exception {
        super.setUp();
        importJSONLines("names_100.json");
    }

    @Override
    public void tearDown() throws Exception {
        if (query != null) {
            query.free();
            query = null;
        }
        super.tearDown();
    }

    //-------------------------------------------------------------------------
    // tests
    //-------------------------------------------------------------------------

    // -- Query parser error messages
    @Test
    public void testDatabaseErrorMessages() {
        try {
            db.createQuery("[\"=\"]");
            fail();
        } catch (LiteCoreException e) {
            assertEquals(LiteCoreDomain, e.domain);
            assertEquals(kC4ErrorInvalidQuery, e.code);
        }
    }

    // - DB Query
    @Test
    public void testDBQuery() throws LiteCoreException {
        compile(json5("['=', ['.', 'contact', 'address', 'state'], 'CA']"));
        assertEquals(Arrays.asList("0000001", "0000015", "0000036", "0000043", "0000053", "0000064", "0000072", "0000073"), run());

        compile(json5("['=', ['.', 'contact', 'address', 'state'], 'CA']"), "", true);
        Map<String, Object> params = new HashMap<>();
        params.put("offset", 1);
        params.put("limit", 8);
        assertEquals(Arrays.asList("0000015", "0000036", "0000043", "0000053", "0000064", "0000072", "0000073"), run(params));

        params = new HashMap<>();
        params.put("offset", 1);
        params.put("limit", 4);
        assertEquals(Arrays.asList("0000015", "0000036", "0000043", "0000053"), run(params));

        compile(json5("['AND', ['=', ['array_count()', ['.', 'contact', 'phone']], 2],['=', ['.', 'gender'], 'male']]"));
        assertEquals(Arrays.asList("0000002", "0000014", "0000017", "0000027", "0000031", "0000033", "0000038", "0000039", "0000045", "0000047",
                "0000049", "0000056", "0000063", "0000065", "0000075", "0000082", "0000089", "0000094", "0000097"), run());

        // MISSING means no value is present (at that array index or dict key)
        compile(json5("['IS', ['.', 'contact', 'phone', [0]], ['MISSING']]"), "", true);
        params = new HashMap<>();
        params.put("offset", 0);
        params.put("limit", 4);
        assertEquals(Arrays.asList("0000004", "0000006", "0000008", "0000015"), run(params));

        // ...wherease null is a JSON null value
        compile(json5("['IS', ['.', 'contact', 'phone', [0]], null]"), "", true);
        params = new HashMap<>();
        params.put("offset", 0);
        params.put("limit", 4);
        assertEquals(Arrays.asList(), run(params));
    }

    // - DB Query LIKE
    @Test
    public void testDBQueryLIKE() throws LiteCoreException {
        compile(json5("['LIKE', ['.name.first'], '%j%']"));
        assertEquals(Arrays.asList("0000085"), run());

        compile(json5("['LIKE', ['.name.first'], '%J%']"));
        assertEquals(Arrays.asList("0000002", "0000004", "0000008", "0000017", "0000028", "0000030", "0000045", "0000052", "0000067", "0000071", "0000088", "0000094"), run());

        compile(json5("['LIKE', ['.name.first'], 'Jen%']"));
        assertEquals(Arrays.asList("0000008", "0000028"), run());
    }

    // - DB Query IN
    @Test
    public void testDBQueryIN() throws LiteCoreException {
        // Type 1: RHS is an expression; generates a call to array_contains
        compile(json5("['IN', 'reading', ['.', 'likes']]"));
        assertEquals(Arrays.asList("0000004", "0000056", "0000064", "0000079", "0000099"), run());

        // Type 2: RHS is an array literal; generates a SQL "IN" expression
        compile(json5("['IN', ['.', 'name', 'first'], ['[]', 'Eddie', 'Verna']]"));
        assertEquals(Arrays.asList("0000091", "0000093"), run());
    }

    // - DB Query sorted
    @Test
    public void testDBQuerySorted() throws LiteCoreException {
        compile(json5("['=', ['.', 'contact', 'address', 'state'], 'CA']"),
                json5("[['.', 'name', 'last']]"));
        assertEquals(Arrays.asList("0000015", "0000036", "0000072", "0000043", "0000001", "0000064", "0000073", "0000053"), run());
    }

    // - DB Query bindings
    @Test
    public void testDBQueryBindings() throws LiteCoreException {
        compile(json5("['=', ['.', 'contact', 'address', 'state'], ['$', 1]]"));
        Map<String, Object> params = new HashMap<>();
        params.put("1", "CA");
        assertEquals(Arrays.asList("0000001", "0000015", "0000036", "0000043", "0000053", "0000064", "0000072", "0000073"), run(params));

        compile(json5("['=', ['.', 'contact', 'address', 'state'], ['$', 'state']]"));
        params = new HashMap<>();
        params.put("state", "CA");
        assertEquals(Arrays.asList("0000001", "0000015", "0000036", "0000043", "0000053", "0000064", "0000072", "0000073"), run(params));
    }

    // - DB Query ANY
    @Test
    public void testDBQueryANY() throws LiteCoreException {
        compile(json5("['ANY', 'like', ['.', 'likes'], ['=', ['?', 'like'], 'climbing']]"));
        assertEquals(Arrays.asList("0000017", "0000021", "0000023", "0000045", "0000060"), run());

        // This EVERY query has lots of results because every empty `likes` array matches it
        compile(json5("['EVERY', 'like', ['.', 'likes'], ['=', ['?', 'like'], 'taxes']]"));
        List<String> result = run();
        assertEquals(42, result.size());
        assertEquals("0000007", result.get(0));

        // Changing the op to ANY AND EVERY returns no results
        compile(json5("['ANY AND EVERY', 'like', ['.', 'likes'], ['=', ['?', 'like'], 'taxes']]"));
        assertEquals(Arrays.asList(), run());

        // Look for people where every like contains an L:
        compile(json5("['ANY AND EVERY', 'like', ['.', 'likes'], ['LIKE', ['?', 'like'], '%l%']]"));
        assertEquals(Arrays.asList("0000017", "0000027", "0000060", "0000068"), run());
    }

    // - DB Query ANY w/paths
    // NOTE: in C4PathsQueryTest.java

    // - DB Query ANY of dict
    @Test
    public void testDBQueryANYofDict() throws LiteCoreException {
        compile(json5("['ANY', 'n', ['.', 'name'], ['=', ['?', 'n'], 'Arturo']]"));
        assertEquals(Arrays.asList("0000090"), run());

        compile(json5("['ANY', 'n', ['.', 'name'], ['contains()', ['?', 'n'], 'V']]"));
        assertEquals(Arrays.asList("0000044", "0000048", "0000053", "0000093"), run());
    }

    // - DB Query expression index
    @Test
    public void testDBQueryExpressionIndex() throws LiteCoreException {
        db.createIndex("length", json5("[['length()', ['.name.first']]]"), kC4ValueIndex, null, true);
        compile(json5("['=', ['length()', ['.name.first']], 9]"));
        assertEquals(Arrays.asList("0000015", "0000099"), run());
    }

    // - Delete indexed doc
    @Test
    public void testDeleteIndexedDoc() throws LiteCoreException {
        // Create the same index as the above test:
        db.createIndex("length", json5("[['length()', ['.name.first']]]"), kC4ValueIndex, null, true);

        // Delete doc "0000015":
        {
            boolean commit = false;
            db.beginTransaction();
            try {
                C4Document doc = db.get("0000015", true);
                assertNotNull(doc);
                String[] history = {doc.getRevID()};
                C4Document updatedDoc = db.put((byte[]) null, doc.getDocID(),
                        C4RevisionFlags.kRevDeleted, false,
                        false, history, true, 0, 0);
                assertNotNull(updatedDoc);
                doc.free();
                updatedDoc.free();
                commit = true;
            } finally {
                db.endTransaction(commit);
            }

        }

        // Now run a query that would have returned the deleted doc, if it weren't deleted:
        compile(json5("['=', ['length()', ['.name.first']], 9]"));
        assertEquals(Arrays.asList("0000099"), run());
    }

    // - Missing columns
    @Test
    public void testMissingColumns() throws LiteCoreException {
        {
            compileSelect(json5("['SELECT', {'WHAT': [['.name'], ['.gender']], 'LIMIT': 1}]"));
            C4QueryEnumerator e = query.run(new C4QueryOptions(), null);
            while (e.next())
                assertEquals(0x00, e.getMissingColumns());
            e.free();
        }

        {
            compileSelect(json5("['SELECT', {'WHAT': [['.XX'], ['.name'], ['.YY'], ['.gender'], ['.ZZ']], 'LIMIT': 1}]"));
            C4QueryEnumerator e = query.run(new C4QueryOptions(), null);
            while (e.next())
                assertEquals(0x15, e.getMissingColumns());
            e.free();
        }
    }

    // ----- FTS:

    // - Full-text query
    @Test
    public void testFullTextQuery() throws LiteCoreException {
        db.createIndex("byStreet", "[[\".contact.address.street\"]]", kC4FullTextIndex, null, true);
        compile(json5("['MATCH', 'byStreet', 'Hwy']"));
        assertEquals(Arrays.asList(
                Arrays.asList(Arrays.asList(13L, 0L, 0L, 10L, 3L)),
                Arrays.asList(Arrays.asList(15L, 0L, 0L, 11L, 3L)),
                Arrays.asList(Arrays.asList(43L, 0L, 0L, 12L, 3L)),
                Arrays.asList(Arrays.asList(44L, 0L, 0L, 12L, 3L)),
                Arrays.asList(Arrays.asList(52L, 0L, 0L, 11L, 3L))
        ), runFTS());

    }

    // - Full-text multiple properties
    @Test
    public void testFullTextMultipleProperties() throws LiteCoreException {
        db.createIndex("byAddress", "[[\".contact.address.street\"], [\".contact.address.city\"], [\".contact.address.state\"]]", kC4FullTextIndex, null, true);

        // Some docs match 'Santa' in the street name, some in the city name
        compile(json5("['MATCH', 'byAddress', 'Santa']"));
        assertEquals(Arrays.asList(
                Arrays.asList(Arrays.asList(15L, 1L, 0L, 0L, 5L)),
                Arrays.asList(Arrays.asList(44L, 0L, 0L, 3L, 5L)),
                Arrays.asList(Arrays.asList(68L, 0L, 0L, 3L, 5L)),
                Arrays.asList(Arrays.asList(72L, 1L, 0L, 0L, 5L))
        ), runFTS());

        // Search only the street name:
        compile(json5("['MATCH', 'byAddress', 'contact.address.street:Santa']"));
        assertEquals(Arrays.asList(
                Arrays.asList(Arrays.asList(44L, 0L, 0L, 3L, 5L)),
                Arrays.asList(Arrays.asList(68L, 0L, 0L, 3L, 5L))
        ), runFTS());

        // Search for 'Santa' in the street name, and 'Saint' in either:
        compile(json5("['MATCH', 'byAddress', 'contact.address.street:Santa Saint']"));
        assertEquals(
                Arrays.asList(
                        Arrays.asList(
                                Arrays.asList(68L, 0L, 0L, 3L, 5L),
                                Arrays.asList(68L, 1L, 1L, 0L, 5L))
                ), runFTS());

        // Search for 'Santa' in the street name, _or_ 'Saint' in either:
        compile(json5("['MATCH', 'byAddress', 'contact.address.street:Santa OR Saint']"));
        assertEquals(Arrays.asList(
                Arrays.asList(Arrays.asList(20L, 1l, 1L, 0L, 5L)),
                Arrays.asList(Arrays.asList(44L, 0L, 0L, 3L, 5L)),
                Arrays.asList(
                        Arrays.asList(68L, 0L, 0L, 3L, 5L),
                        Arrays.asList(68L, 1L, 1L, 0L, 5L)),
                Arrays.asList(Arrays.asList(77L, 1L, 1L, 0L, 5L))
        ), runFTS());
    }


    // - Multiple Full-text indexes
    @Test
    public void testMultipleFullTextIndexes() throws LiteCoreException {
        db.createIndex("byStreet", "[[\".contact.address.street\"]]", kC4FullTextIndex, null, true);
        db.createIndex("byCity", "[[\".contact.address.city\"]]", kC4FullTextIndex, null, true);
        compile(json5("['AND', ['MATCH', 'byStreet', 'Hwy'], ['MATCH', 'byCity',   'Santa']]"));
        assertEquals(Arrays.asList("0000015"), run());
        assertEquals(Arrays.asList(
                Arrays.asList(Arrays.asList(15L, 0L, 0L, 11L, 3L))
        ), runFTS());
    }

    // - Full-text query in multiple ANDs
    @Test
    public void testFullTextQueryInMultipleANDs() throws LiteCoreException {
        db.createIndex("byStreet", "[[\".contact.address.street\"]]", kC4FullTextIndex, null, true);
        db.createIndex("byCity", "[[\".contact.address.city\"]]", kC4FullTextIndex, null, true);
        compile(json5("['AND', ['AND', ['=', ['.gender'], 'male'], ['MATCH', 'byCity', 'Santa']], ['=', ['.name.first'], 'Cleveland']]"));
        assertEquals(Arrays.asList("0000015"), run());
        assertEquals(Arrays.asList(
                Arrays.asList(Arrays.asList(15L, 0L, 0L, 0L, 5L))
        ), runFTS());
    }

    // - Multiple Full-text queries
    @Test
    public void testMultipleFullTextQueries() throws LiteCoreException {
        // You can't query the same FTS index multiple times in a query (says SQLite)
        db.createIndex("byStreet", "[[\".contact.address.street\"]]", kC4FullTextIndex, null, true);
        try {
            C4Query query = db.createQuery(json5("['AND', ['MATCH', 'byStreet', 'Hwy'], ['MATCH', 'byStreet', 'Blvd']]"));
        } catch (LiteCoreException e) {
            assertEquals(LiteCoreDomain, e.domain);
            assertEquals(kC4ErrorInvalidQuery, e.code);
        }
    }

    // - Buried Full-text queries
    @Test
    public void testBuriedFullTextQueries() throws LiteCoreException {
        // You can't put an FTS match inside an expression other than a top-level AND (says SQLite)
        db.createIndex("byStreet", "[[\".contact.address.street\"]]", kC4FullTextIndex, null, true);
        try {
            C4Query query = db.createQuery(json5("['OR', ['MATCH', 'byStreet', 'Hwy'], ['=', ['.', 'contact', 'address', 'state'], 'CA']]"));
        } catch (LiteCoreException e) {
            assertEquals(LiteCoreDomain, e.domain);
            assertEquals(kC4ErrorInvalidQuery, e.code);
        }
    }

    // - WHAT, JOIN, etc:

    // - DB Query WHAT
    @Test
    public void testDBQueryWHAT() throws LiteCoreException {
        List<String> expectedFirst = Arrays.asList("Cleveland", "Georgetta", "Margaretta");
        List<String> expectedLast = Arrays.asList("Bejcek", "Kolding", "Ogwynn");
        compileSelect(json5("{WHAT: ['.name.first', '.name.last'], WHERE: ['>=', ['length()', ['.name.first']], 9],ORDER_BY: [['.name.first']]}"));

        assertEquals(2, query.columnCount());
        // TODO: Names currently wrong
        // String name0 = query.nameOfColumn(0);
        // String name1 = query.nameOfColumn(1);

        C4QueryEnumerator e = query.run(new C4QueryOptions(), null);
        assertNotNull(e);
        int i = 0;
        while (e.next()) {
            FLArrayIterator itr = e.getColumns();
            assertEquals(itr.getValue().asString(), expectedFirst.get(i));
            assertTrue(itr.next());
            assertEquals(itr.getValue().asString(), expectedLast.get(i));
            i++;
        }
        e.free();
        assertEquals(3, i);
    }

    // - DB Query WHAT returning object
    @Test
    public void testDBQueryWHATReturningObject() throws LiteCoreException {
        List<String> expectedFirst = Arrays.asList("Cleveland", "Georgetta", "Margaretta");
        List<String> expectedLast = Arrays.asList("Bejcek", "Kolding", "Ogwynn");
        compileSelect(json5("{WHAT: ['.name'], WHERE: ['>=', ['length()', ['.name.first']], 9], ORDER_BY: [['.name.first']]}"));
        assertEquals(1, query.columnCount());

        C4QueryEnumerator e = query.run(new C4QueryOptions(), null);
        assertNotNull(e);
        int i = 0;
        while (e.next()) {
            FLArrayIterator itr = e.getColumns();
            FLValue col = itr.getValueAt(0);
            assertTrue(col.getType() == kFLDict);
            FLDict name = col.asFLDict();
            assertEquals(expectedFirst.get(i), name.get("first").asString());
            assertEquals(expectedLast.get(i), name.get("last").asString());
            i++;
        }
        e.free();
        assertEquals(3, i);
    }

    // - DB Query Aggregate
    @Test
    public void testDBQueryAggregate() throws LiteCoreException {
        compileSelect(json5("{WHAT: [['min()', ['.name.last']], ['max()', ['.name.last']]]}"));

        C4QueryEnumerator e = query.run(new C4QueryOptions(), null);
        assertNotNull(e);
        int i = 0;
        while (e.next()) {
            FLArrayIterator itr = e.getColumns();
            assertEquals(itr.getValue().asString(), "Aerni");
            assertTrue(itr.next());
            assertEquals(itr.getValue().asString(), "Zirk");
            i++;
        }
        e.free();
        assertEquals(1, i);
    }

    // - DB Query Grouped
    @Test
    public void testDBQueryGrouped() throws LiteCoreException {

        final List<String> expectedState = Arrays.asList("AL", "AR", "AZ", "CA");
        final List<String> expectedMin = Arrays.asList("Laidlaw", "Okorududu", "Kinatyan", "Bejcek");
        final List<String> expectedMax = Arrays.asList("Mulneix", "Schmith", "Kinatyan", "Visnic");
        final int expectedRowCount = 42;

        compileSelect(json5("{WHAT: [['.contact.address.state'], ['min()', ['.name.last']], ['max()', ['.name.last']]],GROUP_BY: [['.contact.address.state']]}"));

        C4QueryEnumerator e = query.run(new C4QueryOptions(), null);
        assertNotNull(e);
        int i = 0;
        while (e.next()) {
            FLArrayIterator itr = e.getColumns();
            if (i < expectedState.size()) {
                assertEquals(itr.getValue().asString(), expectedState.get(i));
                assertTrue(itr.next());
                assertEquals(itr.getValue().asString(), expectedMin.get(i));
                assertTrue(itr.next());
                assertEquals(itr.getValue().asString(), expectedMax.get(i));
            }
            i++;
        }
        e.free();
        assertEquals(expectedRowCount, i);
    }

    // - DB Query Join
    @Test
    public void testDBQueryJoin() throws IOException, LiteCoreException {
        importJSONLines("states_titlecase_line.json", "state-");
        List<String> expectedFirst = Arrays.asList("Cleveland", "Georgetta", "Margaretta");
        List<String> expectedState = Arrays.asList("California", "Ohio", "South Dakota");
        compileSelect(json5("{WHAT: ['.person.name.first', '.state.name'], FROM: [{as: 'person'}, {as: 'state', on: ['=', ['.state.abbreviation'], ['.person.contact.address.state']]}],WHERE: ['>=', ['length()', ['.person.name.first']], 9],ORDER_BY: [['.person.name.first']]}"));
        C4QueryEnumerator e = query.run(new C4QueryOptions(), null);
        assertNotNull(e);
        int i = 0;
        while (e.next()) {
            FLArrayIterator itr = e.getColumns();
            if (i < expectedState.size()) {
                assertEquals(itr.getValue().asString(), expectedFirst.get(i));
                assertTrue(itr.next());
                assertEquals(itr.getValue().asString(), expectedState.get(i));
            }
            i++;
        }
        e.free();
        assertEquals(3, i);
    }

    // - DB Query Seek
    @Test
    public void testDBQuerySeek() throws LiteCoreException {
        compile(json5("['=', ['.', 'contact', 'address', 'state'], 'CA']"));
        C4QueryEnumerator e = query.run(new C4QueryOptions(), null);
        assertNotNull(e);
        e.next();
        String docID = e.getColumns().getValueAt(0).asString();
        assertEquals("0000001", docID);
        e.next();
        e.seek(0);
        docID = e.getColumns().getValueAt(0).asString();
        assertEquals("0000001", docID);
        e.seek(7);
        docID = e.getColumns().getValueAt(0).asString();
        assertEquals("0000073", docID);
        try {
            e.seek(100);
        } catch (LiteCoreException ex) {
            assertEquals(kC4ErrorInvalidParameter, ex.code);
            assertEquals(LiteCoreDomain, ex.domain);
        }
        e.free();
    }

    // - DB Query ANY nested
    // NOTE: in C4NestedQueryTest

    // - Query parser error messages
    @Test
    public void testQueryParserErrorMessages() throws LiteCoreException {
        try {
            query = new C4Query(db.getHandle(), "[\"=\"]");
            fail();
        } catch (LiteCoreException ex) {
            assertEquals(kC4ErrorInvalidQuery, ex.code);
            assertEquals(LiteCoreDomain, ex.domain);
        }
    }

    // - Query refresh
    @Test
    public void testQueryRefresh() throws LiteCoreException {
        compile(json5("['=', ['.', 'contact', 'address', 'state'], 'CA']"));
        String explanation = query.explain().substring(0, 129);
        assertEquals("SELECT key, sequence FROM kv_default AS _doc WHERE (fl_value(_doc.body, 'contact.address.state') = 'CA') AND (_doc.flags & 1 = 0)", explanation);
        C4QueryEnumerator e = query.run(new C4QueryOptions(), null);
        assertNotNull(e);
        C4QueryEnumerator refreshed = e.refresh();
        assertNull(refreshed);

        boolean commit = false;
        db.beginTransaction();
        try {

            commit = true;
        } finally {
            db.endTransaction(commit);
        }
        e.free();

    }

    // - Delete index
    @Test
    public void testDeleteIndex() {
        // TODO
    }

    // - COLLATION:

    // - DB Query collated
    @Test
    public void testDBQueryCollated() {
        // TODO
    }

    // - DB Query aggregate collated
    @Test
    public void testDBQueryAggregateCollated() {
        // TODO
    }
}
