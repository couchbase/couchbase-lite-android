package com.couchbase.lite;

import com.couchbase.lite.internal.support.URIUtils;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static com.couchbase.lite.Authenticator.kCBLReplicatorAuthOption;
import static com.couchbase.lite.Authenticator.kCBLReplicatorAuthPassword;
import static com.couchbase.lite.Authenticator.kCBLReplicatorAuthUserName;
import static com.couchbase.lite.ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL;

public class ReplicatorConfiguration {

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
    // TODO: pinnedServerCertificate
    // TODO: filter

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

    private ReplicatorConfiguration(Database database, Object target, ReplicatorType replicatorType,
                                    boolean continuous, ConflictResolver conflictResolver,
                                    Authenticator authenticator) {
        this.database = database;
        this.target = target;
        this.replicatorType = replicatorType;
        this.continuous = continuous;
        this.conflictResolver = conflictResolver;
        this.authenticator = authenticator;
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

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------
    public ReplicatorConfiguration copy() {
        return new ReplicatorConfiguration(database, target, replicatorType, continuous,
                conflictResolver, authenticator);
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
        } else
            authenticator.authenticate(options);

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
