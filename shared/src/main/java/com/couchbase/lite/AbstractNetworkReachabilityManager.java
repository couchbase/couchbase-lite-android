//
// AbstractNetworkReachabilityManager.java
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

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


/**
 * This uses system api (on Android, uses the Context) to listen for network reachability
 * change events and notifies all NetworkReachabilityListeners that have registered themselves.
 * (an example of a NetworkReachabilityListeners is a Replicator that wants to pause when
 * it's been detected that the network is not reachable)
 */
abstract class AbstractNetworkReachabilityManager {

    private final Set<NetworkReachabilityListener> listeners
        = Collections.synchronizedSet(new HashSet<>());

    /**
     * Add Network Reachability Listener
     */
    void addNetworkReachabilityListener(NetworkReachabilityListener listener) {
        synchronized (listeners) {
            listeners.add(listener);
            if (listeners.size() == 1) { startListening(); }
        }
    }

    /**
     * Remove Network Reachability Listener
     */
    void removeNetworkReachabilityListener(NetworkReachabilityListener listener) {
        synchronized (listeners) {
            listeners.remove(listener);
            if (listeners.size() == 0) { stopListening(); }
        }
    }

    /**
     * Notify listeners that the network is now reachable
     */
    void notifyListenersNetworkReachable() {
        // NOTE: synchronized(listener) causes deadlock with listeners and Replicator.lock.
        final Set<NetworkReachabilityListener> copy = new HashSet<>(listeners);
        for (NetworkReachabilityListener listener : copy) {
            if (listener != null) { listener.networkReachable(); }
        }
    }

    /**
     * Notify listeners that the network is now unreachable
     */
    void notifyListenersNetworkUneachable() {
        // NOTE: synchronized(listener) causes deadlock with listeners and Replicator.lock.
        final Set<NetworkReachabilityListener> copy = new HashSet<>(listeners);
        for (NetworkReachabilityListener listener : copy) {
            if (listener != null) { listener.networkUnreachable(); }
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
