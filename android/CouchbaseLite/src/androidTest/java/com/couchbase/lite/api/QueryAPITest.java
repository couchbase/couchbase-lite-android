package com.couchbase.lite.api;

import android.util.Log;

import com.couchbase.lite.ArrayFunction;
import com.couchbase.lite.BaseTest;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DataSource;
import com.couchbase.lite.Database;
import com.couchbase.lite.Dictionary;
import com.couchbase.lite.Expression;
import com.couchbase.lite.Function;
import com.couchbase.lite.Index;
import com.couchbase.lite.Join;
import com.couchbase.lite.Meta;
import com.couchbase.lite.MutableArray;
import com.couchbase.lite.MutableDictionary;
import com.couchbase.lite.MutableDocument;
import com.couchbase.lite.Ordering;
import com.couchbase.lite.Query;
import com.couchbase.lite.Result;
import com.couchbase.lite.ResultSet;
import com.couchbase.lite.SelectResult;
import com.couchbase.lite.ValueIndexItem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * http://docs-build.sc.couchbase.com/mobile/2.0-code-examples/guides/couchbase-lite/native-api/query/index.html?language=java
 */
public class QueryAPITest extends BaseTest {
    protected final static String DATABASE_NAME = "travel-sample";
    Database database;

    void prepareData() throws CouchbaseLiteException {
        // user data
        MutableDocument mdoc1 = new MutableDocument("james");
        mdoc1.setString("name", "James");
        mdoc1.setString("type", "user");
        mdoc1.setBoolean("admin", false);
        database.save(mdoc1);

        MutableDocument mdoc2 = new MutableDocument("mary");
        mdoc2.setString("name", "Mary");
        mdoc2.setString("type", "user");
        mdoc2.setBoolean("admin", true);
        database.save(mdoc2);

        // airport data
        MutableDocument mdoc3 = new MutableDocument("sfo");
        mdoc3.setString("name", "SFO");
        mdoc3.setString("type", "airport");
        mdoc3.setString("country", "usa");
        mdoc3.setString("tz", "UTC-08:00");
        MutableDictionary geo3 = new MutableDictionary();
        geo3.setInt("alt", 350);
        mdoc3.setDictionary("geo", geo3);
        database.save(mdoc3);

        MutableDocument mdoc4 = new MutableDocument("sjc");
        mdoc4.setString("name", "SJC");
        mdoc4.setString("type", "airport");
        mdoc4.setString("country", "usa");
        mdoc4.setString("tz", "UTC-08:00");
        MutableDictionary geo4 = new MutableDictionary();
        geo4.setInt("alt", 300);
        mdoc4.setDictionary("geo", geo4);
        database.save(mdoc4);

        MutableDocument mdoc5 = new MutableDocument("rix");
        mdoc5.setString("name", "RIX");
        mdoc5.setString("type", "airport");
        mdoc5.setString("country", "Latvia");
        mdoc5.setString("tz", "UTC+02:00");
        MutableDictionary geo5 = new MutableDictionary();
        geo5.setInt("alt", 200);
        mdoc5.setDictionary("geo", geo5);
        database.save(mdoc5);

        MutableDocument mdoc5a = new MutableDocument("nrt");
        mdoc5a.setString("name", "NRT");
        mdoc5a.setString("type", "airport");
        mdoc5a.setString("country", "Japan");
        mdoc5a.setString("tz", "UTC+09:00");
        MutableDictionary geo5a = new MutableDictionary();
        geo5a.setInt("alt", 400);
        mdoc5a.setDictionary("geo", geo5a);
        database.save(mdoc5a);

        // route data
        MutableDocument mdoc6 = new MutableDocument("route1");
        mdoc6.setString("sourceairport", "RIX");
        mdoc6.setString("destinationairport", "SFO");
        mdoc6.setString("airlineid", "cbl_air");
        mdoc6.setString("airline", "CBL Air");
        mdoc6.setInt("stops", 0);
        mdoc6.setString("type", "route");
        database.save(mdoc6);

        MutableDocument mdoc7 = new MutableDocument("route2");
        mdoc7.setString("sourceairport", "SFO");
        mdoc7.setString("destinationairport", "SJC");
        mdoc7.setString("airlineid", "cbl_air");
        mdoc7.setString("airline", "CBL Air");
        mdoc7.setInt("stops", 0);
        mdoc7.setString("type", "route");
        database.save(mdoc7);

        // airline data
        MutableDocument mdoc8 = new MutableDocument("cbl_air");
        mdoc8.setString("name", "CBL Air");
        mdoc8.setString("callsign", "CBL");
        mdoc8.setString("type", "airline");
        database.save(mdoc8);

        MutableDocument mdoc9 = new MutableDocument("couchbase_air");
        mdoc9.setString("name", "Couchbase Air");
        mdoc9.setString("callsign", "CBA");
        mdoc9.setString("type", "airline");
        database.save(mdoc9);

        // hotel data
        MutableDocument mdoc10 = new MutableDocument("hotel128");
        mdoc10.setString("name", "Mt View Hotel");
        mdoc10.setString("type", "hotel");
        mdoc10.setArray("public_likes", new MutableArray(Arrays.asList((Object) "Armani Langworth", "Elfrieda Gutkowski", "Maureen Ruecker")));
        database.save(mdoc10);

        MutableDocument mdoc11 = new MutableDocument("hotel256");
        mdoc11.setString("name", "Hotel Couchbase");
        mdoc11.setString("type", "hotel");
        mdoc11.setArray("public_likes", new MutableArray(Arrays.asList((Object) "Elfrieda Gutkowski", "Maureen Ruecker", "Couchbase")));
        database.save(mdoc11);

        // landmark data
        MutableDocument mdoc12 = new MutableDocument("landmark001");
        mdoc12.setString("name", "Royal Engineers Museum");
        mdoc12.setString("country", "England");
        mdoc12.setString("type", "landmark");
        database.save(mdoc12);

        MutableDocument mdoc13 = new MutableDocument("landmark002");
        mdoc13.setString("name", "Engineer");
        mdoc13.setString("country", "England");
        mdoc13.setString("type", "landmark");
        database.save(mdoc13);
    }

