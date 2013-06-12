package com.couchbase.cblite.testapp.ektorp.tests;

import android.util.Log;

import com.couchbase.cblite.ektorp.CBLiteHttpClient;

import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.DbAccessException;
import org.ektorp.android.util.ChangesFeedAsyncTask;
import org.ektorp.android.util.EktorpAsyncTask;
import org.ektorp.changes.ChangesCommand;
import org.ektorp.changes.DocumentChange;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class EktorpAsyncTaskTest extends CBLiteEktorpTestCase {

    // Regression test for https://github.com/couchbaselabs/TouchDB-Android/issues/71
    public void testWedgedAsyncTasks() {

        final CountDownLatch doneSignal = new CountDownLatch(1);
        HttpClient httpClient = new CBLiteHttpClient(server);
        CouchDbInstance dbInstance = new StdCouchDbInstance(httpClient);

        // create a local database
        CouchDbConnector couchDbConnector = dbInstance.createConnector(DEFAULT_TEST_DB, true);

        //create an ansyc task to get updates
        ChangesCommand changesCmd = new ChangesCommand.Builder().since(0)
                .includeDocs(false)
                .continuous(true)
                .heartbeat(5000)
                .build();

        ChangesFeedAsyncTask couchChangesAsyncTask = new ChangesFeedAsyncTask(couchDbConnector, changesCmd) {
            @Override
            protected void handleDocumentChange(DocumentChange documentChange) {
                Log.d(TAG, "handleDocumentChange called");
            }
        };
        couchChangesAsyncTask.execute();

        EktorpAsyncTask asyncTask = new EktorpAsyncTask() {
            @Override
            protected void doInBackground() {
                Log.d(TAG, "doInBackground() called");
                doneSignal.countDown();
            }
        };
        asyncTask.execute();

        try {
            boolean wasSignalled = doneSignal.await(500, TimeUnit.MILLISECONDS);
            assertTrue("The async task was wedged by the changes feed task", wasSignalled);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

}
