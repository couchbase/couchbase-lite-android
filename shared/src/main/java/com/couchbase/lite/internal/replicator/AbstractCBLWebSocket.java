//
// CBLWebSocket.java
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

import android.support.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Authenticator;
import okhttp3.Challenge;
import okhttp3.Credentials;
import okhttp3.Headers;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.internal.tls.CustomHostnameVerifier;
import okio.Buffer;
import okio.ByteString;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.internal.core.C4Constants;
import com.couchbase.lite.internal.core.C4Replicator;
import com.couchbase.lite.internal.core.C4Socket;
import com.couchbase.lite.internal.core.C4WebSocketCloseCode;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.support.Log;


public class AbstractCBLWebSocket extends C4Socket {
    private static final LogDomain TAG = LogDomain.NETWORK;

    /**
     * Workaround to enable both TLS1.1 and TLS1.2 for Android API 16 - 19.
     * When starting to support from API 20, we could remove the workaround.
     */
    private static class TLSSocketFactory extends SSLSocketFactory {
        private SSLSocketFactory delegate;

        TLSSocketFactory(KeyManager[] keyManagers, TrustManager[] trustManagers, SecureRandom secureRandom)
            throws GeneralSecurityException {
            final SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagers, trustManagers, secureRandom);
            delegate = context.getSocketFactory();
        }

        @Override
        public String[] getDefaultCipherSuites() {
            return delegate.getDefaultCipherSuites();
        }

        @Override
        public String[] getSupportedCipherSuites() {
            return delegate.getSupportedCipherSuites();
        }

