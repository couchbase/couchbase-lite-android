package com.couchbase.apiwalkthrough;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import com.couchbase.lite.Blob;
import com.couchbase.lite.DataSource;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.Document;
import com.couchbase.lite.Expression;
import com.couchbase.lite.FullTextQueryRow;
import com.couchbase.lite.IndexOptions;
import com.couchbase.lite.IndexType;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryRow;
import com.couchbase.lite.Replicator;
import com.couchbase.lite.ReplicatorConfiguration;
import com.couchbase.lite.ReplicatorTarget;
import com.couchbase.lite.ResultSet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // create database
        DatabaseConfiguration config = new DatabaseConfiguration(getApplicationContext());
        final Database database = new Database("my-database", config);

        // create document
        Document newTask = new Document();
        newTask.set("type", "task");
        newTask.set("owner", "todo");
        newTask.set("createdAt", new Date());
        database.save(newTask);

        // mutate document
        newTask.set("name", "Apples");
        database.save(newTask);

        // typed accessors
        newTask.set("createdAt", new Date());
        Date date = newTask.getDate("createdAt");

        // database transaction
        database.inBatch(new TimerTask() {
            @Override
            public void run() {
                for (int i = 0; i < 10; i++) {
                    Document doc = new Document();
                    doc.set("type", "user");
                    doc.set("name", String.format("user %s", i));
                    database.save(doc);
                    Log.d("app", String.format("saved user document %s", doc.getString("name")));
                }
            }
        });

        // blob
        InputStream inputStream = null;
        try {
            inputStream = getAssets().open("avatar.jpg");
        } catch (IOException e) {
            e.printStackTrace();
        }

        Blob blob = new Blob("image/jpg", inputStream);
        newTask.set("avatar", blob);
        database.save(newTask);

        Blob taskBlob = newTask.getBlob("avatar");
        byte[] data = taskBlob.getContent();

        // query
        Query query = Query.select()
            .from(DataSource.database(database))
            .where(Expression.property("type").equalTo("user").add(Expression.property("admin").equalTo(false)));

        ResultSet rows = query.run();
        QueryRow row;
        while ((row = rows.next()) != null) {
            Log.d("app", String.format("doc ID :: %s", row.getDocumentID()));
        }

        // fts example
        // insert documents
        List<String> tasks = new ArrayList<>(Arrays.asList("buy groceries", "play chess", "book travels", "buy museum tickets"));
        for (String task : tasks) {
            Document doc = new Document();
            doc.set("type", "task");
            doc.set("name", task);
            database.save(doc);
        }

        // create index
        List<Expression> expressions = Arrays.<Expression>asList(Expression.property("name"));
        database.createIndex(expressions, IndexType.FullText, new IndexOptions(null, false));

        Query ftsQuery = Query.select()
            .from(DataSource.database(database))
            .where(Expression.property("name").match("'buy'"));

        ResultSet ftsQueryResult = ftsQuery.run();
        FullTextQueryRow ftsRow;
        while ((ftsRow = (FullTextQueryRow) ftsQueryResult.next()) != null) {
            Log.d("app", String.format("document properties :: %s", ftsRow.getDocument().toMap()));
        }

        // replication
        URI uri = null;
        try {
            uri = new URI("blip://10.0.2.2:4984/db");
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        ReplicatorConfiguration replConfig = new ReplicatorConfiguration();
        replConfig.setDatabase(database);
        replConfig.setTarget(new ReplicatorTarget(uri));
        Replicator replicator = new Replicator(replConfig);
        replicator.start();
    }
}
