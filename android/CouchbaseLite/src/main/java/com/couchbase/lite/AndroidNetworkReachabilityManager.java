//
// AndroidNetworkReachabilityManager.java
//
// Copyright (c) 2017 Couchbase, Inc All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
package com.couchbase.lite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.couchbase.lite.internal.support.Log;

/**
 * NOTE: https://developer.android.com/training/basics/network-ops/managing.html
 */
final class AndroidNetworkReachabilityManager extends NetworkReachabilityManager {

    private static final String TAG = Log.SYNC;

    private boolean listening;
    private Context context;
    private NetworkReceiver receiver;

    AndroidNetworkReachabilityManager(Context context) {
        this.listening = false;
        this.context = context;
        this.receiver = new NetworkReceiver();
    }

    /**
     * NOTE: startListening() method is called from addNetworkReachabilityListener() which is
     * synchronized. So this method is not necessary to be synchronized.
     */
    @Override
    void startListening() {
        if (!listening) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            Log.v(TAG, "%s: startListening() registering %s with context %s", this, receiver, context);
            context.registerReceiver(receiver, filter);
            listening = true;
        }
    }

    /**
     * NOTE: stopListening() method is called from removeNetworkReachabilityListener() which is
     * synchronized. So this method is not necessary to be synchronized.
     */
    @Override
    void stopListening() {
        if (listening) {
            try {
                Log.v(TAG, "%s: stopListening() unregistering %s with context %s", this, receiver, context);
                context.unregisterReceiver(receiver);
            } catch (Exception e) {
                Log.e(TAG, "%s: stopListening() exception unregistering %s with context %s", e, this, receiver, context);
            }
            listening = false;
        }
    }

    private boolean isOnline(Context ctx) {
        ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    private class NetworkReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION) || listening == false)
                return;
            boolean bOnline = isOnline(context);
            Log.v(TAG, "NetworkReceiver.onReceive() Online -> " + bOnline);
            if (bOnline)
                notifyListenersNetworkReachable();
            else
                notifyListenersNetworkUneachable();
        }
    }
}
