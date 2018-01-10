package com.couchbase.lite;

import android.os.Build;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.couchbase.lite.ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL;

public class ReplicatorConfiguration {

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

    public enum ReplicatorType {
        PUSH_AND_PULL,
        PUSH,
        PULL
    }

    //---------------------------------------------
    // member variables
    //---------------------------------------------

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
    // Builder
    //---------------------------------------------
    public static class Builder {
        //---------------------------------------------
        // member variables
        //---------------------------------------------
        ReplicatorConfiguration conf;

        //---------------------------------------------
        // Constructors
        //---------------------------------------------
        public Builder(Database database, Endpoint target) {
            conf = new ReplicatorConfiguration(database, target);
        }

        public Builder(ReplicatorConfiguration config) {
            conf = config.copy();
        }

        //---------------------------------------------
        // Setters
        //---------------------------------------------
        public Builder setReplicatorType(ReplicatorType replicatorType) {
            conf.replicatorType = replicatorType;
            return this;
        }

        public Builder setContinuous(boolean continuous) {
            conf.continuous = continuous;
            return this;
        }

        public Builder setConflictResolver(ConflictResolver conflictResolver) {
            if (conflictResolver == null)
                throw new IllegalArgumentException("conflictResolver parameter is null");
            conf.conflictResolver = conflictResolver;
            return this;
        }

        public Builder setAuthenticator(Authenticator authenticator) {
            conf.authenticator = authenticator;
            return this;
        }

        public Builder setPinnedServerCertificate(byte[] pinnedServerCertificate) {
            conf.pinnedServerCertificate = pinnedServerCertificate;
            return this;
        }

        public Builder setHeaders(Map<String, String> headers) {
            conf.headers = new HashMap<>(headers);
            return this;
        }

        public Builder setChannels(List<String> channels) {
            conf.channels = channels;
            return this;
        }

        public Builder setDocumentIDs(List<String> documentIDs) {
            conf.documentIDs = documentIDs;
            return this;
        }

        //---------------------------------------------
        // public API
        //---------------------------------------------
        public ReplicatorConfiguration build() {
            return conf.copy();
        }
    }
    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    private ReplicatorConfiguration(Database database, Endpoint target) {
        this.replicatorType = PUSH_AND_PULL;
        this.database = database;
        this.target = target;
        this.conflictResolver = database.getConflictResolver();
    }

    private ReplicatorConfiguration(Database database,
                                    Endpoint target,
                                    ReplicatorType replicatorType,
                                    boolean continuous,
                                    ConflictResolver conflictResolver,
                                    Authenticator authenticator,
                                    byte[] pinnedServerCertificate,
                                    Map<String, String> headers,
                                    List<String> channels,
                                    List<String> documentIDs) {
        this.database = database;
        this.target = target;
        this.replicatorType = replicatorType;
        this.continuous = continuous;
        this.conflictResolver = conflictResolver;
        this.authenticator = authenticator;
        this.pinnedServerCertificate = pinnedServerCertificate;
        this.headers = headers;
        this.channels = channels;
        this.documentIDs = documentIDs;
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    public Database getDatabase() {
        return database;
    }

    public Endpoint getTarget() {
        return target;
    }

    public ReplicatorType getReplicatorType() {
        return replicatorType;
    }

    public boolean isContinuous() {
        return continuous;
    }

    /**
     * The conflict resolver for this replicator.
     * The default value is nil, which means the local database's conflict resolver will be used.
     */
    public ConflictResolver getConflictResolver() {
        return conflictResolver;
    }

    public Authenticator getAuthenticator() {
        return authenticator;
    }

    public byte[] getPinnedServerCertificate() {
        return pinnedServerCertificate;
    }

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

    ReplicatorConfiguration copy() {
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
            return urlEndpoint.getURI();
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
            userAgent = String.format(Locale.ENGLISH,
                    "CouchbaseLite/%s %s Build/%d Commit/%.8s",
                    BuildConfig.VERSION_NAME,
                    getSystemInfo(),
                    BuildConfig.BUILD_NO,
                    BuildConfig.GitHash
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
