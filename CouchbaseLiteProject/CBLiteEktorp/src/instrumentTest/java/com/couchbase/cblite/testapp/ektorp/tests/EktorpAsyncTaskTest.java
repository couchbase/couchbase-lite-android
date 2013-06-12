package com.couchbase.cblite.testapp.ektorp.tests;

import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLView;
import com.couchbase.cblite.CBLViewMapBlock;
import com.couchbase.cblite.CBLViewMapEmitBlock;
import com.couchbase.cblite.ektorp.CBLiteHttpClient;

import junit.framework.Assert;

import org.ektorp.CouchDbConnector;
import org.ektorp.CouchDbInstance;
import org.ektorp.DbAccessException;
import org.ektorp.ViewQuery;
import org.ektorp.android.util.ChangesFeedAsyncTask;
import org.ektorp.android.util.CouchbaseViewListAdapter;
import org.ektorp.android.util.EktorpAsyncTask;
import org.ektorp.changes.ChangesCommand;
import org.ektorp.changes.DocumentChange;
import org.ektorp.http.HttpClient;
import org.ektorp.impl.StdCouchDbInstance;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class EktorpAsyncTaskTest extends CBLiteEktorpTestCase {

    public static final String dDocName = "ddoc";
    public static final String dDocId = "_design/" + dDocName;
    public static final String viewName = "aview";

    // Regression test for https://github.com/couchbaselabs/TouchDB-Android/issues/71
    public void testWedgedListAdaptor() {

        final CountDownLatch doneSignal = new CountDownLatch(1);

        HttpClient httpClient = new CBLiteHttpClient(server);
        CouchDbInstance dbInstance = new StdCouchDbInstance(httpClient);

        // create a local database
        CouchDbConnector couchDbConnector = dbInstance.createConnector(DEFAULT_TEST_DB, true);

        createView(database);
        ViewQuery viewQuery = new ViewQuery().designDocId(dDocId).viewName(viewName);

        // create a list adapter with follow=true.  will follow the changes feed in async task
        boolean follow = true;
        new CouchbaseViewListAdapter(couchDbConnector, viewQuery, follow) {

            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                return null;
            }

        };

        // wait until list adaptor starts up changes listener task
        // unfortunately, there is no way to hook into when the couchlistadapter's internal
        // async task actually starts reading from the changes feed, so using a timer
        // is the only way to
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // create a dummy async task.  if things are working ok, the doInBackground()
        // will get called back.  OTOH if async tasks are wedged, it will never get called back.
        EktorpAsyncTask asyncTask = new EktorpAsyncTask() {
            @Override
            protected void doInBackground() {
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
        couchChangesAsyncTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void)null);  // fix by using thread pool executor

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

    public static CBLView createView(CBLDatabase db) {
        CBLView view = db.getViewNamed(String.format("%s/%s", dDocName, viewName));
        view.setMapReduceBlocks(new CBLViewMapBlock() {

            @Override
            public void map(Map<String, Object> document, CBLViewMapEmitBlock emitter) {
                Assert.assertNotNull(document.get("_id"));
                Assert.assertNotNull(document.get("_rev"));
                if(document.get("key") != null) {
                    emitter.emit(document.get("key"), null);
                }
            }
        }, null, "1");
        return view;
    }


}
