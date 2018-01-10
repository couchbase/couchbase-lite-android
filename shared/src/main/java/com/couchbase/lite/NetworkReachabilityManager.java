/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package com.couchbase.lite;

import java.util.HashSet;
import java.util.Set;

/**
 * This uses system api (on Android, uses the Context) to listen for network reachability
 * change events and notifies all NetworkReachabilityListeners that have registered themselves.
 * (an example of a NetworkReachabilityListeners is a Replicator that wants to pause when
 * it's been detected that the network is not reachable)
 */
abstract class NetworkReachabilityManager {

    private Object lock = new Object();

    private Set<NetworkReachabilityListener> listeners = new HashSet<>();

    /**
     * Add Network Reachability Listener
     */
    void addNetworkReachabilityListener(NetworkReachabilityListener listener) {
        synchronized (lock) {
            listeners.add(listener);
            if (listeners.size() == 1)
                startListening();
        }
    }

    /**
     * Remove Network Reachability Listener
     */
    void removeNetworkReachabilityListener(NetworkReachabilityListener listener) {
        synchronized (lock) {
            listeners.remove(listener);
            if (listeners.size() == 0)
                stopListening();
        }
    }

    /**
     * Notify listeners that the network is now reachable
     */
    void notifyListenersNetworkReachable() {
        synchronized (lock) {
            for (NetworkReachabilityListener listener : listeners)
                listener.networkReachable();
        }
    }

    /**
     * Notify listeners that the network is now unreachable
     */
    void notifyListenersNetworkUneachable() {
        synchronized (lock) {
            for (NetworkReachabilityListener listener : listeners)
                listener.networkUnreachable();
        }
    }

    /**
     * This method starts listening for network connectivity state changes.
     */
    abstract void startListening();

    /**
     * This method stops this class from listening for network changes.
     */
    abstract void stopListening();
}