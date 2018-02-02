package com.couchbase.lite;

import android.os.Build;

import com.couchbase.litecore.C4;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.couchbase.lite.ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL;

/**
 * Replicator configuration.
 */
public final class ReplicatorConfiguration {

    // Replicator option dictionary keys:
    static final String kC4ReplicatorOptionExtraHeaders = "headers";  // Extra HTTP headers; string[]
    static final String kC4ReplicatorOptionCookies = "cookies";  // HTTP Cookie header value; string
    static final String kCBLReplicatorAuthOption = "auth";       // Auth settings; Dict
    static final String kC4ReplicatorOptionPinnedServerCert = "pinnedCert";  // Cert or public key; data
    static final String kC4ReplicatorOptionDocIDs = "docIDs";   // Docs to replicate; string[]
    static final String kC4ReplicatorOptionChannels = "channels";// SG channel names; string[]
    static final String kC4ReplicatorOptionFilter = "filter";   // Filter name; string
    static final String kC4ReplicatorOptionFilterParams = "filterParams";  // Filter params; Dict[string]
    static final String kC4ReplicatorOptionSkipDeleted = "skipDeleted"; // Don't push/pull tombstones; bool
    static final String kC4ReplicatorOptionNoConflicts = "noConflicts"; // Puller rejects conflicts; bool
    static final String kC4ReplicatorCheckpointInterval = "checkpointInterval"; // How often to checkpoint, in seconds; number

    // Auth dictionary keys:
    static final String kC4ReplicatorAuthType = "type"; // Auth property; string
    static final String kCBLReplicatorAuthUserName = "username"; // Auth property; string
    static final String kCBLReplicatorAuthPassword = "password"; // Auth property; string
    static final String kC4ReplicatorAuthClientCert = "clientCert"; // Auth property; value platform-dependent

    // auth.type values:
    static final String kC4AuthTypeBasic = "Basic"; // HTTP Basic (the default)
    static final String kC4AuthTypeSession = "Session"; // SG session cookie
    static final String kC4AuthTypeOpenIDConnect = "OpenID Connect";
    static final String kC4AuthTypeFacebook = "Facebook";
    static final String kC4AuthTypeClientCert = "Client Cert";

    /**
     * Replicator type
     * PUSH_AND_PULL: Bidirectional; both push and pull
     * PUSH: Pushing changes to the target
     * PULL: Pulling changes from the target
     */
    public enum ReplicatorType {
        PUSH_AND_PULL,
        PUSH,
        PULL
    }

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private boolean readonly = false;
    private Database database = null;
    private Endpoint target = null;
    private ReplicatorType replicatorType = PUSH_AND_PULL;
    private boolean continuous = false;
    private ConflictResolver conflictResolver = null;
    private Authenticator authenticator = null;
    private Map<String, String> headers = null;
    private byte[] pinnedServerCertificate = null;
    private List<String> channels = null;
    private List<String> documentIDs = null;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    public ReplicatorConfiguration(ReplicatorConfiguration config) {
        this.readonly = false;
        this.database = config.database;
        this.target = config.target;
        this.replicatorType = config.replicatorType;
        this.continuous = config.continuous;
        this.conflictResolver = config.conflictResolver;
        this.authenticator = config.authenticator;
        this.pinnedServerCertificate = config.pinnedServerCertificate;
        this.headers = config.headers;
        this.channels = config.channels;
        this.documentIDs = config.documentIDs;
    }

