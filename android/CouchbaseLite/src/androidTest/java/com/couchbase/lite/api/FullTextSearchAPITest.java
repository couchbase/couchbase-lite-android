package com.couchbase.lite.api;


import com.couchbase.lite.BaseTest;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DataSource;
import com.couchbase.lite.Database;
import com.couchbase.lite.Expression;
import com.couchbase.lite.FullTextIndexItem;
import com.couchbase.lite.Index;
import com.couchbase.lite.internal.support.Log;
import com.couchbase.lite.Meta;
import com.couchbase.lite.MutableDocument;
import com.couchbase.lite.Query;
import com.couchbase.lite.Result;
import com.couchbase.lite.ResultSet;
import com.couchbase.lite.SelectResult;
import com.couchbase.lite.FullTextExpression;

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
        // --- code example ---
        database.createIndex("nameFTSIndex", Index.fullTextIndex(FullTextIndexItem.property("name")).ignoreAccents(false));
        // --- code example ---
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
        // --- code example ---
        Expression whereClause = FullTextExpression.index("nameFTSIndex").match("buy");
        Query ftsQuery = Query.select(SelectResult.expression(Meta.id))
                .from(DataSource.database(database))
                .where(whereClause);
        ResultSet ftsQueryResult = ftsQuery.execute();
        for (Result result : ftsQueryResult)
            Log.i(TAG, String.format("document properties %s", result.getString(0)));
        // --- code example ---
    }
}
