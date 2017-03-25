package com.couchbase.lite.examples.helloworld;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseOptions;
import com.couchbase.lite.Document;
import com.couchbase.lite.Log;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    Database db = null;

    final static String FIXED_DOCID = "fixed_doc_id";
    Button btnPost;
    Button btnShow;
    EditText editText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        // enable logging
        // myself
        Log.enableLogging(TAG, Log.VERBOSE);
        // couchbase
        Log.enableLogging(Log.DATABASE, Log.VERBOSE);

        Log.v(TAG, "onCreate()");

        DatabaseOptions options = DatabaseOptions.getDefaultOptions();
        options.setDirectory(getFilesDir());
        db = new Database("helloworld", options);


        editText = (EditText)findViewById(R.id.etText);
        btnPost = (Button)findViewById(R.id.btnPost);
        btnPost.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String text = editText.getText().toString();
                Document doc = db.getDocument(FIXED_DOCID);
                doc.set("text", text);
                doc.save();
            }
        });
        btnShow = (Button)findViewById(R.id.btnShow);
        btnShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Document doc = db.getDocument(FIXED_DOCID);
                Toast.makeText(MainActivity.this, doc.getString("text"), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        Log.v(TAG, "onDestroy()");
        if(db!=null) {
            db.close();
            db = null;
        }
        super.onDestroy();
    }
}
