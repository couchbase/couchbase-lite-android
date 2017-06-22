package com.couchbase.lite;

import com.couchbase.lite.internal.support.URIUtils;

import java.util.HashMap;
import java.util.Map;

import static com.couchbase.lite.ReplicatorType.PUSH_AND_PULL;

public class ReplicatorConfiguration {

    /**
     * Options key for authentication dictionary
     */
    public static final String kCBLReplicatorAuthOption = "auth";
    /**
     * Auth key for username string
     */
    public static final String kCBLReplicatorAuthUserName = "username";
    /**
     * Auth key for password string
     */
    public static final String kCBLReplicatorAuthPassword = "password";


    private Database database = null;
    private ReplicatorTarget target = null;
    private ReplicatorType type = PUSH_AND_PULL;
    private boolean continuous = false;
    private ConflictResolver resolver;
    private Map<String,Object> options;

    public ReplicatorConfiguration() {
        type = PUSH_AND_PULL;
        continuous = false;
        resolver = null;
        options  = null;
    }

    public ReplicatorConfiguration(Database database,
                                   ReplicatorTarget target,
                                   ReplicatorType type,
                                   boolean continuous,
                                   ConflictResolver resolver,
                                   Map<String,Object> options) {
        this.database = database;
        this.target = target;
        this.type = type;
        this.continuous = continuous;
        this.resolver = resolver;
        this.options = options;
    }

    public Database getDatabase() {
        return database;
    }

    public void setDatabase(Database database) {
        this.database = database;
    }

    public ReplicatorTarget getTarget() {
        return target;
    }

    public void setTarget(ReplicatorTarget target) {
        this.target = target;
    }

    public ReplicatorType getType() {
        return type;
    }

    public void setType(ReplicatorType type) {
        this.type = type;
    }

    public boolean isContinuous() {
        return continuous;
    }

    public void setContinuous(boolean continuous) {
        this.continuous = continuous;
    }

    public ConflictResolver getResolver() {
        return resolver;
    }

    public void setResolver(ConflictResolver resolver) {
        this.resolver = resolver;
    }

    public Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(Map<String, Object> options) {
        this.options = options;
    }

    public ReplicatorConfiguration copy() {
        return new ReplicatorConfiguration(database, target, type, continuous, resolver, options);
    }

    /*package*/ Map<String, Object> effectiveOptions() {

        Map<String, Object> options = this.options != null ? new HashMap<>(this.getOptions()) : new HashMap<String, Object>();

        // If the URL has a hardcoded username/password, add them as an "auth" option:
        if(target.getUri()!= null) {
            String username = URIUtils.getUsername(target.getUri());
            if (username != null && !options.containsKey(kCBLReplicatorAuthOption)) {
                Map<String, String> auth = new HashMap<>();
                auth.put(kCBLReplicatorAuthUserName, username);
                auth.put(kCBLReplicatorAuthPassword, URIUtils.getPassword(target.getUri()));
                if (options == null)
                    options = new HashMap<String, Object>();
                options.put(kCBLReplicatorAuthOption, auth);
            }
        }

        // Add the pinned certificate if any:

        // Add custom cookies if any:

        return options;
    }
}
