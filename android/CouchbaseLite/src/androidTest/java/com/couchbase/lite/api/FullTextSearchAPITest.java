//
// FullTextSearchAPITest.java
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
package com.couchbase.lite.api;


import com.couchbase.lite.BaseTest;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DataSource;
import com.couchbase.lite.Database;
import com.couchbase.lite.Expression;
import com.couchbase.lite.FullTextExpression;
import com.couchbase.lite.FullTextIndexItem;

import com.couchbase.lite.IndexBuilder;
import com.couchbase.lite.Meta;
import com.couchbase.lite.MutableDocument;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryBuilder;
import com.couchbase.lite.Result;
import com.couchbase.lite.ResultSet;
import com.couchbase.lite.SelectResult;
import com.couchbase.lite.internal.support.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class FullTextSearchAPITest extends BaseTest {
    static final String TAG = FullTextSearchAPITest.class.getSimpleName();
    protected final static String DATABASE_NAME = "database";

    Database database;

    void prepareData() throws CouchbaseLiteException {
        // --- code example ---
        String[] tasks = {"buy groceries", "play chess", "book travels", "buy museum tickets"};
        for (String task : tasks) {
            MutableDocument doc = new MutableDocument();
            doc.setString("name", task);
            doc.setString("type", "task");
            database.save(doc);
        }
        // --- code example ---
    }

    void prepareIndex() throws CouchbaseLiteException {
        // # tag::fts-index[]
        database.createIndex("nameFTSIndex", IndexBuilder.fullTextIndex(FullTextIndexItem.property("name")).ignoreAccents(false));
        // # end::fts-index[]
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        database = open(DATABASE_NAME);
        prepareIndex();
        prepareData();
    }

    @After
    public void tearDown() throws Exception {
        if (database != null) {
            database.close();
            database = null;
        }

        // database exist, delete it
        deleteDatabase(DATABASE_NAME);

        super.tearDown();
    }

    @Test
    public void testFTS() throws CouchbaseLiteException {
        // # tag::fts-query[]
        Expression whereClause = FullTextExpression.index("nameFTSIndex").match("buy");
        Query ftsQuery = QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.database(database))
                .where(whereClause);
        ResultSet ftsQueryResult = ftsQuery.execute();
        for (Result result : ftsQueryResult)
            Log.i(TAG, String.format("document properties %s", result.getString(0)));
        // # end::fts-query[]
    }
}
