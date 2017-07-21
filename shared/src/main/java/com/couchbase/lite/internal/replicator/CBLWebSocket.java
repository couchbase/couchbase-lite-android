package com.couchbase.lite.internal.replicator;

import android.os.Build;

import com.couchbase.lite.Log;
import com.couchbase.litecore.C4Socket;
import com.couchbase.litecore.LiteCoreException;
import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.FLValue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
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
import java.util.Random;
import java.util.concurrent.TimeUnit;

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

import static android.util.Base64.NO_WRAP;
import static android.util.Base64.encodeToString;
import static com.couchbase.litecore.C4Constants.C4ErrorDomain.NetworkDomain;
import static com.couchbase.litecore.C4Constants.C4ErrorDomain.POSIXDomain;
import static com.couchbase.litecore.C4Constants.C4ErrorDomain.WebSocketDomain;
import static com.couchbase.litecore.C4Constants.NetworkError.kC4NetErrTLSCertUntrusted;

public class CBLWebSocket extends C4Socket {
    //-------------------------------------------------------------------------
    // Constants
    //-------------------------------------------------------------------------
    private static final String TAG = CBLWebSocket.class.getSimpleName();

    //-------------------------------------------------------------------------
    // Member Variables
    //-------------------------------------------------------------------------
    private URI uri = null;
    private Map<String, Object> options;
    private WebSocket webSocket = null;
    OkHttpClient httpClient = null;
    CBLWebSocketListener wsListener = null;

    //-------------------------------------------------------------------------
    // constructor
    //-------------------------------------------------------------------------
    public CBLWebSocket(long handle, URI uri, Map<String, Object> options) throws GeneralSecurityException {
        super(handle);
        this.uri = uri;
        this.options = options;
        this.httpClient = setupOkHttpClient();
        this.wsListener = new CBLWebSocketListener();
    }

    public static void register() {
        C4Socket.IMPLEMENTATION_CLASS_NAME = CBLWebSocket.class.getName();
        registerFactory();
    }

