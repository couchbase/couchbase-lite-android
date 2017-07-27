package com.couchbase.apiwalkthrough;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.couchbase.lite.Blob;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DataSource;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.Document;
import com.couchbase.lite.Expression;
import com.couchbase.lite.FullTextQueryRow;
import com.couchbase.lite.IndexOptions;
import com.couchbase.lite.IndexType;
import com.couchbase.lite.LiveQuery;
import com.couchbase.lite.LiveQueryChange;
import com.couchbase.lite.LiveQueryChangeListener;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.Replicator;
import com.couchbase.lite.ReplicatorChangeListener;
import com.couchbase.lite.ReplicatorConfiguration;
import com.couchbase.lite.ResultSet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // create database
        DatabaseConfiguration config = new DatabaseConfiguration(getApplicationContext());
        config.setConflictResolver(new ExampleConflictResolver());
        Database database = null;
        try {
            database = new Database("my-database", config);
        } catch (CouchbaseLiteException e) {
            Log.e("app", "Failed to create database instance", e);
            return;
        }

        // create document
        Document newTask = new Document();
        newTask.set("type", "task");
        newTask.set("owner", "todo");
        newTask.set("createdAt", new Date());
        try {
            database.save(newTask);
        } catch (CouchbaseLiteException e) {
            Log.e("app", "Failed to save document", e);
        }

        // mutate document
        newTask.set("name", "Apples");
        try {
            database.save(newTask);
        } catch (CouchbaseLiteException e) {
            Log.e("app", "Failed to save document", e);
        }

        // typed accessors
        newTask.set("createdAt", new Date());
        Date date = newTask.getDate("createdAt");

        // database transaction
        try {
            final Database finalDatabase = database;
            database.inBatch(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        Document doc = new Document();
                        doc.set("type", "user");
                        doc.set("name", String.format("user %s", i));
                        try {
                            finalDatabase.save(doc);
                        } catch (CouchbaseLiteException e) {
                            Log.e("app", "Failed to save document", e);
                        }
                        Log.d("app", String.format("saved user document %s", doc.getString("name")));
                    }
                }
            });
        } catch (CouchbaseLiteException e) {
            Log.e("app", "Database batch operation failed", e);
        }

        // blob
        InputStream inputStream = null;
        try {
            inputStream = getAssets().open("avatar.jpg");
        } catch (IOException e) {
            Log.e("app", "Failed to open asset", e);
        }

        Blob blob = new Blob("image/jpg", inputStream);
        newTask.set("avatar", blob);
        try {
            database.save(newTask);
        } catch (CouchbaseLiteException e) {
            Log.e("app", "Failed to save document", e);
        }

        Blob taskBlob = newTask.getBlob("avatar");
        byte[] data = taskBlob.getContent();

        // query
        Query query = Query.select()
            .from(DataSource.database(database))
            .where(Expression.property("type").equalTo("user").add(Expression.property("admin").equalTo(false)));

        ResultSet rows = null;
        try {
            rows = query.run();
        } catch (CouchbaseLiteException e) {
            Log.e("app", "Failed to run query", e);
        }
        QueryRow row;
        while ((row = rows.next()) != null) {
            Log.d("app", String.format("doc ID :: %s", row.getDocumentID()));
        }

        // live query
        final LiveQuery liveQuery = query.toLive();
        liveQuery.addChangeListener(new LiveQueryChangeListener() {
            @Override
            public void changed(LiveQueryChange change) {
                Log.d("query", String.format("Number of rows :: %s", change.getRows().toString()));
            }
        });
        liveQuery.run();
        Document newDoc = new Document();
        newDoc.set("type", "user");
        newDoc.set("admin", false);
        try {
            database.save(newDoc);
        } catch (CouchbaseLiteException e) {
            Log.e("app", "Failed to save document", e);
        }

        // fts example
        // insert documents
        List<String> tasks = new ArrayList<>(Arrays.asList("buy groceries", "play chess", "book travels", "buy museum tickets"));
        for (String task : tasks) {
            Document doc = new Document();
            doc.set("type", "task");
            doc.set("name", task);
            try {
                database.save(doc);
            } catch (CouchbaseLiteException e) {
                Log.e("app", "Failed to save document", e);
            }
        }

        // create index
        List<Expression> expressions = Arrays.<Expression>asList(Expression.property("name"));
        try {
            database.createIndex(expressions, IndexType.FullText, new IndexOptions(null, false));
        } catch (CouchbaseLiteException e) {
            Log.e("app", "Failed to run FTS query", e);
        }

        Query ftsQuery = Query.select()
            .from(DataSource.database(database))
            .where(Expression.property("name").match("'buy'"));

        ResultSet ftsQueryResult = null;
        try {
            ftsQueryResult = ftsQuery.run();
        } catch (CouchbaseLiteException e) {
            Log.e("app", "Failed to run query", e);
        }
        FullTextQueryRow ftsRow;
        while ((ftsRow = (FullTextQueryRow) ftsQueryResult.next()) != null) {
            Log.d("app", String.format("document properties :: %s", ftsRow.getDocument().toMap()));
        }

        // create conflict
        /*
         * 1. Create a document twice with the same ID.
         * 2. The `theirs` properties in the conflict resolver represents the current rev and
         * `mine` is what's being saved.
         * 3. Read the document after the second save operation and verify its property is as expected.
         */
        Document theirs = new Document("buzz");
        theirs.set("status", "theirs");
        Document mine = new Document("buzz");
        mine.set("status", "mine");
        try {
            database.save(theirs);
            database.save(mine);
        } catch (CouchbaseLiteException e) {
            Log.e("app", "Failed to save document", e);
        }
        Document conflictResolverResult = database.getDocument("buzz");
        Log.d("app", String.format("conflictResolverResult doc.status ::: %s", conflictResolverResult.getString("status")));

        // replication
        /*
         * Tested with SG 1.5 https://www.couchbase.com/downloads
         * Config file:
         * {
              "databases": {
                "db": {
                  "server":"walrus:",
                  "users": {
                    "GUEST": {"disabled": false, "admin_channels": ["*"]}
                  },
                  "unsupported": {
                    "replicator_2":true
                  }
                }
              }
            }
         */
        URI uri = null;
        try {
            uri = new URI("blip://10.0.2.2:4984/db");
        } catch (URISyntaxException e) {
            Log.e("app", "Invalid URL", e);
        }
        ReplicatorConfiguration replConfig = new ReplicatorConfiguration(database, uri);
        Replicator replicator = new Replicator(replConfig);
        replicator.start();

        // replication change listener
        replicator.addChangeListener(new ReplicatorChangeListener() {
            @Override
            public void changed(Replicator replicator, Replicator.Status status, CouchbaseLiteException error) {
                if (status.getActivityLevel().equals(Replicator.ActivityLevel.STOPPED)) {
                    Log.d("app", "Replication was completed.");
                }
            }
        });
    }
}
