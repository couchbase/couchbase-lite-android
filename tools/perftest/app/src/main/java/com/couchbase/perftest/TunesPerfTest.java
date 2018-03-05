package com.couchbase.perftest;


import com.couchbase.lite.Collation;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DataSource;
import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.Expression;
import com.couchbase.lite.FullTextExpression;
import com.couchbase.lite.FullTextIndex;
import com.couchbase.lite.FullTextIndexItem;
import com.couchbase.lite.IndexBuilder;
import com.couchbase.lite.Meta;
import com.couchbase.lite.MutableDocument;
import com.couchbase.lite.Ordering;
import com.couchbase.lite.Parameters;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryBuilder;
import com.couchbase.lite.Result;
import com.couchbase.lite.ResultSet;
import com.couchbase.lite.SelectResult;
import com.couchbase.lite.ValueIndex;
import com.couchbase.lite.ValueIndexItem;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class TunesPerfTest extends PerfTest {
    static final int kNumIterations = 10;
    static final int kInterTestSleep = 0; // 0ms

    List<Map<String, Object>> _tracks = new ArrayList<>();
    int _documentCount = 0;
    List<String> _artists;
    Benchmark _importBench = new Benchmark();
    //Benchmark _updatePlayCountBench = new Benchmark();
    Benchmark _updateArtistsBench = new Benchmark();
    Benchmark _indexArtistsBench = new Benchmark();
    Benchmark _queryArtistsBench = new Benchmark();
    Benchmark _queryIndexedArtistsBench = new Benchmark();
    Benchmark _queryAlbumsBench = new Benchmark();
    Benchmark _queryIndexedAlbumsBench = new Benchmark();
    Benchmark _indexFTSBench = new Benchmark();
    Benchmark _queryFTSBench = new Benchmark();


    public TunesPerfTest(DatabaseConfiguration dbConfig) {
        super(dbConfig);
    }

    @Override
    protected void setUp() {
        super.setUp();
        _tracks = new ArrayList<>();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(getAsset("iTunesMusicLibrary.json")));
            try {
                String line;
                while ((line = br.readLine()) != null) {
                    Map<String, Object> track = new Gson().fromJson(line,
                            new TypeToken<HashMap<String, Object>>() {
                            }.getType());
                    _tracks.add(track);
                }
            } finally {
                br.close();
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        _documentCount = _tracks.size();
    }

    @Override
    protected void test() {
        int numDocs = 0;
        int numUpdates = 0;
        int numArtists = 0;
        int numAlbums = 0;
        int numFTS = 0;

        for (int i = 0; i < kNumIterations; i++) {
            System.err.print(String.format("Starting iteration #%d...\n", i + 1));
            eraseDB();
            pause();
            numDocs = importLibrary();
            pause();
            reopenDB();
            pause();
            numUpdates = updateArtistNames();
            pause();

            numArtists = queryAllArtists(_queryArtistsBench);
            pause();
            numAlbums = queryAlbums(_queryAlbumsBench);
            pause();

            createArtistsIndex();
            pause();

            int numArtists2 = queryAllArtists(_queryIndexedArtistsBench);
            assert (numArtists2 == numArtists);
            pause();
            int numAlbums2 = queryAlbums(_queryIndexedAlbumsBench);
            assert (numAlbums2 == numAlbums);
            pause();

            numFTS = fullTextSearch();
            pause();
        }

        System.err.print("\n\n");
        System.err.print(String.format("Import %5d docs:  ", numDocs));
        _importBench.printReport(null);
        System.err.print("                    ");
        _importBench.printReport(1.0 / numDocs, "doc");
        System.err.print(String.format("                     Rate: %.0f docs/sec\n", numDocs / _importBench.median()));

//        _updatePlayCountBench.empty();
//        System.err.print("Update all docs:    ");
//        _updatePlayCountBench.printReport(null);
//        System.err.print("                    ");
//        _updatePlayCountBench.printReport(1.0 / numDocs, "update");

        System.err.print(String.format("Update %4d docs:   ", numUpdates));
        _updateArtistsBench.printReport(null);
        System.err.print(String.format("                     Rate: %.0f docs/sec\n", numUpdates / _updateArtistsBench.median()));
        System.err.print("                    ");
        _updateArtistsBench.printReport(1.0 / numUpdates, "update");
        System.err.print(String.format("Query %4d artists: ", numArtists));
        _queryArtistsBench.printReport(null);
        System.err.print("                    ");
        _queryArtistsBench.printReport(1.0 / numArtists, "row");
        System.err.print(String.format("Query %4d albums:  ", numAlbums));
        _queryAlbumsBench.printReport(null);
        System.err.print("                    ");
        _queryAlbumsBench.printReport(1.0 / numArtists, "artist");
        System.err.print("Index by artist:    ");
        _indexArtistsBench.printReport(null);
        System.err.print("                    ");
        _indexArtistsBench.printReport(1.0 / numDocs, "doc");
        System.err.print("Re-query artists:   ");
        _queryIndexedArtistsBench.printReport(null);
        System.err.print("                    ");
        _queryIndexedArtistsBench.printReport(1.0 / numArtists, "row");
        System.err.print("Re-query albums:    ");
        _queryIndexedAlbumsBench.printReport(null);
        System.err.print("                    ");
        _queryIndexedAlbumsBench.printReport(1.0 / numArtists, "artist");
        System.err.print("FTS indexing:       ");
        _indexFTSBench.printReport(null);
        System.err.print("                    ");
        _indexFTSBench.printReport(1.0 / numDocs, "doc");
        System.err.print("FTS query:          ");
        _queryFTSBench.printReport(null);
        System.err.print("                    ");
        _queryFTSBench.printReport(1.0 / numFTS, "row");
    }

    protected InputStream getAsset(String name) {
        return this.getClass().getResourceAsStream("/assets/" + name);
    }

    protected void pause() {
        try {
            Thread.sleep(kInterTestSleep);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    // Adds all the tracks to the database.
    protected int importLibrary() {

        _importBench.start();
        _documentCount = 0;
        final AtomicLong startTransaction = new AtomicLong();
        try {
            db.inBatch(new Runnable() {
                @Override
                public void run() {
                    for (Map<String, Object> track : _tracks) {
                        String trackType = (String) track.get("Track Type");
                        if (!trackType.equals("File") && !trackType.equals("Remote"))
                            continue;
                        String docID = (String) track.get("Persistent ID");
                        if (docID == null)
                            continue;
                        _documentCount++;
                        MutableDocument doc = new MutableDocument(docID);
                        doc.setData(track);
                        try {
                            db.save(doc);
                        } catch (CouchbaseLiteException e) {
                            e.printStackTrace();
                        }
                    }
                    startTransaction.set(System.nanoTime());
                }
            });
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        double commitTime = System.nanoTime() - startTransaction.longValue();
        double t = _importBench.stop();
        System.err.print(String.format("Imported %d documents in %.06f sec (import %g, commit %g)\n", _documentCount, t, t - commitTime, commitTime));
        return _documentCount;
    }

    protected ResultSet queryAllDocuments() throws CouchbaseLiteException {
        return QueryBuilder.select(SelectResult.expression(Meta.id))
                .from(DataSource.database(db))
                .execute();
    }

    int count = 0;

    // Strips "The " from the names of all artists.
    protected int updateArtistNames() {
        _updateArtistsBench.start();
        count = 0;
        final AtomicLong startTransaction = new AtomicLong();
        try {
            db.inBatch(new Runnable() {
                @Override
                public void run() {
                    try {
                        ResultSet rs = queryAllDocuments();
                        for (Result r : rs) {
                            String docID = r.getString(0);
                            MutableDocument doc = db.getDocument(docID).toMutable();
                            String artist = doc.getString("Artist");
                            if (artist != null && artist.startsWith("The ")) {
                                doc.setString("Artist", artist.substring(4));
                                db.save(doc);
                                count++;
                            }
                        }
                    } catch (CouchbaseLiteException e) {
                        e.printStackTrace();
                    }
                    startTransaction.set(System.nanoTime());
                }
            });

        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        double commitTime = System.nanoTime() - startTransaction.longValue();
        double t = _updateArtistsBench.stop();
        System.err.print(String.format("Updated %d docs in %.06f sec (update %g, commit %g)\n", count, t, t - commitTime, commitTime));
        return count;
    }

    List<String> collectQueryResults(Query query) throws CouchbaseLiteException {
        List<String> results = new ArrayList<>();
        ResultSet rs = query.execute();
        for(Result r : rs){
            results.add(r.getString(0));
        }
        return results;
    }

    // Collects the names of all artists in the database using a query.
    protected int queryAllArtists(Benchmark bench) {
        Expression artist = Expression.property("Artist");
        Expression compilation = Expression.property("Compilation");
        Collation.Unicode cd = Collation.unicode().locale(null).ignoreCase(true).ignoreAccents(true);
        Query query = QueryBuilder.select(SelectResult.expression(artist))
                .from(DataSource.database(db))
                .where(artist.notNullOrMissing().and(compilation.isNullOrMissing()))
                .groupBy(artist.collate(cd))
                //.having(null)
                .orderBy(Ordering.expression(artist.collate(cd)));
                //.limit(null);
        try {
            System.err.println(String.format("%s", query.explain()));
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        bench.start();
        try {
            _artists = collectQueryResults(query);
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        double t = bench.stop();
        System.err.print(String.format("Artist query took %.06f sec", t));
        System.err.print(String.format("%d artists:\n'%s'", _artists.size(), _artists.toString()));
        System.err.print(String.format("%d artists, from %s to %s", _artists.size(), _artists.get(0), _artists.get(_artists.size() - 1)));
        return _artists.size();
    }

    protected int queryAlbums(Benchmark bench) {
        Expression artist = Expression.property("Artist");
        Expression compilation = Expression.property("Compilation");
        Expression album = Expression.property("Album");
        Collation.Unicode cd = Collation.unicode().locale(null).ignoreCase(true).ignoreAccents(true);

        Query query = QueryBuilder.select(SelectResult.expression(album))
                .from(DataSource.database(db))
                .where(artist.collate(cd).equalTo(Expression.parameter("ARTIST")).and(compilation.isNullOrMissing()))
                .groupBy(album.collate(cd))
                //.having(null)
                .orderBy(Ordering.expression(album.collate(cd)));
        //.limit(null);
        try {
            System.err.println(String.format("%s", query.explain()));
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        bench.start();
        // Run one query per artist to find their albums. We could write a single query to get all of
        // these results at once, but I want to benchmark running a CBLQuery lots of times...
        int albumCount = 0;
        try {
            for(String artistName : _artists){
                Parameters params = new Parameters();
                params.setString("ARTIST", artistName);
                query.setParameters(params);
                List<String> albums = collectQueryResults(query);
                albumCount += albums.size();
                System.err.print(String.format("%d albums by %s: '%s'", albums.size(), artistName, albums.toString()));
            }
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        double t = bench.stop();
        System.err.print(String.format("%d albums total, in %.06f sec", albumCount, t));
        return albumCount;
    }

    protected int fullTextSearch() {
        _indexFTSBench.start();
        FullTextIndex index = IndexBuilder.fullTextIndex(FullTextIndexItem.property("Name"));
        try {
            db.createIndex("name",index);
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        _indexFTSBench.stop();
        pause();

        Expression artist = Expression.property("Artist");
        Expression name = Expression.property("Name");
        Expression album = Expression.property("Album");
        Collation.Unicode cd = Collation.unicode().locale(null).ignoreCase(true).ignoreAccents(true);

        Query query = QueryBuilder.select(SelectResult.expression(name), SelectResult.expression(artist),SelectResult.expression(album))
                .from(DataSource.database(db))
                .where(FullTextExpression.index("name").match("'Rock'"))
                .orderBy(Ordering.expression(artist.collate(cd)),Ordering.expression(album.collate(cd)));
        try {
            System.err.println(String.format("%s", query.explain()));
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        pause();

        _queryFTSBench.start();
        List<String> results = null;
        try {
            results = collectQueryResults(query);
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }
        double t = _queryFTSBench.stop();
        System.err.print(String.format("%d 'rock' songs in %.06f sec: \"%s\"", results.size(), t, results.toString()));
        System.err.print(String.format("%d 'rock' songs in %.06f sec",results.size(), t));
        return results.size();
    }

    protected void createArtistsIndex() {
        System.err.print("Indexing artists...");
        _indexArtistsBench.start();

        Collation.Unicode cd = Collation.unicode().locale(null).ignoreCase(true).ignoreAccents(true);
        ValueIndex index = IndexBuilder.valueIndex(ValueIndexItem.property("Artist"), ValueIndexItem.property("Compilation"));
        try {
            db.createIndex("byArtist", index);
        } catch (CouchbaseLiteException e) {
            e.printStackTrace();
        }

        double t = _indexArtistsBench.stop();
        System.err.print(String.format("Indexed artists in %.06f sec", t));
    }
}
