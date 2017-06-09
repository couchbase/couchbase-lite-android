package com.couchbase.liteservandroid;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.TextView;

import com.couchbase.litecore.*;

import java.io.PrintWriter;
import java.io.StringWriter;

public class MainActivity extends AppCompatActivity implements Constants, LiteCoreServer.StatusCallback {
    //---------------------------------------------
    // Load LiteCore library and its dependencies
    //---------------------------------------------
    static {
        NativeLibraryLoader.load();
    }

    private static final int DEFAULT_LISTEN_PORT = 5984;
    private static final String LISTEN_PORT_PARAM_NAME = "listen_port";

    private LiteCoreServer server = null;
    TextView textView;

    public MainActivity() {
        this.server = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = (TextView)findViewById(R.id.tvMessage);
        server = new LiteCoreServer(this, getListenPort(), this);
        try {
            server.start();
        } catch (LiteCoreException e) {
            Log.e(TAG, "Error in LiteCoreServer.start(): " + e);
            textView.append(e.getMessage()+"\n");

            StringWriter writer = new StringWriter();
            e.printStackTrace(new PrintWriter(writer));
            textView.append(writer.toString());
        }
    }

    @Override
    protected void onDestroy() {
        if (server != null) {
            server.stop();
            server = null;
        }

        super.onDestroy();
    }

    @Override
    public void update(String message) {
        textView.append(message + "\n");
    }

    private int getListenPort() {
        return getIntent().getIntExtra(LISTEN_PORT_PARAM_NAME, DEFAULT_LISTEN_PORT);
    }
}
