package com.couchbase.perftest;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

import com.couchbase.lite.DatabaseConfiguration;

public class MainActivity extends AppCompatActivity {
    Button btnDocPerf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View.OnClickListener clickListener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (view.getId()) {
                    case R.id.btnDocPerf:
                        new DocPerfTest(MainActivity.this,new DatabaseConfiguration( MainActivity.this)).run();
                        break;
                    case R.id.btnTunesPerf:
                        new TunesPerfTest(MainActivity.this,new DatabaseConfiguration(MainActivity.this)).run();
                        break;
                    case R.id.btnQuryPerf:
                        new QueryPerfTest(MainActivity.this,new DatabaseConfiguration(MainActivity.this)).run();
                        break;
                }
            }
        };

        findViewById(R.id.btnDocPerf).setOnClickListener(clickListener);
        findViewById(R.id.btnTunesPerf).setOnClickListener(clickListener);
        findViewById(R.id.btnQuryPerf).setOnClickListener(clickListener);
    }
}
