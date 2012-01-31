package com.couchbase.touchdb.testapp;

import java.io.IOException;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.listener.TDListener;

public class TestAppActivity extends Activity {

    public static final String TAG = "TestAppActivity";
    private TDListener listener;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        String filesDir = getFilesDir().getAbsolutePath();
        TDServer server;
        try {
            server = new TDServer(filesDir);
            listener = new TDListener(server, 8888);
            listener.start();
        } catch (IOException e) {
            Log.e(TAG, "Unable to create TDServer", e);
        }

    }
}