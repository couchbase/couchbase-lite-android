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
package com.couchbase.lite.support;

import com.couchbase.lite.LiteTestCaseWithDB;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import okhttp3.Cookie;

public class PersistentCookieStoreTest extends LiteTestCaseWithDB {

    // https://github.com/couchbase/couchbase-lite-java-core/issues/964
    public void testClear() throws Exception {
        PersistentCookieJar cookieStore = new PersistentCookieJar(database);
        cookieStore.clear();
    }

    public void testEncodeDecodeCookie() throws Exception {
        String cookieName = "foo";
        String cookieVal = "bar";
        String cookiePath = "/baz";
        String cookieDomain = "foo.com";

        // expiration date - 1 day from now
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        int numDaysToAdd = 1;
        cal.add(Calendar.DATE, numDaysToAdd);
        long expirationDate = cal.getTimeInMillis();

        Cookie cookie = new Cookie.Builder()
                .name(cookieName)
                .value(cookieVal)
                .expiresAt(expirationDate)
                .domain(cookieDomain)
                .path(cookiePath)
                .build();

        String encodedCookie = new SerializableCookie().encode(cookie);
        Cookie fetchedCookie = new SerializableCookie().decode(encodedCookie);

        assertEquals(cookieName, fetchedCookie.name());
        assertEquals(cookieVal, fetchedCookie.value());
        assertEquals(expirationDate, fetchedCookie.expiresAt());
        assertEquals(cookiePath, fetchedCookie.path());
        assertEquals(cookieDomain, fetchedCookie.domain());
    }

    public void testPersistentCookiestore() throws Exception {
        ClearableCookieJar cookieJar = new PersistentCookieJar(database);
        assertEquals(0, cookieJar.loadForRequest(null).size());

        String cookieName = "foo";
        String cookieVal = "bar";
        String cookiePath = "/baz";
        String cookieDomain = "foo.com";

        // expiration date - 1 day from now
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        int numDaysToAdd = 1;
        cal.add(Calendar.DATE, numDaysToAdd);
        long expirationDate = cal.getTimeInMillis();

        Cookie cookie = new Cookie.Builder()
                .name(cookieName)
                .value(cookieVal)
                .expiresAt(expirationDate)
                .domain(cookieDomain)
                .path(cookiePath)
                .build();

        // TODO: HttpUrl parameter should be revisited.
        cookieJar.saveFromResponse(null, Arrays.asList(cookie));

        assertEquals(1, cookieJar.loadForRequest(null).size());
        List<Cookie> fetchedCookies = cookieJar.loadForRequest(null);
        Cookie fetchedCookie = fetchedCookies.get(0);
        assertEquals(cookieName, fetchedCookie.name());
        assertEquals(cookieVal, fetchedCookie.value());
        assertEquals(expirationDate, fetchedCookie.expiresAt());
        assertEquals(cookiePath, fetchedCookie.path());
        assertEquals(cookieDomain, fetchedCookie.domain());
    }
}
