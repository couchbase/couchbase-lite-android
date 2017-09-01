package com.couchbase.lite;

import android.os.Build;

import com.couchbase.lite.internal.support.URIUtils;

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
    private Object target = null;
    private ReplicatorType replicatorType = PUSH_AND_PULL;
    private boolean continuous = false;
    private ConflictResolver conflictResolver = null;
    private Authenticator authenticator = null;
    private byte[] pinnedServerCertificate = null;
    private List<String> channels = null;
    private List<String> documentIDs = null;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    public ReplicatorConfiguration(Database database, Database target) {
        this.replicatorType = PUSH_AND_PULL;
        this.database = database;
        this.target = target;
    }

    public ReplicatorConfiguration(Database database, URI target) {
        this.replicatorType = PUSH_AND_PULL;
        this.database = database;
        this.target = target;
    }

    public ReplicatorConfiguration(Database database,
                                   Object target,
                                   ReplicatorType replicatorType,
                                   boolean continuous,
                                   ConflictResolver conflictResolver,
                                   Authenticator authenticator,
                                   byte[] pinnedServerCertificate,
                                   List<String> channels,
                                   List<String> documentIDs) {
        this.database = database;
        this.target = target;
        this.replicatorType = replicatorType;
        this.continuous = continuous;
        this.conflictResolver = conflictResolver;
        this.authenticator = authenticator;
        this.pinnedServerCertificate = pinnedServerCertificate;
        this.channels = channels;
        this.documentIDs = documentIDs;
    }

    //---------------------------------------------
    // Getters/Setters
    //---------------------------------------------

    public Database getDatabase() {
        return database;
    }

    public Object getTarget() {
        return target;
    }

    public ReplicatorType getReplicatorType() {
        return replicatorType;
    }

    public void setReplicatorType(ReplicatorType replicatorType) {
        this.replicatorType = replicatorType;
    }

    public boolean isContinuous() {
        return continuous;
    }

    public void setContinuous(boolean continuous) {
        this.continuous = continuous;
    }

    /**
     * The conflict resolver for this replicator.
     * The default value is nil, which means the local database's conflict resolver will be used.
     */
    public ConflictResolver getConflictResolver() {
        return conflictResolver;
    }

    public void setConflictResolver(ConflictResolver conflictResolver) {
        this.conflictResolver = conflictResolver;
    }

    public byte[] getPinnedServerCertificate() {
        return pinnedServerCertificate;
    }

    public void setPinnedServerCertificate(byte[] pinnedServerCertificate) {
        this.pinnedServerCertificate = pinnedServerCertificate;
    }

    public Authenticator getAuthenticator() {
        return authenticator;
    }

    public void setAuthenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    /**
     * A set of Sync Gateway channel names to pull from. Ignored for push replication.
     * The default value is null, meaning that all accessible channels will be pulled.
     * Note: channels that are not accessible to the user will be ignored by Sync Gateway.
     */
    public List<String> getChannels() {
        return channels;
    }

    public void setChannels(List<String> channels) {
        this.channels = channels;
    }

    /**
     * A set of document IDs to filter by: if not nil, only documents with these IDs will be pushed
     * and/or pulled.
     */
    public List<String> getDocumentIDs() {
        return documentIDs;
    }

    public void setDocumentIDs(List<String> documentIDs) {
        this.documentIDs = documentIDs;
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------
    public ReplicatorConfiguration copy() {
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
                channels != null ? new ArrayList<>(channels) : null,
                documentIDs != null ? new ArrayList<>(documentIDs) : null);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    Map<String, Object> effectiveOptions() {
        Map<String, Object> options = new HashMap<>();

        String username = getUsername();
        if (username != null) {
            Map<String, Object> auth = new HashMap<>();
            auth.put(kCBLReplicatorAuthUserName, username);
            auth.put(kCBLReplicatorAuthPassword, getPassword());
            options.put(kCBLReplicatorAuthOption, auth);
        } else {
            if (authenticator != null)
                authenticator.authenticate(options);
        }

        // Add the pinned certificate if any:
        if (pinnedServerCertificate != null)
            options.put(kC4ReplicatorOptionPinnedServerCert, pinnedServerCertificate);

        if (documentIDs != null && documentIDs.size() > 0)
            options.put(kC4ReplicatorOptionDocIDs, documentIDs);

        if (channels != null && channels.size() > 0)
            options.put(kC4ReplicatorOptionChannels, channels);

        // User-Agent:
        Map<String, Object> userAgentHeader = new HashMap<>();
        userAgentHeader.put("User-Agent", getUserAgent());
        options.put(kC4ReplicatorOptionExtraHeaders, userAgentHeader);

        return options;
    }

    URI getTargetURI() {
        return target instanceof URI ? (URI) target : null;
    }

    Database getTargetDatabase() {
        return target instanceof Database ? (Database) target : null;
    }

    //---------------------------------------------
    // Private level access
    //---------------------------------------------
    private String getUsername() {
        if (target != null && target instanceof URI)
            return URIUtils.getUsername((URI) target);
        else
            return null;
    }

    private String getPassword() {
        if (target != null && target instanceof URI)
            return URIUtils.getPassword((URI) target);
        else
            return null;
    }

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
