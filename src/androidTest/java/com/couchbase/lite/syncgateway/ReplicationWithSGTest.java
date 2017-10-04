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
package com.couchbase.lite.syncgateway;

import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.TransactionalTask;
import com.couchbase.lite.UnsavedRevision;
import com.couchbase.lite.auth.Authenticator;
import com.couchbase.lite.auth.AuthenticatorFactory;
import com.couchbase.lite.auth.MemTokenStore;
import com.couchbase.lite.auth.OIDCLoginCallback;
import com.couchbase.lite.auth.OIDCLoginContinuation;
import com.couchbase.lite.auth.OpenIDConnectAuthorizer;
import com.couchbase.lite.auth.TokenStore;
import com.couchbase.lite.auth.TokenStoreFactory;
import com.couchbase.lite.replicator.RemoteFormRequest;
import com.couchbase.lite.replicator.RemoteRequest;
import com.couchbase.lite.replicator.RemoteRequestCompletion;
import com.couchbase.lite.replicator.RemoteRequestResponseException;
import com.couchbase.lite.replicator.Replication;
import com.couchbase.lite.support.CouchbaseLiteHttpClientFactory;
import com.couchbase.lite.support.PersistentCookieJar;
import com.couchbase.lite.util.Log;

import junit.framework.Assert;

import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.Response;

/**
 * Replicaton_Tests.m
 * <p/>
 * Steps to run this test
 * 1. Run sync gateway with assets/configs/cbl_unit_tests.json
 * 2. In assets/test.properties
 * set "true" for syncgatewayTestsEnabled
 * set "Sync Gateway's IP address" for replicationServer
 */
public class ReplicationWithSGTest extends LiteTestCaseWithDB {
    public static final String TAG = "AuthFailureTest";

    @Override
    protected void setUp() throws Exception {
        if (!syncgatewayTestsEnabled())
            return;

        super.setUp();
    }

    public void test19_Auth_Failure() throws Exception {
        if (!syncgatewayTestsEnabled())
            return;

        URL remoteDbURL = getRemoteTestDBURL("cbl_auth_test");
        assertNotNull(remoteDbURL);

        Replication repl = database.createPullReplication(remoteDbURL);
        repl.setAuthenticator(AuthenticatorFactory.createBasicAuthenticator("wrong", "wrong"));
        runReplication(repl);
        Throwable error = repl.getLastError();
        assertNotNull(error);
        assertTrue(error instanceof RemoteRequestResponseException);
        RemoteRequestResponseException rrre = (RemoteRequestResponseException) error;
        assertEquals(401, rrre.getCode());

        Map challenge = (Map) rrre.getUserInfo().get("AuthChallenge");
        assertEquals("Basic", challenge.get("Scheme"));
        assertEquals("Couchbase Sync Gateway", challenge.get("realm"));

        // TODO:
        // OAuthAuthenticator is not implemented.
    }

    // Creates a doc with a very deep revision history and pushes the entire history to the server.
    // Then pulls it into another database.
    public void test25_DeepRevTree() throws Exception {
        if (!syncgatewayTestsEnabled())
            return;

        final int kNumRevisions = 2000;

        URL remoteDbURL = getRemoteTestDBURL("db");
        assertNotNull(remoteDbURL);

        Replication push = database.createPushReplication(remoteDbURL);

        final Document doc = database.getDocument("deep");
        final AtomicInteger numRevisions = new AtomicInteger(0);
        for (; numRevisions.get() < kNumRevisions; ) {
            database.runInTransaction(new TransactionalTask() {
                @Override
                public boolean run() {
                    // Have to push the doc periodically, to make sure the server gets the whole
                    // history, since CBL will only remember the latest 20 revisions.
                    int batchSize = Math.min(database.getMaxRevTreeDepth() - 1, kNumRevisions - numRevisions.get());
                    Log.i(TAG, String.format(Locale.ENGLISH, "Adding revisions %d -- %d ...", numRevisions.get() + 1, numRevisions.get() + batchSize));
                    try {
                        for (int i = 0; i < batchSize; ++i) {
                            doc.update(new Document.DocumentUpdater() {
                                @Override
                                public boolean update(UnsavedRevision newRevision) {
                                    Map<String, Object> properties = newRevision.getUserProperties();
                                    properties.put("counter", numRevisions.addAndGet(1));
                                    newRevision.setUserProperties(properties);
                                    return true;
                                }
                            });
                        }
                    } catch (CouchbaseLiteException e) {
                        Log.e(TAG, "Error in Document.update()", e);
                        Assert.fail("Error in Document.update()");
                    }
                    return true;
                }
            });
            Log.i(TAG, "Pushing ...");
            runReplication(push);
        }

        Log.i(TAG, "\n\n$$$$$$$$$$ PULLING TO DB2 $$$$$$$$$$");

        // Now create a second database and pull the remote db into it:
        Database db2 = manager.getDatabase("prepopdb");
        assertNotNull(db2);
        Replication pull = db2.createPullReplication(remoteDbURL);
        runReplication(pull);

        Document doc2 = db2.getDocument("deep");
        assertEquals(db2.getMaxRevTreeDepth(), doc2.getRevisionHistory().size());
        assertEquals(1, doc2.getConflictingRevisions().size());

        Log.i(TAG, "\n\n$$$$$$$$$$ PUSHING 1 DOC FROM DB $$$$$$$$$$");

        // Now add a revision to the doc, push, and pull into db2:
        doc.update(new Document.DocumentUpdater() {
            @Override
            public boolean update(UnsavedRevision newRevision) {
                Map<String, Object> properties = newRevision.getUserProperties();
                properties.put("counter", numRevisions.addAndGet(1));
                newRevision.setUserProperties(properties);
                return true;
            }
        });
        runReplication(push);

        Log.i(TAG, "\n\n$$$$$$$$$$ PULLING 1 DOC INTO DB2 $$$$$$$$$$");
        runReplication(pull);
        assertEquals(db2.getMaxRevTreeDepth(), doc2.getRevisionHistory().size());
        assertEquals(1, doc2.getConflictingRevisions().size());
    }