    void prepareIndex() throws CouchbaseLiteException {
        database.createIndex("TypeNameIndex",
                Index.valueIndex(ValueIndexItem.property("type"),
                        ValueIndexItem.property("name")));
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

    // Indexing
    @Test
    public void testIndexing() throws CouchbaseLiteException {
        // For Documentation
        {
            database.createIndex("TypeNameIndex",
                    Index.valueIndex(ValueIndexItem.property("type"),
                            ValueIndexItem.property("name")));
        }
    }

    // SELECT statement
    @Test
    public void testSelectStatement() throws CouchbaseLiteException {
        // For Validation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id),
                            SelectResult.property("name"),
                            SelectResult.property("type"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("hotel"))
                    .orderBy(Ordering.expression(Meta.id));
            assertNotNull(query);
            ResultSet rs = query.execute();
            assertNotNull(rs);
            int count = 0;
            for (Result result : rs) {
                assertEquals(3, result.count());
                if (count == 0) {
                    assertEquals("hotel128", result.getString("id"));
                    assertEquals("hotel128", result.getString(0));
                    assertEquals("Mt View Hotel", result.getString("name"));
                    assertEquals("Mt View Hotel", result.getString(1));
                } else {
                    assertEquals("hotel256", result.getString("id"));
                    assertEquals("hotel256", result.getString(0));
                    assertEquals("Hotel Couchbase", result.getString("name"));
                    assertEquals("Hotel Couchbase", result.getString(1));
                }
                count++;
            }
            assertEquals(2, count);
        }

        // For Documentation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id),
                            SelectResult.property("name"),
                            SelectResult.property("type"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("hotel"))
                    .orderBy(Ordering.expression(Meta.id));
            ;
            try {
                ResultSet rs = query.execute();
                for (Result result : rs) {
                    Log.i("Sample", String.format("hotel id -> %s", result.getString("id")));
                    Log.i("Sample", String.format("hotel name -> %s", result.getString("name")));
                }
            } catch (CouchbaseLiteException e) {
                Log.e("Sample", e.getLocalizedMessage());
            }
        }
    }

    // META function
    @Test
    public void testMetaFunction() throws CouchbaseLiteException {
        // For Validation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("airport"))
                    .orderBy(Ordering.expression(Meta.id));
            assertNotNull(query);
            ResultSet rs = query.execute();
            assertNotNull(rs);
            int count = 0;
            for (Result result : rs) {
                assertEquals(1, result.count());
                if (count == 0) {
                    assertEquals("nrt", result.getString("id"));
                    assertEquals("nrt", result.getString(0));
                } else if (count == 1) {
                    assertEquals("rix", result.getString("id"));
                    assertEquals("rix", result.getString(0));
                } else if (count == 2) {
                    assertEquals("sfo", result.getString("id"));
                    assertEquals("sfo", result.getString(0));
                } else if (count == 3) {
                    assertEquals("sjc", result.getString("id"));
                    assertEquals("sjc", result.getString(0));
                }
                count++;
            }
            assertEquals(4, count);
        }

        // For Documentation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("airport"))
                    .orderBy(Ordering.expression(Meta.id));
            ResultSet rs = query.execute();
            for (Result result : rs) {
                Log.w("Sample", String.format("airport id -> %s", result.getString("id")));
                Log.w("Sample", String.format("airport id -> %s", result.getString(0)));
            }
        }
    }

    // all(*)
    @Test
    public void testSelectAll() throws CouchbaseLiteException {
        // For Validation
        {
            Query query = Query
                    .select(SelectResult.all())
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("hotel"));
            assertNotNull(query);
            ResultSet rs = query.execute();
            assertNotNull(rs);
            int count = 0;
            for (Result result : rs) {
                assertEquals(1, result.count());
                Dictionary all = result.getDictionary(DATABASE_NAME);
                assertNotNull(all);
                assertEquals(3, all.count());
                Log.i("Sample", String.format("hotel -> %s", all.toMap()));
                count++;
            }
            assertEquals(2, count);
        }

        // For Documentation
        {
            Query query = Query
                    .select(SelectResult.all())
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("hotel"));
            ResultSet rs = query.execute();
            for (Result result : rs)
                Log.i("Sample", String.format("hotel -> %s", result.getDictionary(DATABASE_NAME).toMap()));
        }
    }

    // WHERE statement
    @Test
    public void testWhereStatement() throws CouchbaseLiteException {
        // For Validation
        {
            Query query = Query
                    .select(SelectResult.all())
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("hotel"))
                    .orderBy(Ordering.property("name"))
                    .limit(1);
            assertNotNull(query);
            ResultSet rs = query.execute();
            assertNotNull(rs);
            int count = 0;
            for (Result result : rs) {
                assertEquals(1, result.count());
                Dictionary all = result.getDictionary(DATABASE_NAME);
                assertNotNull(all);
                assertEquals(3, all.count());
                assertEquals("Hotel Couchbase", all.getString("name"));
                assertEquals("hotel", all.getString("type"));
                count++;
            }
            assertEquals(1, count);
        }

        // For Documentation
        {
            Query query = Query
                    .select(SelectResult.all())
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("hotel"))
                    .limit(10);
            ResultSet rs = query.execute();
            for (Result result : rs) {
                Dictionary all = result.getDictionary(DATABASE_NAME);
                Log.i("Sample", String.format("name -> %s", all.getString("name")));
                Log.i("Sample", String.format("type -> %s", all.getString("type")));
            }
        }
    }

    // Collection Operators
    @Test
    public void testCollectionStatement() throws CouchbaseLiteException {
        // For Validation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id),
                            SelectResult.property("name"),
                            SelectResult.property("public_likes"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("hotel")
                            .and(ArrayFunction.contains(Expression.property("public_likes"), "Armani Langworth")));
            assertNotNull(query);
            ResultSet rs = query.execute();
            assertNotNull(rs);
            int count = 0;
            for (Result result : rs) {
                assertEquals(3, result.count());
                assertEquals("hotel128", result.getString("id"));
                assertEquals("Mt View Hotel", result.getString("name"));
                assertEquals(Arrays.asList((Object) "Armani Langworth", "Elfrieda Gutkowski", "Maureen Ruecker"), result.getArray("public_likes").toList());
                count++;
            }
            assertEquals(1, count);
        }
        // For Documentation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id),
                            SelectResult.property("name"),
                            SelectResult.property("public_likes"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("hotel")
                            .and(ArrayFunction.contains(Expression.property("public_likes"), "Armani Langworth")));
            ResultSet rs = query.execute();
            for (Result result : rs)
                Log.i("Sample", String.format("public_likes -> %s", result.getArray("public_likes").toList()));
        }
    }

    // Pattern Matching
    @Test
    public void testPatternMatching() throws CouchbaseLiteException {
        // For Validation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id),
                            SelectResult.property("country"),
                            SelectResult.property("name"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("landmark")
                            .and(Expression.property("name").like("Royal Engineers Museum")));
            assertNotNull(query);
            ResultSet rs = query.execute();
            assertNotNull(rs);
            int count = 0;
            for (Result result : rs) {
                assertEquals(3, result.count());
                assertEquals("landmark001", result.getString("id"));
                assertEquals("England", result.getString("country"));
                assertEquals("Royal Engineers Museum", result.getString("name"));
                count++;
            }
            assertEquals(1, count);
        }
        // For Documentation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id),
                            SelectResult.property("country"),
                            SelectResult.property("name"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("landmark")
                            .and(Expression.property("name").like("Royal Engineers Museum")));
            ResultSet rs = query.execute();
            for (Result result : rs)
                Log.i("Sample", String.format("name -> %s", result.getString("name")));
        }
    }

    // Wildcard Match
    @Test
    public void testWildcardMatch() throws CouchbaseLiteException {
        // For Validation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id),
                            SelectResult.property("country"),
                            SelectResult.property("name"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("landmark")
                            .and(Expression.property("name").like("Eng%e%")));
            assertNotNull(query);
            ResultSet rs = query.execute();
            assertNotNull(rs);
            int count = 0;
            for (Result result : rs) {
                assertEquals(3, result.count());
                assertEquals("landmark002", result.getString("id"));
                assertEquals("England", result.getString("country"));
                assertEquals("Engineer", result.getString("name"));
                count++;
            }
            assertEquals(1, count);
        }
        // For Documentation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id),
                            SelectResult.property("country"),
                            SelectResult.property("name"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("landmark")
                            .and(Expression.property("name").like("Eng%e%")));
            ResultSet rs = query.execute();
            for (Result result : rs)
                Log.i("Sample", String.format("name -> %s", result.getString("name")));
        }
    }

    // Wildcard Character Match
    @Test
    public void testWildCharacterMatch() throws CouchbaseLiteException {
        // For Validation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id),
                            SelectResult.property("country"),
                            SelectResult.property("name"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("landmark")
                            .and(Expression.property("name").like("Eng____r")));
            assertNotNull(query);
            ResultSet rs = query.execute();
            assertNotNull(rs);
            int count = 0;
            for (Result result : rs) {
                assertEquals(3, result.count());
                assertEquals("landmark002", result.getString("id"));
                assertEquals("England", result.getString("country"));
                assertEquals("Engineer", result.getString("name"));
                count++;
            }
            assertEquals(1, count);
        }
        // For Documentation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id),
                            SelectResult.property("country"),
                            SelectResult.property("name"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("landmark")
                            .and(Expression.property("name").like("Eng____r")));
            ResultSet rs = query.execute();
            for (Result result : rs)
                Log.i("Sample", String.format("name -> %s", result.getString("name")));
        }
    }

    // Regex Match
    @Test
    public void testRegexMatch() throws CouchbaseLiteException {
        // For Validation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id),
                            SelectResult.property("country"),
                            SelectResult.property("name"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("landmark")
                            .and(Expression.property("name").regex("\\bEng.*r\\b")));
            assertNotNull(query);
            ResultSet rs = query.execute();
            assertNotNull(rs);
            int count = 0;
            for (Result result : rs) {
                assertEquals(3, result.count());
                assertEquals("landmark002", result.getString("id"));
                assertEquals("England", result.getString("country"));
                assertEquals("Engineer", result.getString("name"));
                count++;
            }
            assertEquals(1, count);
        }
        // For Documentation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id),
                            SelectResult.property("country"),
                            SelectResult.property("name"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("landmark")
                            .and(Expression.property("name").regex("\\bEng.*r\\b")));
            ResultSet rs = query.execute();
            for (Result result : rs)
                Log.i("Sample", String.format("name -> %s", result.getString("name")));
        }
    }

    // JOIN statement
    @Test
    public void testJoinStatement() throws CouchbaseLiteException {
        // For Validation
        {
            Query query = Query.select(
                    SelectResult.expression(Expression.property("name").from("airline")),
                    SelectResult.expression(Expression.property("callsign").from("airline")),
                    SelectResult.expression(Expression.property("destinationairport").from("route")),
                    SelectResult.expression(Expression.property("stops").from("route")),
                    SelectResult.expression(Expression.property("airline").from("route")))
                    .from(DataSource.database(database).as("airline"))
                    .join(Join.join(DataSource.database(database).as("route"))
                            .on(Meta.id.from("airline").equalTo(Expression.property("airlineid").from("route"))))
                    .where(Expression.property("type").from("route").equalTo("route")
                            .and(Expression.property("type").from("airline").equalTo("airline"))
                            .and(Expression.property("sourceairport").from("route").equalTo("RIX")));
            assertNotNull(query);
            ResultSet rs = query.execute();
            assertNotNull(rs);
            int count = 0;
            for (Result result : rs) {
                assertEquals(5, result.count());
                Log.e("Sample", result.toMap().toString());
                assertEquals("CBL Air", result.getString(0));
                assertEquals("CBL Air", result.getString("name"));
                assertEquals("CBL", result.getString(1));
                assertEquals("CBL", result.getString("callsign"));
                assertEquals("SFO", result.getString(2));
                assertEquals("SFO", result.getString("destinationairport"));
                assertEquals(0, result.getInt(3));
                assertEquals(0, result.getInt("stops"));
                assertEquals("CBL Air", result.getString(4));
                assertEquals("CBL Air", result.getString("airline"));
                count++;
            }
            assertEquals(1, count);
        }

        // For Documentation
        {
            Query query = Query.select(
                    SelectResult.expression(Expression.property("name").from("airline")),
                    SelectResult.expression(Expression.property("callsign").from("airline")),
                    SelectResult.expression(Expression.property("destinationairport").from("route")),
                    SelectResult.expression(Expression.property("stops").from("route")),
                    SelectResult.expression(Expression.property("airline").from("route")))
                    .from(DataSource.database(database).as("airline"))
                    .join(Join.join(DataSource.database(database).as("route"))
                            .on(Meta.id.from("airline").equalTo(Expression.property("airlineid").from("route"))))
                    .where(Expression.property("type").from("route").equalTo("route")
                            .and(Expression.property("type").from("airline").equalTo("airline"))
                            .and(Expression.property("sourceairport").from("route").equalTo("RIX")));
            ResultSet rs = query.execute();
            for (Result result : rs)
                Log.w("Sample", String.format("%s", result.toMap().toString()));
        }
    }

    // GROUPBY statement
    @Test
    public void testGroupByStatement() throws CouchbaseLiteException {
        // For Validation
        {
            Query query = Query.select(
                    SelectResult.expression(Function.count("*")),
                    SelectResult.property("country"),
                    SelectResult.property("tz"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("airport")
                            .and(Expression.property("geo.alt").greaterThanOrEqualTo(300)))
                    .groupBy(Expression.property("country"),
                            Expression.property("tz"))
                    .orderBy(Ordering.expression(Function.count("*")).descending());
            assertNotNull(query);
            ResultSet rs = query.execute();
            assertNotNull(rs);
            int count = 0;
            for (Result result : rs) {
                if (count == 0) {
                    assertEquals(2, result.getInt(0));
                    assertEquals(2, result.getInt("$1"));
                    assertEquals("usa", result.getString(1));
                    assertEquals("usa", result.getString("country"));
                    assertEquals("UTC-08:00", result.getString(2));
                    assertEquals("UTC-08:00", result.getString("tz"));
                } else {
                    assertEquals(1, result.getInt(0));
                    assertEquals(1, result.getInt("$1"));
                    assertEquals("Japan", result.getString(1));
                    assertEquals("Japan", result.getString("country"));
                    assertEquals("UTC+09:00", result.getString(2));
                    assertEquals("UTC+09:00", result.getString("tz"));
                }
                count++;
            }
            assertEquals(2, count);
        }

        // For Documentation
        {
            Query query = Query.select(
                    SelectResult.expression(Function.count("*")),
                    SelectResult.property("country"),
                    SelectResult.property("tz"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("airport")
                            .and(Expression.property("geo.alt").greaterThanOrEqualTo(300)))
                    .groupBy(Expression.property("country"),
                            Expression.property("tz"))
                    .orderBy(Ordering.expression(Function.count("*")).descending());
            ResultSet rs = query.execute();
            for (Result result : rs)
                Log.i("Sample",
                        String.format("There are %d airports on the %s timezone located in %s and above 300ft",
                                result.getInt("$1"),
                                result.getString("tz"),
                                result.getString("country")));
        }
    }

    // ORDER BY statement
    @Test
    public void testOrderByStatement() throws CouchbaseLiteException {
        // For Validation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id),
                            SelectResult.property("name"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("hotel"))
                    .orderBy(Ordering.property("name").ascending())
                    .limit(10);
            assertNotNull(query);
            ResultSet rs = query.execute();
            assertNotNull(rs);
            int count = 0;
            for (Result result : rs) {
                assertEquals(2, result.count());
                if (count == 0) {
                    assertEquals("hotel256", result.getString("id"));
                    assertEquals("hotel256", result.getString(0));
                    assertEquals("Hotel Couchbase", result.getString("name"));
                    assertEquals("Hotel Couchbase", result.getString(1));
                } else {
                    assertEquals("hotel128", result.getString("id"));
                    assertEquals("hotel128", result.getString(0));
                    assertEquals("Mt View Hotel", result.getString("name"));
                    assertEquals("Mt View Hotel", result.getString(1));
                }
                count++;
            }
            assertEquals(2, count);
        }

        // For Documentation
        {
            Query query = Query
                    .select(SelectResult.expression(Meta.id),
                            SelectResult.property("name"))
                    .from(DataSource.database(database))
                    .where(Expression.property("type").equalTo("hotel"))
                    .orderBy(Ordering.property("name").ascending())
                    .limit(10);
            ResultSet rs = query.execute();
            for (Result result : rs)
                Log.i("Sample", String.format("%s", result.toMap()));
        }
    }

    // IN operator
    @Test
    public void testInOperator() throws CouchbaseLiteException {
        // For Validation
        {
            Query query = Query.select(SelectResult.property("name"))
                    .from(DataSource.database(database))
                    .where(Expression.property("country").in("Latvia", "usa")
                            .and(Expression.property("type").equalTo("airport")))
                    .orderBy(Ordering.property("name"));
            assertNotNull(query);
            ResultSet rs = query.execute();
            assertNotNull(rs);
            int count = 0;
            for (Result result : rs) {
                if (count == 0) assertEquals("RIX", result.getString("name"));
                if (count == 1) assertEquals("SFO", result.getString("name"));
                if (count == 2) assertEquals("SJC", result.getString("name"));
                count++;
            }
            assertEquals(3, count);
        }

        // For Documentation
        {
            Query query = Query.select(SelectResult.property("name"))
                    .from(DataSource.database(database))
                    .where(Expression.property("country").in("Latvia", "usa")
                            .and(Expression.property("type").equalTo("airport")))
                    .orderBy(Ordering.property("name"));
            ResultSet rs = query.execute();
            for (Result result : rs)
                Log.w("Sample", String.format("%s", result.toMap().toString()));
        }
    }
}