    //-------------------------------------------------------------------------
    // Internal class
    //-------------------------------------------------------------------------
    class CBLWebSocketListener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            Log.e(TAG, "WebSocketListener.onOpen() response -> " + response);
            CBLWebSocket.this.webSocket = webSocket;
            receivedHTTPResponse(response);
            opened(handle);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            Log.e(TAG, "WebSocketListener.onMessage() text -> " + text);
            received(handle, text.getBytes());
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
            Log.e(TAG, "WebSocketListener.onMessage() bytes -> " + bytes.hex());
            received(handle, bytes.toByteArray());
        }

        @Override
        public void onClosing(WebSocket webSocket, int code, String reason) {
            Log.e(TAG, "WebSocketListener.onClosing() code -> " + code + ", reason -> " + reason);
            closeRequested(handle, code, reason);
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            Log.e(TAG, "WebSocketListener.onClosed() code -> " + code + ", reason -> " + reason);
            closed(handle, WebSocketDomain, code);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            Log.e(TAG, "WebSocketListener.onFailure() response -> " + response, t);

            // Invoked when a web socket has been closed due to an error reading from or writing to the
            // network. Both outgoing and incoming messages may have been lost. No further calls to this
            // listener will be made.

            if (response != null)
                closed(handle, WebSocketDomain, response.code());
            else if (t != null) {
                // TODO: Following codes works with only Android.
                // In case errno is set
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP &&
                        t.getCause() != null &&
                        t.getCause().getCause() != null &&
                        t.getCause().getCause() instanceof android.system.ErrnoException) {
                    android.system.ErrnoException e = (android.system.ErrnoException) t.getCause().getCause();
                    closed(handle, POSIXDomain, e != null ? e.errno : 0);
                }

                // TODO:
                // java.net.ConnectException -> ECONNREFUSED -> 61? with POSIXDomain
                // #define	ECONNREFUSED	61		/* Connection refused */

                // TLS Certificate error
                else if (t.getCause() != null &&
                        t.getCause() instanceof java.security.cert.CertificateException) {
                    closed(handle, NetworkDomain, kC4NetErrTLSCertUntrusted);
                }
                // SSLPeerUnverifiedException
                else if (t instanceof javax.net.ssl.SSLPeerUnverifiedException) {
                    closed(handle, NetworkDomain, kC4NetErrTLSCertUntrusted);
                } else {
                    closed(handle, WebSocketDomain, 0);
                }
            } else {
                closed(handle, WebSocketDomain, 0);
            }
        }
    }

    //-------------------------------------------------------------------------
    // Abstract method implementation
    //-------------------------------------------------------------------------

    @Override
    protected void start() {
        Log.i(TAG, String.format(Locale.ENGLISH, "CBLWebSocket connecting to %s...", uri));
        httpClient.newWebSocket(newRequest(), wsListener);
    }

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
    protected void close() {
    }

    @Override
    protected void requestClose(int status, String message) {
        if (!webSocket.close(status, message)) {
            Log.e(TAG, "CBLWebSocket.requestClose() Failed to attempt to initiate a graceful shutdown of this web socket.");
        }
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

        // certificate
        setupSelfSignedCertificate(builder);

        // HostnameVerifier
        setupHostnameVerifier(builder);

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

                            Log.i(TAG, "Authenticating for response: " + response);

                            // If failed 3 times, give up.
                            if (responseCount(response) >= 3)
                                return null;

                            List<Challenge> challenges = response.challenges();
                            Log.i(TAG, "Challenges: " + challenges);
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

    private void setupSelfSignedCertificate(OkHttpClient.Builder builder) throws GeneralSecurityException {
        if (options != null && options.containsKey(kC4ReplicatorOptionPinnedServerCert)) {
            byte[] pin = (byte[]) options.get(kC4ReplicatorOptionPinnedServerCert);
            if (pin != null) {
                X509TrustManager trustManager = trustManagerForCertificates(toStream(pin));
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{trustManager}, null);
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
                if (trustManager != null && sslSocketFactory != null)
                    builder.sslSocketFactory(sslSocketFactory, trustManager);
            }
        }
    }

    private InputStream toStream(byte[] pin) {
        return new Buffer().write(pin).inputStream();
    }

    private void setupHostnameVerifier(OkHttpClient.Builder builder) {
        builder.hostnameVerifier(CustomHostnameVerifier.getInstance());
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

        // Configure the nonce/key for the request:
        byte[] nonceBytes = new byte[16];
        new Random().nextBytes(nonceBytes);
        String nonceKey = encodeToString(nonceBytes, NO_WRAP);

        // Construct the HTTP request:
        if (options != null) {
            FLValue value = (FLValue) options.get(kC4ReplicatorOptionExtraHeaders);
            if (value != null && value.asDict() != null) {
                for (Map.Entry<String, Object> entry : value.asDict().entrySet())
                    builder.header(entry.getKey(), entry.getValue().toString());
            }
            // Cookies
            value = (FLValue) options.get(kC4ReplicatorOptionCookies);
            if (value != null && value.asString() != null)
                builder.addHeader("Cookie", value.asString());
        }

        // other header values
        builder.header("Connection", "Upgrade");
        builder.header("Upgrade", "websocket");
        builder.header("Sec-WebSocket-Version", "13");
        builder.header("Sec-WebSocket-Key", nonceKey);

        // Add User-Agent if necessary:
        builder.header("User-Agent", getUserAgent());

        return builder.build();
    }

    /**
     * Return User-Agent value
     * Format: ex: CouchbaseLite/1.2 (Java Linux/MIPS Android/)
     */
    private static String USER_AGENT = null;

    // TODO:
    private static String getUserAgent() {
        if (USER_AGENT == null) {
            USER_AGENT = String.format(Locale.ENGLISH, "%s/%s (%s/%s)",
                    "CouchbaseLite",
                    "2.0.0",
                    "Android",
                    Build.VERSION.RELEASE);
        }
        return USER_AGENT;
    }

    private void receivedHTTPResponse(Response response) {
        int httpStatus = response.code();
        Log.e(TAG, "receivedHTTPResponse() httpStatus -> " + httpStatus);

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
            }
            enc.free();
            gotHTTPResponse(httpStatus, headersFleece);
        }
    }

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
}
