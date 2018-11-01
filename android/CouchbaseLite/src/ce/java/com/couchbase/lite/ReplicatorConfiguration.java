//
// ReplicatorConfiguration.java
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
package com.couchbase.lite;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
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
    static final String kC4ReplicatorOptionRemoteDBUniqueID = "remoteDBUniqueID"; // How often to checkpoint, in seconds; number
    static final String kC4ReplicatorResetCheckpoint = "reset"; // reset remote checkpoint
    static final String kC4ReplicatorOptionNoDeltas = "noDeltas";

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
    private Authenticator authenticator = null;
    private Map<String, String> headers = null;
    private byte[] pinnedServerCertificate = null;
    private List<String> channels = null;
    private List<String> documentIDs = null;
    private boolean deltaSyncEnabled = true;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    public ReplicatorConfiguration(ReplicatorConfiguration config) {
        this.readonly = false;
        this.database = config.database;
        this.target = config.target;
        this.replicatorType = config.replicatorType;
        this.continuous = config.continuous;
        this.authenticator = config.authenticator;
        this.pinnedServerCertificate = config.pinnedServerCertificate;
        this.headers = config.headers;
        this.channels = config.channels;
        this.documentIDs = config.documentIDs;
        this.deltaSyncEnabled = config.deltaSyncEnabled;
    }

    public ReplicatorConfiguration(Database database, Endpoint target) {
        this.readonly = false;
        this.replicatorType = PUSH_AND_PULL;
        this.database = database;
        this.target = target;
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

    /**
     * Sets boolean value to indicate the delta sync is enabled or not.
     * The default value is true.
     * @param enable The boolean enable.
     * @return The self object.
     */
    public ReplicatorConfiguration setDeltaSyncEnabled(boolean enable){
        if (readonly)
            throw new IllegalStateException("ReplicatorConfiguration is readonly mode.");
        this.deltaSyncEnabled = enable;
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

    /**
     * Return the deltaSyncEnabled flag indicating the delta sync is enabled or not.
     */
    public boolean isDeltaSyncEnabled() {
        return deltaSyncEnabled;
    }

    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    ReplicatorConfiguration readonlyCopy() {
        ReplicatorConfiguration config = new ReplicatorConfiguration(this);
        config.readonly = true;
        return config;
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

        if (deltaSyncEnabled)
            options.put(kC4ReplicatorOptionNoDeltas, !deltaSyncEnabled);

        if (channels != null && channels.size() > 0)
            options.put(kC4ReplicatorOptionChannels, channels);


        Map<String, Object> httpHeaders = new HashMap<>();
        // User-Agent:
        httpHeaders.put("User-Agent", CBLVersion.getUserAgent());
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
        return null;
    }
}
