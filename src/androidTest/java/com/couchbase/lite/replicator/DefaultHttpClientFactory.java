//
// Copyright (c) 2016 Couchbase, Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
// except in compliance with the License. You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under the
// License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions
// and limitations under the License.
//
package com.couchbase.lite.replicator;

import com.couchbase.lite.support.HttpClientFactory;

import java.net.URL;
import java.util.List;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.OkHttpClient;

/**
 * Created by hideki on 5/17/16.
 */
public class DefaultHttpClientFactory implements HttpClientFactory {

    @Override
    public OkHttpClient getOkHttpClient() {
        return null;
    }

    @Override
    public void addCookies(List<Cookie> cookies) {

    }

    @Override
    public void deleteCookie(String name) {

    }

    @Override
    public void deleteCookie(URL url) {

    }

    @Override
    public void resetCookieStore() {

    }

    @Override
    public CookieJar getCookieStore() {
        return null;
    }
}
