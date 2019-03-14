//
// URLEndpoint.java
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

import android.support.annotation.NonNull;

import java.net.URI;
import java.util.Locale;


/**
 * URL based replication target endpoint
 */
public final class URLEndpoint implements Endpoint {
    //---------------------------------------------
    // Constant variables
    //---------------------------------------------
    static final String kURLEndpointScheme = "ws";
    static final String kURLEndpointTLSScheme = "wss";

    //---------------------------------------------
    // Member variables
    //---------------------------------------------
    private final URI url;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    /**
     * Constructor with the url. The supported URL schemes
     * are ws and wss for transferring data over a secure channel.
     *
     * @param url The url.
     */
    public URLEndpoint(@NonNull URI url) {
        if (url == null) { throw new IllegalArgumentException("url cannot be null."); }

        final String scheme = url.getScheme();
        if (!(kURLEndpointScheme.equals(scheme) || kURLEndpointTLSScheme.equals(scheme))) {
            throw new IllegalArgumentException(
                String.format(Locale.ENGLISH,
                    "Invalid scheme for URLEndpoint url (%s); must be either %s or %s.",
                    scheme, kURLEndpointScheme, kURLEndpointTLSScheme));
        }

        final String userInfo = url.getUserInfo();
        if (userInfo != null && userInfo.split(":").length == 2) {
            throw new IllegalArgumentException(
                "Embedded credentials in a URL (username:password@url) are not allowed; use the BasicAuthenticator "
                    + "class instead.");
        }

        this.url = url;
    }

    @NonNull
    @Override
    public String toString() {
        return "URLEndpoint{" +
            "url=" + url +
            '}';
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Returns the url.
     */
    @NonNull
    public URI getURL() {
        return url;
    }
}
