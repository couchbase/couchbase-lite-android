package com.couchbase.lite.android;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;

import com.couchbase.lite.AsyncTask;
import com.couchbase.lite.Context;
import com.couchbase.lite.Database;
import com.couchbase.lite.NetworkReachabilityManager;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.util.Log;

public class AndroidNetworkReachabilityManager extends NetworkReachabilityManager {

    private Context context;
    private boolean listening;
    private android.content.Context wrappedContext;
    private ConnectivityBroadcastReceiver receiver;
    private State state;

    public enum State {
        UNKNOWN,

        /** This state is returned if there is connectivity to any network **/
        CONNECTED,
        /**
         * This state is returned if there is no connectivity to any network. This is set to true
         * under two circumstances:
         * <ul>
         * <li>When connectivity is lost to one network, and there is no other available network to
         * attempt to switch to.</li>
         * <li>When connectivity is lost to one network, and the attempt to switch to another
         * network fails.</li>
         */
        NOT_CONNECTED
    }

    public AndroidNetworkReachabilityManager(AndroidContext context) {
        this.context = context;
        this.wrappedContext = context.getWrappedContext();
        this.receiver = new ConnectivityBroadcastReceiver();
        this.state = State.UNKNOWN;
    }


    public synchronized void startListening() {
        if (!listening) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            Log.v(Log.TAG_SYNC, "%s: startListening() registering %s with context %s", this, receiver, wrappedContext);
            wrappedContext.registerReceiver(receiver, filter);
            listening = true;
        }
    }

    public synchronized void stopListening() {
        if (listening) {
            try {
                Log.v(Log.TAG_SYNC, "%s: stopListening() unregistering %s with context %s", this, receiver, wrappedContext);
                wrappedContext.unregisterReceiver(receiver);
            } catch (Exception e) {
                Log.e(Log.TAG_SYNC, "%s: stopListening() exception unregistering %s with context %s", e, this, receiver, wrappedContext);
            }
            context = null;
            listening = false;
        }
    }

    private class ConnectivityBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            String action = intent.getAction();

            if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION) || listening == false) {
                return;
            }

            boolean noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);

            if (noConnectivity) {
                state = State.NOT_CONNECTED;
            } else {
                state = State.CONNECTED;
            }

            if (state == State.NOT_CONNECTED) {
                notifyListenersNetworkUneachable();
            }

            if (state == State.CONNECTED) {
                notifyListenersNetworkReachable();
            }

        }
    };

}
