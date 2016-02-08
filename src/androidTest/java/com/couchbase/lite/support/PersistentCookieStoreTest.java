package com.couchbase.lite.support;

import com.couchbase.lite.LiteTestCaseWithDB;

import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookie;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class PersistentCookieStoreTest extends LiteTestCaseWithDB {

    // https://github.com/couchbase/couchbase-lite-java-core/issues/964
    public void testClear() throws Exception {
        PersistentCookieStore cookieStore = new PersistentCookieStore(database);
        cookieStore.clear();
    }

    public void testEncodeDecodeCookie() throws Exception {
        PersistentCookieStore cookieStore = new PersistentCookieStore(database);

        String cookieName = "foo";
        String cookieVal = "bar";
        boolean isSecure = false;
        String cookiePath = "baz";
        String cookieDomain = "foo.com";

        // expiration date - 1 day from now
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        int numDaysToAdd = 1;
        cal.add(Calendar.DATE, numDaysToAdd);
        Date expirationDate = cal.getTime();

        BasicClientCookie cookie = new BasicClientCookie(cookieName, cookieVal);
        cookie.setExpiryDate(expirationDate);
        cookie.setSecure(isSecure);
        cookie.setDomain(cookieDomain);
        cookie.setPath(cookiePath);

        String encodedCookie = cookieStore.encodeCookie(new SerializableCookie(cookie));
        Cookie fetchedCookie = cookieStore.decodeCookie(encodedCookie);

        assertEquals(cookieName, fetchedCookie.getName());
        assertEquals(cookieVal, fetchedCookie.getValue());
        assertEquals(expirationDate, fetchedCookie.getExpiryDate());
        assertEquals(cookiePath, fetchedCookie.getPath());
        assertEquals(cookieDomain, fetchedCookie.getDomain());
    }

    public void testPersistentCookiestore() throws Exception {
        CookieStore cookieStore = new PersistentCookieStore(database);
        assertEquals(0, cookieStore.getCookies().size());

        String cookieName = "foo";
        String cookieVal = "bar";
        boolean isSecure = false;
        String cookiePath = "baz";
        String cookieDomain = "foo.com";

        // expiration date - 1 day from now
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        int numDaysToAdd = 1;
        cal.add(Calendar.DATE, numDaysToAdd);
        Date expirationDate = cal.getTime();

        BasicClientCookie cookie = new BasicClientCookie(cookieName, cookieVal);
        cookie.setExpiryDate(expirationDate);
        cookie.setSecure(isSecure);
        cookie.setDomain(cookieDomain);
        cookie.setPath(cookiePath);

        cookieStore.addCookie(cookie);

        assertEquals(1, cookieStore.getCookies().size());
        List<Cookie> fetchedCookies = cookieStore.getCookies();
        Cookie fetchedCookie = fetchedCookies.get(0);
        assertEquals(cookieName, fetchedCookie.getName());
        assertEquals(cookieVal, fetchedCookie.getValue());
        assertEquals(expirationDate, fetchedCookie.getExpiryDate());
        assertEquals(cookiePath, fetchedCookie.getPath());
        assertEquals(cookieDomain, fetchedCookie.getDomain());
    }
}