        @Override
        public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
            return setEnabledProtocols(delegate.createSocket(socket, host, port, autoClose));
        }

        @Override
        public Socket createSocket(String host, int port) throws IOException {
            return setEnabledProtocols(delegate.createSocket(host, port));
        }

        @Override
        public Socket createSocket(String host, int port, InetAddress localHost, int localPort)
            throws IOException {
            return setEnabledProtocols(delegate.createSocket(host, port, localHost, localPort));
        }

        @Override
        public Socket createSocket(InetAddress address, int port) throws IOException {
            return setEnabledProtocols(delegate.createSocket(address, port));
        }

        @Override
        public Socket createSocket(InetAddress inetAddress, int port, InetAddress localAddress, int localPort)
            throws IOException {
            return setEnabledProtocols(delegate.createSocket(inetAddress, port, localAddress, localPort));
        }

        private Socket setEnabledProtocols(Socket socket) {
            if (socket instanceof SSLSocket) {
                ((SSLSocket) socket).setEnabledProtocols(new String[] {"TLSv1", "TLSv1.1", "TLSv1.2"});
            }
            return socket;
        }
    }

    //-------------------------------------------------------------------------
    // Internal class
    //-------------------------------------------------------------------------
    class CBLWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.v(TAG, "WebSocketListener.onOpen() response -> " + response);
            AbstractCBLWebSocket.this.webSocket = webSocket;
            receivedHTTPResponse(response);
            Log.i(TAG, "CBLWebSocket CONNECTED!");
            opened(handle);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Log.v(TAG, "WebSocketListener.onMessage() text -> " + text);
            received(handle, text.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            Log.v(TAG, "WebSocketListener.onMessage() bytes -> " + bytes.hex());
            received(handle, bytes.toByteArray());
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.v(TAG, "WebSocketListener.onClosing() code -> " + code + ", reason -> " + reason);
            closeRequested(handle, code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.v(TAG, "WebSocketListener.onClosed() code -> " + code + ", reason -> " + reason);
            didClose(code, reason);
        }

        // NOTE: from CBLStatus.mm
        // {kCFErrorHTTPConnectionLost,                {POSIXDomain, ECONNRESET}},
        // {kCFURLErrorCannotConnectToHost,            {POSIXDomain, ECONNREFUSED}},
        // {kCFURLErrorNetworkConnectionLost,          {POSIXDomain, ECONNRESET}},

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.w(TAG, "WebSocketListener.onFailure() response -> " + response, t);

            // Invoked when a web socket has been closed due to an error reading from or writing to the
            // network. Both outgoing and incoming messages may have been lost. No further calls to this
            // listener will be made.
            if (response == null) {
                didClose(t);
            }
            else {
                final int httpStatus = response.code();
                if (httpStatus == 101) {
                    didClose(C4WebSocketCloseCode.kWebSocketCloseProtocolError, response.message());
                }
                else {
                    int closeCode = C4WebSocketCloseCode.kWebSocketClosePolicyError;
                    if (httpStatus >= 300 && httpStatus < 1000) { closeCode = httpStatus; }
                    didClose(closeCode, response.message());
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Socket Factory Callbacks
    // ---------------------------------------------------------------------------------------------
    // !! Called by reflection!  Don't change the name.
    @SuppressWarnings({"MethodName", "PMD.MethodNamingConventions"})
    public static void socket_open(
        long socket,
        Object socketFactoryContext,
        String scheme,
        String hostname,
        int port,
        String path,
        byte[] optionsFleece) {
        Log.e(TAG, "CBLWebSocket.socket_open()");

        Map<String, Object> options = null;
        if (optionsFleece != null) { options = FLValue.fromData(optionsFleece).asDict(); }

        // NOTE: OkHttp can not understand blip/blips
        if (scheme.equalsIgnoreCase(C4Replicator.C4_REPLICATOR_SCHEME_2)) { scheme = WEBSOCKET_SCHEME; }
        else if (scheme.equalsIgnoreCase(C4Replicator.C4_REPLICATOR_TLS_SCHEME_2)) {
            scheme = WEBSOCKET_SECURE_CONNECTION_SCHEME;
        }

        final AbstractCBLWebSocket c4sock;
        try {
            c4sock = new CBLWebSocket(socket, scheme, hostname, port, path, options);
        }
        catch (Exception e) {
            Log.e(TAG, "Failed to instantiate C4Socket: " + e);
            e.printStackTrace();
            return;
        }

        reverseLookupTable.put(socket, c4sock);

        c4sock.start();
    }

    private OkHttpClient httpClient;
    private CBLWebSocketListener wsListener;
    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    private URI uri;
    private Map<String, Object> options;

    //-------------------------------------------------------------------------
    // Abstract method implementation
    //-------------------------------------------------------------------------
    private WebSocket webSocket;

    //-------------------------------------------------------------------------
    // constructor
    //-------------------------------------------------------------------------
    protected AbstractCBLWebSocket(
        long handle,
        String scheme,
        String hostname,
        int port,
        String path,
        Map<String, Object> options)
        throws GeneralSecurityException, URISyntaxException {
        super(handle);
        this.uri = new URI(checkScheme(scheme), null, hostname, port, path, null, null);
        this.options = options;
        this.httpClient = setupOkHttpClient();
        this.wsListener = new CBLWebSocketListener();
    }

    @Override
    protected void send(byte[] allocatedData) {
        if (this.webSocket.send(ByteString.of(allocatedData, 0, allocatedData.length))) {
            completedWrite(allocatedData.length);
        }
        else { Log.e(TAG, "CBLWebSocket.send() FAILED to send data"); }
    }

    @Override
    protected void completedReceive(long byteCount) { }

    @Override
    protected void close() { }

    @Override
    protected void requestClose(int status, String message) {
        if (webSocket == null) {
            Log.w(TAG, "CBLWebSocket.requestClose() webSocket is not initialized.");
            return;
        }

        if (!webSocket.close(status, message)) {
            Log.w(
                TAG,
                "CBLWebSocket.requestClose() Failed to attempt to initiate a graceful shutdown of this web socket.");
        }
    }

    protected boolean handleClose(Throwable error) { return false; }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------

    private void start() {
        Log.v(TAG, String.format(Locale.ENGLISH, "CBLWebSocket connecting to %s...", uri));
        httpClient.newWebSocket(newRequest(), wsListener);
    }

    private OkHttpClient setupOkHttpClient() throws GeneralSecurityException {
        final OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // timeouts
        builder.connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS);

        // redirection
        builder.followRedirects(true).followSslRedirects(true);

        // authenticator
        final Authenticator authenticator = setupAuthenticator();
        if (authenticator != null) { builder.authenticator(authenticator); }

        // setup SSLFactory and trusted certificate (pinned certificate)
        setupSSLSocketFactory(builder);

        return builder.build();
    }

    private Authenticator setupAuthenticator() {
        if (options != null && options.containsKey(kC4ReplicatorOptionAuthentication)) {
            @SuppressWarnings("unchecked") final Map<String, Object> auth
                = (Map<String, Object>) options.get(kC4ReplicatorOptionAuthentication);
            if (auth != null) {
                final String username = (String) auth.get(kC4ReplicatorAuthUserName);
                final String password = (String) auth.get(kC4ReplicatorAuthPassword);
                if (username != null && password != null) {
                    return new Authenticator() {
                        @Override
                        public Request authenticate(@NonNull Route route, @NonNull Response response) {
                            // http://www.ietf.org/rfc/rfc2617.txt
                            Log.v(TAG, "Authenticating for response: " + response);

                            // If failed 3 times, give up.
                            if (responseCount(response) >= 3) { return null; }

                            final List<Challenge> challenges = response.challenges();
                            Log.v(TAG, "Challenges: " + challenges);
                            if (challenges != null) {
                                for (Challenge challenge : challenges) {
                                    if (challenge.scheme().equals("Basic")) {
                                        return response.request()
                                            .newBuilder()
                                            .header("Authorization", Credentials.basic(username, password))
                                            .build();
                                    }

                                    // NOTE: Not implemented Digest authentication
                                    //       https://github.com/rburgst/okhttp-digest
                                    //else if(challenge.scheme().equals("Digest")){
                                    //}
                                }
                            }

                            return null;
                        }
                    };
                }
            }
        }
        return null;
    }

    private InputStream toStream(byte[] pin) {
        return new Buffer().write(pin).inputStream();
    }

    private int responseCount(Response response) {
        int result = 1;
        while ((response = response.priorResponse()) != null) {
            result++;
        }
        return result;
    }

    private Request newRequest() {
        final Request.Builder builder = new Request.Builder();

        // Sets the URL target of this request.
        builder.url(uri.toString());

        // Set/update the "Host" header:
        String host = uri.getHost();
        if (uri.getPort() != -1) { host = String.format(Locale.ENGLISH, "%s:%d", host, uri.getPort()); }
        builder.header("Host", host);

        // Construct the HTTP request:
        if (options != null) {
            // Extra Headers
            @SuppressWarnings("unchecked") final Map<String, Object> extraHeaders
                = (Map<String, Object>) options.get(kC4ReplicatorOptionExtraHeaders);
            if (extraHeaders != null) {
                for (Map.Entry<String, Object> entry : extraHeaders.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue().toString());
                }
            }

            // Cookies:
            final String cookieString = (String) options.get(kC4ReplicatorOptionCookies);
            if (cookieString != null) { builder.addHeader("Cookie", cookieString); }

            // Configure WebSocket related headers:
            final String protocols = (String) options.get(kC4SocketOptionWSProtocols);
            if (protocols != null) {
                builder.header("Sec-WebSocket-Protocol", protocols);
            }
        }

        return builder.build();
    }

    private void receivedHTTPResponse(Response response) {
        final int httpStatus = response.code();
        Log.v(TAG, "receivedHTTPResponse() httpStatus -> " + httpStatus);

        // Post the response headers to LiteCore:
        final Headers hs = response.headers();
        if (hs != null && hs.size() > 0) {
            byte[] headersFleece = null;
            final Map<String, Object> headers = new HashMap<>();
            for (int i = 0; i < hs.size(); i++) {
                headers.put(hs.name(i), hs.value(i));
                //Log.e(TAG, hs.name(i) + " -> " + hs.value(i));
            }
            final FLEncoder enc = new FLEncoder();
            enc.write(headers);
            try {
                headersFleece = enc.finish();
            }
            catch (LiteCoreException e) {
                Log.e(TAG, "Failed to encode", e);
            }
            finally {
                enc.free();
            }
            gotHTTPResponse(httpStatus, headersFleece);
        }
    }

    private void didClose(int code, String reason) {
        if (code == C4WebSocketCloseCode.kWebSocketCloseNormal) {
            didClose(null);
            return;
        }

        Log.i(TAG, "CBLWebSocket CLOSED WITH STATUS " + code + " \"" + reason + "\"");
        closed(handle, C4Constants.C4ErrorDomain.WebSocketDomain, code, reason);
    }

    private void didClose(Throwable error) {
        if (error == null) {
            closed(handle, C4Constants.C4ErrorDomain.WebSocketDomain, 0, null);
            return;
        }

        if (handleClose(error)) { return; }

        // TLS Certificate error
        if (error.getCause() instanceof java.security.cert.CertificateException) {
            closed(
                handle,
                C4Constants.C4ErrorDomain.NetworkDomain,
                C4Constants.NetworkError.kC4NetErrTLSCertUntrusted,
                null);
            return;
        }

        // SSLPeerUnverifiedException
        if (error instanceof javax.net.ssl.SSLPeerUnverifiedException) {
            closed(
                handle,
                C4Constants.C4ErrorDomain.NetworkDomain,
                C4Constants.NetworkError.kC4NetErrTLSCertUntrusted,
                null);
            return;
        }

        // UnknownHostException - this is thrown if Airplane mode, offline
        if (error instanceof UnknownHostException) {
            closed(
                handle,
                C4Constants.C4ErrorDomain.NetworkDomain,
                C4Constants.NetworkError.kC4NetErrUnknownHost,
                null);
            return;
        }

        closed(handle, C4Constants.C4ErrorDomain.WebSocketDomain, 0, null);
    }

    //-------------------------------------------------------------------------
    // SSL Support
    //-------------------------------------------------------------------------

    private String checkScheme(String scheme) {
        // NOTE: OkHttp can not understand blip/blips
        if (scheme.equalsIgnoreCase(C4Replicator.C4_REPLICATOR_SCHEME_2)) { return WEBSOCKET_SCHEME; }

        if (scheme.equalsIgnoreCase(C4Replicator.C4_REPLICATOR_TLS_SCHEME_2)) {
            return WEBSOCKET_SECURE_CONNECTION_SCHEME;
        }

        return scheme;
    }

    private void setupSSLSocketFactory(OkHttpClient.Builder builder) throws GeneralSecurityException {
        boolean isPinningServerCert = false;
        X509TrustManager trustManager = null;
        if (options != null && options.containsKey(kC4ReplicatorOptionPinnedServerCert)) {
            final byte[] pin = (byte[]) options.get(kC4ReplicatorOptionPinnedServerCert);
            if (pin != null) {
                trustManager = trustManagerForCertificates(toStream(pin));
                isPinningServerCert = true;
            }
        }

        if (trustManager == null) { trustManager = defaultTrustManager(); }

        SSLContext.getInstance("TLS").init(null, new TrustManager[] {trustManager}, null);
        final SSLSocketFactory sslSocketFactory = new TLSSocketFactory(null, new TrustManager[] {trustManager}, null);
        builder.sslSocketFactory(sslSocketFactory, trustManager);

        if (isPinningServerCert) {
            // Custom hostname verifier - allow IP address and empty Common Name (CN).
            builder.hostnameVerifier(CustomHostnameVerifier.getInstance());
        }
    }

    private X509TrustManager defaultTrustManager() throws GeneralSecurityException {
        final TrustManagerFactory trustManagerFactory
            = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init((KeyStore) null);
        final TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        if (trustManagers.length == 0) { throw new IllegalStateException("Cannot find the default trust manager"); }
        return (X509TrustManager) trustManagers[0];
    }

    // https://github.com/square/okhttp/wiki/HTTPS
    // https://github.com/square/okhttp/blob/master/samples/guide/src/main/java/okhttp3/recipes/CustomTrust.java
    private X509TrustManager trustManagerForCertificates(InputStream in) throws GeneralSecurityException {
        final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        final Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(in);
        if (certificates.isEmpty()) {
            throw new IllegalArgumentException("expected non-empty set of trusted certificates");
        }

        // Put the certificates a key store.
        final char[] password = "umwxnikwxx".toCharArray(); // Any password will work.
        final KeyStore keyStore = newEmptyKeyStore(password);
        int index = 0;
        for (Certificate certificate : certificates) {
            final String certificateAlias = Integer.toString(index++);
            keyStore.setCertificateEntry(certificateAlias, certificate);
        }

        // Use it to build an X509 trust manager.
        final KeyManagerFactory keyManagerFactory
            = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password);
        final TrustManagerFactory trustManagerFactory
            = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        final TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
            throw new IllegalStateException("Unexpected default trust managers:"
                + Arrays.toString(trustManagers));
        }
        return (X509TrustManager) trustManagers[0];
    }

    private KeyStore newEmptyKeyStore(char[] password) throws GeneralSecurityException {
        try {
            final KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, password);
            return keyStore;
        }
        catch (IOException e) {
            throw new AssertionError(e);
        }
    }
}
