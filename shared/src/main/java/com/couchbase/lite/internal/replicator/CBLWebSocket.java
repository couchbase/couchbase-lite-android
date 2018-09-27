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

import com.couchbase.lite.internal.support.Log;
import com.couchbase.litecore.C4Socket;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.FLValue;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
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

import static com.couchbase.litecore.C4Constants.C4ErrorDomain.NetworkDomain;
import static com.couchbase.litecore.C4Constants.C4ErrorDomain.POSIXDomain;
import static com.couchbase.litecore.C4Constants.C4ErrorDomain.WebSocketDomain;
import static com.couchbase.litecore.C4Constants.NetworkError.kC4NetErrTLSCertUntrusted;
import static com.couchbase.litecore.C4Constants.NetworkError.kC4NetErrUnknownHost;
import static com.couchbase.litecore.C4Replicator.kC4Replicator2Scheme;
import static com.couchbase.litecore.C4Replicator.kC4Replicator2TLSScheme;
import static com.couchbase.litecore.C4WebSocketCloseCode.kWebSocketCloseNormal;
import static com.couchbase.litecore.C4WebSocketCloseCode.kWebSocketClosePolicyError;
import static com.couchbase.litecore.C4WebSocketCloseCode.kWebSocketCloseProtocolError;

/**
 * NOTE: CBLWebSocket class should be public as this class is instantiated
 * from com.couchbase.litecore package.
 */
public final class CBLWebSocket extends C4Socket {
    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------
    private static final String TAG = Log.WEB_SOCKET;

    // Posix errno values with Android.
    // from sysroot/usr/include/asm-generic/errno.h
    private final static int ECONNRESET = 104;    // java.net.SocketException
    private final static int ECONNREFUSED = 111;  // java.net.ConnectException

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    private URI uri = null;
    private Map<String, Object> options;
    private WebSocket webSocket = null;
    private AtomicBoolean isRequestClose = new AtomicBoolean(false);

    OkHttpClient httpClient = null;
    CBLWebSocketListener wsListener = null;

    //-------------------------------------------------------------------------
    // constructor
    //-------------------------------------------------------------------------
    CBLWebSocket(long handle, String scheme, String hostname, int port, String path, Map<String, Object> options) throws GeneralSecurityException, URISyntaxException {
        super(handle);
        this.uri = new URI(checkScheme(scheme), null, hostname, port, path, null, null);
        this.options = options;
        this.httpClient = setupOkHttpClient();
        this.wsListener = new CBLWebSocketListener();
    }