    public ReplicatorConfiguration(Database database, Endpoint target) {
        this.readonly = false;
        this.replicatorType = PUSH_AND_PULL;
        this.database = database;
        this.target = target;
        this.conflictResolver = database.getConflictResolver();
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    /**
     * Sets the replicator type indicating the direction of the replicator.
     * The default value is .pushAndPull which is bidrectional.
     *
     * @param replicatorType The replicator type.
     * @return The self object.
     */
    public ReplicatorConfiguration setReplicatorType(ReplicatorType replicatorType) {
        if (readonly)
            throw new IllegalStateException("ReplicatorConfiguration is readonly mode.");
        this.replicatorType = replicatorType;
        return this;
    }

    /**
     * Sets whether the replicator stays active indefinitely to replicate
     * changed documents. The default value is false, which means that the
     * replicator will stop after it finishes replicating the changed
     * documents.
     *
     * @param continuous The continuous flag.
     * @return The self object.
     */
    public ReplicatorConfiguration setContinuous(boolean continuous) {
        if (readonly)
            throw new IllegalStateException("ReplicatorConfiguration is readonly mode.");
        this.continuous = continuous;
        return this;
    }

    /**
     * Sets the custom conflict resolver for this replicator. Without
     * setting the conflict resolver, CouchbaseLite will use the default
     * conflict resolver.
     *
     * @param conflictResolver The conflict resolver.
     * @return The self object.
     */
    public ReplicatorConfiguration setConflictResolver(ConflictResolver conflictResolver) {
        if (conflictResolver == null)
            throw new IllegalArgumentException("conflictResolver parameter is null");
        if (readonly)
            throw new IllegalStateException("ReplicatorConfiguration is readonly mode.");
        this.conflictResolver = conflictResolver;
        return this;
    }

    /**
     * Sets the authenticator to authenticate with a remote target server.
     * Currently there are two types of the authenticators,
     * BasicAuthenticator and SessionAuthenticator, supported.
     *
     * @param authenticator The authenticator.
     * @return The self object.
     */
    public ReplicatorConfiguration setAuthenticator(Authenticator authenticator) {
        if (readonly)
            throw new IllegalStateException("ReplicatorConfiguration is readonly mode.");
        this.authenticator = authenticator;
        return this;
    }

    /**
     * Sets the target server's SSL certificate.
     *
     * @param pinnedServerCertificate the SSL certificate.
     * @return The self object.
     */
    public ReplicatorConfiguration setPinnedServerCertificate(byte[] pinnedServerCertificate) {
        if (readonly)
            throw new IllegalStateException("ReplicatorConfiguration is readonly mode.");
        this.pinnedServerCertificate = pinnedServerCertificate;
        return this;
    }

    /**
     * Sets the extra HTTP headers to send in all requests to the remote target.
     *
     * @param headers The HTTP Headers.
     * @return The self object.
     */
    public ReplicatorConfiguration setHeaders(Map<String, String> headers) {
        if (readonly)
            throw new IllegalStateException("ReplicatorConfiguration is readonly mode.");
        this.headers = new HashMap<>(headers);
        return this;
    }

    /**
     * Sets a set of Sync Gateway channel names to pull from. Ignored for
     * push replication. If unset, all accessible channels will be pulled.
     * Note: channels that are not accessible to the user will be ignored
     * by Sync Gateway.
     *
     * @param channels The Sync Gateway channel names.
     * @return The self object.
     */
    public ReplicatorConfiguration setChannels(List<String> channels) {
        if (readonly)
            throw new IllegalStateException("ReplicatorConfiguration is readonly mode.");
        this.channels = channels;
        return this;
    }

    /**
     * Sets a set of document IDs to filter by: if given, only documents
     * with these IDs will be pushed and/or pulled.
     *
     * @param documentIDs The document IDs.
     * @return The self object.
     */
    public ReplicatorConfiguration setDocumentIDs(List<String> documentIDs) {
        if (readonly)
            throw new IllegalStateException("ReplicatorConfiguration is readonly mode.");
        this.documentIDs = documentIDs;
        return this;
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    /**
     * Return the local database to replicate with the replication target.
     */
    public Database getDatabase() {
        return database;
    }

    /**
     * Return the replication target to replicate with.
     */
    public Endpoint getTarget() {
        return target;
    }

    /**
     * Return Replicator type indicating the direction of the replicator.
     */
    public ReplicatorType getReplicatorType() {
        return replicatorType;
    }

    /**
     * Return the continuous flag indicating whether the replicator should stay
     * active indefinitely to replicate changed documents.
     */
    public boolean isContinuous() {
        return continuous;
    }

    /**
     * The conflict resolver for this replicator.
     */
    public ConflictResolver getConflictResolver() {
        return conflictResolver;
    }

    /**
     * Return the Authenticator to authenticate with a remote target.
     */
    public Authenticator getAuthenticator() {
        return authenticator;
    }

    /**
     * Return the remote target's SSL certificate.
     */
    public byte[] getPinnedServerCertificate() {
        return pinnedServerCertificate;
    }

    /**
     * Return Extra HTTP headers to send in all requests to the remote target.
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * A set of Sync Gateway channel names to pull from. Ignored for push replication.
     * The default value is null, meaning that all accessible channels will be pulled.
     * Note: channels that are not accessible to the user will be ignored by Sync Gateway.
     */
    public List<String> getChannels() {
        return channels;
    }

    /**
     * A set of document IDs to filter by: if not nil, only documents with these IDs will be pushed
     * and/or pulled.
     */
    public List<String> getDocumentIDs() {
        return documentIDs;
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    ReplicatorConfiguration readonlyCopy() {
        ReplicatorConfiguration config = new ReplicatorConfiguration(this);
        config.readonly = true;
        return config;
        /*
        return new ReplicatorConfiguration(
                database,
                target,
                replicatorType,
                continuous,
                conflictResolver,
                authenticator,
                pinnedServerCertificate != null ?
                        Arrays.copyOf(pinnedServerCertificate, pinnedServerCertificate.length) :
                        null,
                headers != null ? new HashMap<String, String>(headers) : null,
                channels != null ? new ArrayList<>(channels) : null,
                documentIDs != null ? new ArrayList<>(documentIDs) : null);
                */
    }

    Map<String, Object> effectiveOptions() {
        Map<String, Object> options = new HashMap<>();

        if (authenticator != null)
            authenticator.authenticate(options);

        // Add the pinned certificate if any:
        if (pinnedServerCertificate != null)
            options.put(kC4ReplicatorOptionPinnedServerCert, pinnedServerCertificate);

        if (documentIDs != null && documentIDs.size() > 0)
            options.put(kC4ReplicatorOptionDocIDs, documentIDs);

        if (channels != null && channels.size() > 0)
            options.put(kC4ReplicatorOptionChannels, channels);


        Map<String, Object> httpHeaders = new HashMap<>();
        // User-Agent:
        httpHeaders.put("User-Agent", getUserAgent());
        // headers
        if (headers != null && headers.size() > 0) {
            for (Map.Entry<String, String> entry : headers.entrySet())
                httpHeaders.put(entry.getKey(), entry.getValue());
        }
        options.put(kC4ReplicatorOptionExtraHeaders, httpHeaders);

        return options;
    }

    URI getTargetURI() {
        if (target instanceof URLEndpoint) {
            URLEndpoint urlEndpoint = (URLEndpoint) target;
            return urlEndpoint.getURL();
        } else
            return null;
    }

    Database getTargetDatabase() {
        if (target instanceof DatabaseEndpoint) {
            DatabaseEndpoint urlEndpoint = (DatabaseEndpoint) target;
            return urlEndpoint.getDatabase();
        } else
            return null;
    }

    //---------------------------------------------
    // Private level access
    //---------------------------------------------

    static String userAgent = null;

    static String getUserAgent() {
        if (userAgent == null) {
            String liteCoreVers = C4.getVersion();
            userAgent = String.format(Locale.ENGLISH,
                    "CouchbaseLite/%s %s Build/%d Commit/%.8s LiteCore/%s",
                    BuildConfig.VERSION_NAME,
                    getSystemInfo(),
                    BuildConfig.BUILD_NO,
                    BuildConfig.GitHash,
                    liteCoreVers
            );
        }
        return userAgent;
    }

    static String getSystemInfo() {
        StringBuilder result = new StringBuilder(64);
        result.append("(Java; Android ");
        String version = Build.VERSION.RELEASE; // "1.0" or "3.4b5"
        result.append(version.length() > 0 ? version : "1.0");
        // add the model for the release build
        if ("REL".equals(Build.VERSION.CODENAME)) {
            String model = Build.MODEL;
            if (model.length() > 0) {
                result.append("; ");
                result.append(model);
            }
        }
        result.append(")");
        return result.toString();
    }
}
