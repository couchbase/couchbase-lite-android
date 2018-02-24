//
// CBLWebSocketUtils.java
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
package com.couchbase.lite.internal.replicator;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.X509TrustManager;

public class CBLWebSocketUtils {
    public static void setTrustManager(X509TrustManager trustManager) {
        CBLWebSocket.setTrustManager(trustManager);
    }

    public static void setHostnameVerifier(HostnameVerifier hostnameVerifier) {
        CBLWebSocket.setHostnameVerifier(hostnameVerifier);
    }
}