    //-------------------------------------------------------------------------
    // Internal class
    //-------------------------------------------------------------------------
    class CBLWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.v(TAG, "WebSocketListener.onOpen() response -> " + response);
            CBLWebSocket.this.webSocket = webSocket;
            receivedHTTPResponse(response);
            Log.i(TAG, "CBLWebSocket CONNECTED!");
            opened(handle);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Log.v(TAG, "WebSocketListener.onMessage() text -> " + text);
            received(handle, text.getBytes());
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            Log.v(TAG, "WebSocketListener.onMessage() bytes -> " + bytes.hex());
            received(handle, bytes.toByteArray());
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.v(TAG, "WebSocketListener.onClosing() code -> " + code + ", reason -> " + reason);
            if (!isRequestClose.get()) {
                closeRequested(handle, code, reason);
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.v(TAG, "WebSocketListener.onClosed() code -> " + code + ", reason -> " + reason);
            didClose(code, reason);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.w(TAG, "WebSocketListener.onFailure() response -> " + response, t);

            // Invoked when a web socket has been closed due to an error reading from or writing to the
            // network. Both outgoing and incoming messages may have been lost. No further calls to this
            // listener will be made.
            if (response != null) {
                int httpStatus = response.code();
                if (httpStatus != 101) {
                    int closeCode = kWebSocketClosePolicyError;
                    if (httpStatus >= 300 && httpStatus < 1000)
                        closeCode = httpStatus;
                    didClose(closeCode, response.message());
                } else {
                    didClose(kWebSocketCloseProtocolError, response.message());
                }
            } else {
                didClose(t);
            }
        }
    }

    //-------------------------------------------------------------------------
    // Abstract method implementation
    //-------------------------------------------------------------------------
    @Override
    protected void send(byte[] allocatedData) {
        if (this.webSocket.send(ByteString.of(allocatedData, 0, allocatedData.length)))
            completedWrite(allocatedData.length);
        else
            Log.e(TAG, "CBLWebSocket.send() FAILED to send data");
    }

    @Override
    protected void completedReceive(long byteCount) {
    }

    @Override
    protected void close() { }

    @Override
    protected void requestClose(int status, String message) {
        if (webSocket == null) {
            Log.w(TAG, "CBLWebSocket.requestClose() webSocket is not initialized.");
            return;
        }

        if (webSocket.close(status, message)) {
            isRequestClose.set(true);
        } else {
            Log.w(TAG, "CBLWebSocket.requestClose() Failed to attempt to initiate a graceful shutdown of this web socket.");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Socket Factory Callbacks
    // ---------------------------------------------------------------------------------------------
    public static void socket_open(long socket, int socketFactoryContext, String scheme, String hostname, int port, String path, byte[] optionsFleece) {
        Log.v(TAG, "CBLWebSocket.socket_open()");
        Map<String, Object> options = null;
        if (optionsFleece != null)
            options = FLValue.fromData(optionsFleece).asDict();

        // NOTE: OkHttp can not understand blip/blips
        if (scheme.equalsIgnoreCase(kC4Replicator2Scheme))
            scheme = WEBSOCKET_SCHEME;
        else if (scheme.equalsIgnoreCase(kC4Replicator2TLSScheme))
            scheme = WEBSOCKET_SECURE_CONNECTION_SCHEME;

        CBLWebSocket c4sock;
        try {
            c4sock = new CBLWebSocket(socket, scheme, hostname, port, path, options);
        } catch (Exception e) {
            Log.e(TAG, "Failed to instantiate C4Socket: " + e);
            e.printStackTrace();
            return;
        }

        reverseLookupTable.put(socket, c4sock);

        c4sock.start();
    }

    protected void start() {
        Log.v(TAG, String.format(Locale.ENGLISH, "CBLWebSocket connecting to %s...", uri));
        httpClient.newWebSocket(newRequest(), wsListener);
    }

    //-------------------------------------------------------------------------
    // private methods
    //-------------------------------------------------------------------------
    private OkHttpClient setupOkHttpClient() throws GeneralSecurityException {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();

        // timeouts
        builder.connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);

        // redirection
        builder.followRedirects(true).followSslRedirects(true);

        // authenticator
        Authenticator authenticator = setupAuthenticator();
        if (authenticator != null)
            builder.authenticator(authenticator);

        // trusted certificate (pinned certificate)
        setupTrustedCertificate(builder);

        return builder.build();
    }

    private Authenticator setupAuthenticator() {
        if (options != null && options.containsKey(kC4ReplicatorOptionAuthentication)) {
            Map<String, Object> auth = (Map<String, Object>) options.get(kC4ReplicatorOptionAuthentication);
            if (auth != null) {
                final String username = (String) auth.get(kC4ReplicatorAuthUserName);
                final String password = (String) auth.get(kC4ReplicatorAuthPassword);
                if (username != null && password != null) {
                    return new Authenticator() {
                        @Override
                        public Request authenticate(Route route, Response response) throws IOException {
                            // http://www.ietf.org/rfc/rfc2617.txt
                            Log.v(TAG, "Authenticating for response: " + response);
                            // If failed 3 times, give up.
                            if (responseCount(response) >= 3)
                                return null;

                            List<Challenge> challenges = response.challenges();
                            Log.v(TAG, "Challenges: " + challenges);
                            if (challenges != null) {
                                for (Challenge challenge : challenges) {
                                    if (challenge.scheme().equals("Basic")) {
                                        String credential = Credentials.basic(username, password);
                                        return response.request().newBuilder().header("Authorization", credential).build();
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

    private void setupTrustedCertificate(OkHttpClient.Builder builder) throws GeneralSecurityException {
        if (options != null && options.containsKey(kC4ReplicatorOptionPinnedServerCert)) {
            byte[] pin = (byte[]) options.get(kC4ReplicatorOptionPinnedServerCert);
            if (pin != null) {
                X509TrustManager trustManager = trustManagerForCertificates(toStream(pin));
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{trustManager}, null);
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                if (trustManager != null && sslSocketFactory != null)
                    builder.sslSocketFactory(sslSocketFactory, trustManager);

                // custom hostname verifier - allow IP address and empty Common Name (CN).
                builder.hostnameVerifier(CustomHostnameVerifier.getInstance());
            }
        }
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
        Request.Builder builder = new Request.Builder();

        // Sets the URL target of this request.
        builder.url(uri.toString());

        // Set/update the "Host" header:
        String host = uri.getHost();
        if (uri.getPort() != -1)
            host = String.format(Locale.ENGLISH, "%s:%d", host, uri.getPort());
        builder.header("Host", host);

        // Construct the HTTP request:
        if (options != null) {
            // Extra Headers
            Map<String, Object> extraHeaders = (Map<String, Object>) options.get(kC4ReplicatorOptionExtraHeaders);
            if (extraHeaders != null) {
                for (Map.Entry<String, Object> entry : extraHeaders.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue().toString());
                }
            }

            // Cookies:
            String cookieString = (String) options.get(kC4ReplicatorOptionCookies);
            if (cookieString != null)
                builder.addHeader("Cookie", cookieString);
        }

        // Configure WebSocket related headers:
        String protocols = (String) options.get(kC4SocketOptionWSProtocols);
        if (protocols != null) {
            builder.header("Sec-WebSocket-Protocol", protocols);
        }

        return builder.build();
    }

    private void receivedHTTPResponse(Response response) {
        int httpStatus = response.code();
        Log.v(TAG, "receivedHTTPResponse() httpStatus -> " + httpStatus);

        // Post the response headers to LiteCore:
        Headers hs = response.headers();
        if (hs != null && hs.size() > 0) {
            byte[] headersFleece = null;
            Map<String, Object> headers = new HashMap<>();
            for (int i = 0; i < hs.size(); i++) {
                headers.put(hs.name(i), hs.value(i));
                //Log.e(TAG, hs.name(i) + " -> " + hs.value(i));
            }
            FLEncoder enc = new FLEncoder();
            enc.write(headers);
            try {
                headersFleece = enc.finish();
            } catch (LiteCoreException e) {
                Log.e(TAG, "Failed to encode", e);
            } finally {
                enc.free();
            }
            gotHTTPResponse(httpStatus, headersFleece);
        }
    }

    // https://github.com/square/okhttp/wiki/HTTPS
    // https://github.com/square/okhttp/blob/master/samples/guide/src/main/java/okhttp3/recipes/CustomTrust.java
    private X509TrustManager trustManagerForCertificates(InputStream in) throws GeneralSecurityException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        Collection<? extends Certificate> certificates = certificateFactory.generateCertificates(in);
        if (certificates.isEmpty()) {
            throw new IllegalArgumentException("expected non-empty set of trusted certificates");
        }

        // Put the certificates a key store.
        char[] password = "umwxnikwxx".toCharArray(); // Any password will work.
        KeyStore keyStore = newEmptyKeyStore(password);
        int index = 0;
        for (Certificate certificate : certificates) {
            String certificateAlias = Integer.toString(index++);
            keyStore.setCertificateEntry(certificateAlias, certificate);
        }

        // Use it to build an X509 trust manager.
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password);
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(keyStore);
        TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
        if (trustManagers.length != 1 || !(trustManagers[0] instanceof X509TrustManager)) {
            throw new IllegalStateException("Unexpected default trust managers:"
                    + Arrays.toString(trustManagers));
        }
        return (X509TrustManager) trustManagers[0];
    }

    private KeyStore newEmptyKeyStore(char[] password) throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            InputStream in = null; // By convention, 'null' creates an empty key store.
            keyStore.load(in, password);
            return keyStore;
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    private void didClose(int code, String reason) {
        if (code == kWebSocketCloseNormal) {
            didClose(null);
            return;
        }

        Log.i(TAG, "CBLWebSocket CLOSED WITH STATUS " + code + " \"" + reason + "\"");
        closed(handle, WebSocketDomain, code, reason);
    }

    private void didClose(Throwable error) {
        if (error == null) {
            closed(handle, WebSocketDomain, 0, null);
            return;
        }

        Integer errno = getAndroidExceptionErrorNumber(error);
        if (errno != null) {
            closed(handle, POSIXDomain, errno, null);
        }
        // TLS Certificate error
        else if (error.getCause() != null &&
                error.getCause() instanceof java.security.cert.CertificateException) {
            closed(handle, NetworkDomain, kC4NetErrTLSCertUntrusted, null);
        }
        // SSLPeerUnverifiedException
        else if (error instanceof javax.net.ssl.SSLPeerUnverifiedException) {
            closed(handle, NetworkDomain, kC4NetErrTLSCertUntrusted, null);
        }
        // ConnectException
        else if (error instanceof java.net.ConnectException) {
            closed(handle, POSIXDomain, ECONNREFUSED, null);
        }
        // SocketException
        else if (error instanceof java.net.SocketException) {
            closed(handle, POSIXDomain, ECONNRESET, null);
        }
        // EOFException
        else if (error instanceof java.io.EOFException) {
            closed(handle, POSIXDomain, ECONNRESET, null);
        }
        // UnknownHostException - this is thrown if Airplane mode, offline
        else if (error instanceof java.net.UnknownHostException) {
            closed(handle, NetworkDomain, kC4NetErrUnknownHost, null);
        }
        // Unknown
        else {
            closed(handle, WebSocketDomain, 0, null);
        }
    }

    private Integer getAndroidExceptionErrorNumber(Throwable error) {
        if (error == null)
            return null;

        // android.system.ErrnoException is available from API 21 (LOLLIPOP):
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.LOLLIPOP)
            return null;

        Throwable cause = error.getCause();
        if (cause == null)
            return null;

        cause = cause.getCause();
        if (cause == null)
            return null;

        Class clazz;
        Field errno;
        try {
            clazz = Class.forName("android.system.ErrnoException");
            errno = clazz.getField("errno");
            return errno.getInt(cause);
        } catch (Exception e) {
            return null;
        }
    }

    private String checkScheme(String scheme) {
        // NOTE: OkHttp can not understand blip/blips
        if (scheme.equalsIgnoreCase(kC4Replicator2Scheme))
            return WEBSOCKET_SCHEME;
        else if (scheme.equalsIgnoreCase(kC4Replicator2TLSScheme))
            return WEBSOCKET_SECURE_CONNECTION_SCHEME;
        return scheme;
    }
}
