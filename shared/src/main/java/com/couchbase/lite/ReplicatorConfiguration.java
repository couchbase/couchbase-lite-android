package com.couchbase.lite;

import com.couchbase.lite.internal.support.URIUtils;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.couchbase.lite.ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL;

public class ReplicatorConfiguration {

    // Replicator option dictionary keys:
    static final String kC4ReplicatorOptionExtraHeaders = "cookies";  // Extra HTTP headers; string[]
    static final String kC4ReplicatorOptionCookies = "cookies";  // HTTP Cookie header value; string
    static final String kCBLReplicatorAuthOption = "auth";       // Auth settings; Dict
    static final String kC4ReplicatorOptionPinnedServerCert = "pinnedCert";  // Cert or public key [data]
    static final String kC4ReplicatorOptionChannels = "channels";// SG channel names; string[]
    static final String kC4ReplicatorOptionFilter = "filter";   // Filter name; string
    static final String kC4ReplicatorOptionFilterParams = "filterParams";  // Filter params; Dict[string]
    static final String kC4ReplicatorOptionSkipDeleted = "skipDeleted"; // Don't push/pull tombstones; bool

    // Auth dictionary keys:
    static final String kC4ReplicatorAuthType = "type"; // Auth property; string
    static final String kCBLReplicatorAuthUserName = "username"; // Auth property; string
    static final String kCBLReplicatorAuthPassword = "password"; // Auth property; string

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

    public class Filter {
        private List<String> channels = null;
        private List<String> documentIDs = null;

        /*package*/ Filter() {
        }

        public List<String> getChannels() {
            return channels;
        }

        /*package*/ void setChannels(List<String> channels) {
            this.channels = channels;
        }

        public List<String> getDocumentIDs() {
            return documentIDs;
        }

        /*package*/ void setDocumentIDs(List<String> documentIDs) {
            this.documentIDs = documentIDs;
        }
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
    private Filter filter = null;
    // TODO: pinnedServerCertificate

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    public ReplicatorConfiguration(Database database, Database target) {
        this.replicatorType = PUSH_AND_PULL;
        this.database = database;
        this.target = target;
        this.filter = new Filter();
    }

    public ReplicatorConfiguration(Database database, URI target) {
        this.replicatorType = PUSH_AND_PULL;
        this.database = database;
        this.target = target;
        this.filter = new Filter();
    }

    public ReplicatorConfiguration(Database database, Object target, ReplicatorType replicatorType,
                                   boolean continuous, ConflictResolver conflictResolver,
                                   Authenticator authenticator, Filter filter) {
        this.database = database;
        this.target = target;
        this.replicatorType = replicatorType;
        this.continuous = continuous;
        this.conflictResolver = conflictResolver;
        this.authenticator = authenticator;
        this.filter = filter;
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

    public ConflictResolver getConflictResolver() {
        return conflictResolver;
    }

    public void setConflictResolver(ConflictResolver conflictResolver) {
        this.conflictResolver = conflictResolver;
    }

    public Authenticator getAuthenticator() {
        return authenticator;
    }

    public void setAuthenticator(Authenticator authenticator) {
        this.authenticator = authenticator;
    }

    public Filter getFilter() {
        return filter;
    }

    // TODO:
    public void setChannels(List<String> channels) {
        this.filter.setChannels(channels);
    }

    // TODO:
    public void setDocumentIDs(List<String> documentIDs) {
        this.filter.setDocumentIDs(documentIDs);
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------
    public ReplicatorConfiguration copy() {
        return new ReplicatorConfiguration(database, target, replicatorType, continuous,
                conflictResolver, authenticator, filter);
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    /*package*/ Map<String, Object> effectiveOptions() {
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

        // doc_ids
        if (filter.getDocumentIDs() != null && filter.getDocumentIDs().size() > 0)
            options.put("doc_ids", filter.getDocumentIDs());

        // channels
        if (filter.getDocumentIDs() != null && filter.getDocumentIDs().size() > 0)
            options.put(kC4ReplicatorOptionChannels, filter.getChannels());

        // TODO:
        // Add the pinned certificate if any:

        return options;
    }

    /*package*/URI getTargetURI() {
        return target instanceof URI ? (URI) target : null;
    }

    /*package*/Database getTargetDatabase() {
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
}
