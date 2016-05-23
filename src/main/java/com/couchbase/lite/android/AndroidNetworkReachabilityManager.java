/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite.android;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.couchbase.lite.NetworkReachabilityManager;
import com.couchbase.lite.util.Log;

public class AndroidNetworkReachabilityManager extends NetworkReachabilityManager {

    private boolean listening;
    private android.content.Context wrappedContext;
    private ConnectivityBroadcastReceiver receiver;

    public AndroidNetworkReachabilityManager(AndroidContext androidContextcontext) {
        this.listening = false;
        this.wrappedContext = androidContextcontext.getWrappedContext();
        this.receiver = new ConnectivityBroadcastReceiver();
    }

    public synchronized void startListening() {
        if (!listening) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
            Log.v(Log.TAG_SYNC, "%s: startListening() registering %s with context %s",
                    this, receiver, wrappedContext);
            wrappedContext.registerReceiver(receiver, filter);
            listening = true;
        }
    }

    public synchronized void stopListening() {
        if (listening) {
            try {
                Log.v(Log.TAG_SYNC, "%s: stopListening() unregistering %s with context %s",
                        this, receiver, wrappedContext);
                wrappedContext.unregisterReceiver(receiver);
            } catch (Exception e) {
                Log.e(Log.TAG_SYNC,
                        "%s: stopListening() exception unregistering %s with context %s",
                        e, this, receiver, wrappedContext);
            }
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

            boolean bOnline = isOnline(context);
            Log.v(Log.TAG_SYNC, "BroadcastReceiver.onReceive() bOnline=" + bOnline);

            if (bOnline) {
                notifyListenersNetworkReachable();
            } else {
                notifyListenersNetworkUneachable();
            }
        }
    }

    public boolean isOnline() {
        return isOnline(wrappedContext);
    }

    private boolean isOnline(android.content.Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(
                android.content.Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnected();
    }
}