    // #pragma mark - OPENID CONNECT:

    public void test26_OpenIDConnectAuth() throws Exception {
        _test26_OpenIDConnectAuth(TokenStoreFactory.build(getTestContext("db")));
    }

    public void test26_OpenIDConnectAuthMem() throws Exception {
        _test26_OpenIDConnectAuth(new MemTokenStore());
    }

    public void _test26_OpenIDConnectAuth(TokenStore tokenStore) throws Exception {
        if (!syncgatewayTestsEnabled() || !isSQLiteDB())
            return;

        final URL remoteDbURL = getRemoteTestDBURL("openid_db");
        assertNotNull(remoteDbURL);

        OpenIDConnectAuthorizer.forgetIDTokensForServer(remoteDbURL, tokenStore);

        Authenticator auth = AuthenticatorFactory.createOpenIDConnectAuthenticator(
            new OIDCLoginCallback() {
                @Override
                public void callback(URL login, URL authBase, OIDCLoginContinuation cont) {
                    assertValidOIDCLogin(login, authBase, remoteDbURL);
                    // Fake a form submission to the OIDC test provider, to get an auth URL redirect:
                    URL authURL = loginToOIDCTestProvider(remoteDbURL);
                    assertNotNull(authURL);
                    Log.e(TAG, "**** Callback handing control back to authenticator...");
                    cont.callback(authURL, null);
                }
            }, tokenStore);

        Throwable authError = pullWithOIDCAuth(auth, "pupshaw");
        assertNull(authError);

        // Now try again; this should use the ID token from the keychain and/or a session cookie:
        Log.v(TAG, "**** Second replication...");
        final AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        auth = AuthenticatorFactory.createOpenIDConnectAuthenticator(
            new OIDCLoginCallback() {
                @Override
                public void callback(URL login, URL authBase, OIDCLoginContinuation cont) {
                    assertValidOIDCLogin(login, authBase, remoteDbURL);
                    assertFalse(callbackInvoked.get());
                    callbackInvoked.set(true);
                    cont.callback(null, null); // cancel
                }
            }, tokenStore);
        authError = pullWithOIDCAuth(auth, "pupshaw");
        assertNull(authError);
        assertFalse(callbackInvoked.get());
    }

    public void test27_OpenIDConnectAuth_ExpiredIDToken() throws Exception {
        _test27_OpenIDConnectAuth_ExpiredIDToken(TokenStoreFactory.build(getTestContext("db")));
    }

    public void test27_OpenIDConnectAuth_ExpiredIDTokenMem() throws Exception {
        _test27_OpenIDConnectAuth_ExpiredIDToken(new MemTokenStore());
    }

