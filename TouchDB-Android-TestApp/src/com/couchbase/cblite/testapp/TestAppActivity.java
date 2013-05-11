package com.couchbase.cblite.testapp;

import java.io.IOException;

import org.ektorp.CouchDbInstance;
import org.ektorp.ReplicationCommand;
import org.ektorp.impl.StdCouchDbInstance;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;

import com.couchbase.cblite.CBLServer;
import com.couchbase.cblite.ektorp.CBLiteHttpClient;
import com.couchbase.cblite.listener.CBLListener;
import com.couchbase.cblite.router.CBLURLStreamHandlerFactory;
import com.couchbase.cblite.testapp.R;

public class TestAppActivity extends Activity {

    public static final String TAG = "TestAppActivity";
    private CBLListener listener;
    private CBLServer server;

    {
        CBLURLStreamHandlerFactory.registerSelfIgnoreError();
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                .detectDiskReads().detectDiskWrites().detectNetwork() // or
                                                                      // .detectAll()
                                                                      // for all
                                                                      // detectable
                                                                      // problems
                .penaltyLog().build());

        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                // .detectLeakedClosableObjects()
                .penaltyLog().penaltyDeath().build());

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        String filesDir = getFilesDir().getAbsolutePath();
        try {
            server = new CBLServer(filesDir);
            listener = new CBLListener(server, 8888);
            listener.start();
        } catch (IOException e) {
            Log.e(TAG, "Unable to create CBLServer", e);
        }

        CBLiteHttpClient client = new CBLiteHttpClient(server);
        CouchDbInstance instance = new StdCouchDbInstance(client);
        instance.createConnector("tutorial", true);

        ReplicationCommand pull = new ReplicationCommand.Builder()
                .source("http://10.17.44.237:5984/itdashboard").target("tutorial")
                .continuous(false).build();

        instance.replicate(pull);
    }

    @Override
    protected void onDestroy() {
        AsyncTask<Void, Void, Void> closeServer = new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... params) {
                if (server != null) {
                    server.close();
                }
                return null;
            };
        };
        closeServer.execute();

        super.onDestroy();
    }
}