    public void _test27_OpenIDConnectAuth_ExpiredIDToken(TokenStore tokenStore) throws Exception {
        if (!syncgatewayTestsEnabled() || !isSQLiteDB())
            return;

        final URL remoteDbURL = getRemoteTestDBURL("openid_db");
        assertNotNull(remoteDbURL);

        OpenIDConnectAuthorizer.forgetIDTokensForServer(remoteDbURL, tokenStore);

        final AtomicBoolean callbackInvoked = new AtomicBoolean(false);
        Authenticator auth = AuthenticatorFactory.createOpenIDConnectAuthenticator(
                new OIDCLoginCallback() {
                    @Override
                    public void callback(URL login, URL authBase,
                                         OIDCLoginContinuation cont) {
                        assertValidOIDCLogin(login, authBase, remoteDbURL);
                        assertFalse(callbackInvoked.get());
                        callbackInvoked.set(true);
                        cont.callback(null, null); // cancel
                    }
                }, tokenStore);

        // Set bogus ID and refresh tokens, so first the session check will fail, then the attempt
        // to refresh the ID token will fail. Finally the callback above will be called.
        ((OpenIDConnectAuthorizer) auth).setIDToken("BOGUS_ID");
        ((OpenIDConnectAuthorizer) auth).setRefreshToken("BOGUS_REFRESH");

        Throwable authError = pullWithOIDCAuth(auth, null);
        assertTrue(callbackInvoked.get());
        assertTrue(authError instanceof RemoteRequestResponseException);
        RemoteRequestResponseException rrre = (RemoteRequestResponseException) authError;
        assertEquals(RemoteRequestResponseException.USER_DENIED_AUTH, rrre.getCode());
    }

    // Use the CBLRestLogin class to log in with OIDC without using a replication
    public void test28_OIDCLoginWithoutReplicator() throws Exception {
        // TODO:
        // RemoteLogin is not implemented yet.
    }

    private URL loginToOIDCTestProvider(URL remoteDbURL) {
        // Fake a form submission to the OIDC test provider, to get an auth URL redirect:
        try {
            PersistentCookieJar cookieStore = database.getPersistentCookieStore();
            CouchbaseLiteHttpClientFactory factory = new CouchbaseLiteHttpClientFactory(cookieStore);
            // Don't redirect
            factory.setFollowRedirects(false);

            URL formURL = new URL(remoteDbURL.toExternalForm() +
                    "/_oidc_testing/authenticate?client_id=CLIENTID&redirect_uri=http%3A%2F%2F" +
                    getSyncGatewayHost() +
                    "%3A4984%2Fopenid_db%2F_oidc_callback&response_type=code&scope=openid+email&state=");
            Map<String, String> formData = new HashMap<String, String>();
            formData.put("username", "pupshaw");
            formData.put("authenticated", "true");
            final Map<String, Object> results = new HashMap<String, Object>();
            RemoteRequest rq = new RemoteFormRequest(factory, "POST", formURL, false, formData, null,
                new RemoteRequestCompletion() {
                    @Override
                    public void onCompletion(RemoteRequest remoteRequest, Response httpResponse, Object result, Throwable error) {
                        results.put("response", httpResponse);
                        results.put("result", result);
                        results.put("error", error);
                    }
                });
            rq.setAuthenticator(null);
            Thread t = new Thread(rq);
            t.start();
            t.join();

            assertTrue(results.containsKey("error"));
            assertTrue(results.get("error") instanceof RemoteRequestResponseException);
            RemoteRequestResponseException rrre = (RemoteRequestResponseException) results.get("error");
            assertEquals(302, rrre.getCode());

            Response response = (Response) results.get("response");
            String authURLStr = response.header("Location");
            Log.e(TAG, "Redirected to: %s", authURLStr);
            assertNotNull(authURLStr);
            return new URL(authURLStr);
        } catch (Exception ex) {
            Log.e(TAG, "Error in loginToOIDCTestProvider() remoteDbURL=<%s>", ex, remoteDbURL);
            return null;
        }
    }

    private void assertValidOIDCLogin(URL login, URL authBase, URL remoteDbURL) {
        Log.w(TAG, "*** Login callback invoked with login URL: <%s>, authBase: <%s>", login, authBase);
        assertNotNull(login);
        assertEquals(remoteDbURL.getHost(), login.getHost());
        assertEquals(remoteDbURL.getPort(), login.getPort());
        assertEquals(remoteDbURL.getPath() + "/_oidc_testing/authorize", login.getPath());

        assertNotNull(authBase);
        assertEquals(remoteDbURL.getHost(), authBase.getHost());
        assertEquals(remoteDbURL.getPort(), authBase.getPort());
        assertEquals(remoteDbURL.getPath() + "/_oidc_callback", authBase.getPath());
    }

    private Throwable pullWithOIDCAuth(Authenticator auth, String username)
            throws Exception {
        URL remoteDbURL = getRemoteTestDBURL("openid_db");
        if (remoteDbURL == null)
            return null;
        Replication repl = database.createPullReplication(remoteDbURL);
        repl.setAuthenticator(auth);
        runReplication(repl);
        if(username != null && repl.getLastError() == null)
            // SG namespaces the username by prefixing it with the hash of
            // the identity provider's registered name (given in the SG config file.)
            assertTrue(repl.getUsername().endsWith(username));
        return repl.getLastError();
    }
}