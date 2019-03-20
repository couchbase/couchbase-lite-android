//
// AbstractReplicatorConfiguration.java
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

import android.support.annotation.NonNull;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import com.couchbase.lite.internal.core.CBLVersion;


/**
 * Replicator configuration.
 */
abstract class AbstractReplicatorConfiguration {

    // Replicator option dictionary keys:
    static final String kC4ReplicatorOptionExtraHeaders = "headers";  // Extra HTTP headers; string[]
    static final String kC4ReplicatorOptionCookies = "cookies";  // HTTP Cookie header value; string
    static final String kCBLReplicatorAuthOption = "auth";       // Auth settings; Dict
    static final String kC4ReplicatorOptionPinnedServerCert = "pinnedCert";  // Cert or public key; data
    static final String kC4ReplicatorOptionDocIDs = "docIDs";   // Docs to replicate; string[]
    static final String kC4ReplicatorOptionChannels = "channels"; // SG channel names; string[]
    static final String kC4ReplicatorOptionFilter = "filter";   // Filter name; string
    static final String kC4ReplicatorOptionFilterParams = "filterParams";  // Filter params; Dict[string]
    static final String kC4ReplicatorOptionSkipDeleted = "skipDeleted"; // Don't push/pull tombstones; bool
    static final String kC4ReplicatorOptionNoConflicts = "noConflicts"; // Puller rejects conflicts; bool
    static final String kC4ReplicatorOptionCheckpointInterval = "checkpointInterval"; // How often to checkpoint, in
    // seconds; number
    static final String kC4ReplicatorOptionRemoteDBUniqueID = "remoteDBUniqueID"; // How often to checkpoint, in
    // seconds; number
    static final String kC4ReplicatorResetCheckpoint = "reset"; // reset remote checkpoint
    static final String kC4ReplicatorOptionProgressLevel = "progress";  //< If >=1, notify on every doc; if >=2, on
    // every attachment (int)

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
    private Database database;
    private ReplicatorType replicatorType;
    private boolean continuous;
    private Authenticator authenticator;
    private Map<String, String> headers;
    private byte[] pinnedServerCertificate;
    private List<String> channels;
    private List<String> documentIDs;
    private ReplicationFilter pushFilter;
    private ReplicationFilter pullFilter;

    protected boolean readonly;
    protected Endpoint target;

    //---------------------------------------------
    // Constructors
    //---------------------------------------------

    AbstractReplicatorConfiguration(@NonNull AbstractReplicatorConfiguration config) {
        if (config == null) { throw new IllegalArgumentException("config cannot be null."); }

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
        this.pullFilter = config.pullFilter;
        this.pushFilter = config.pushFilter;
    }

    protected AbstractReplicatorConfiguration(@NonNull Database database, @NonNull Endpoint target) {
        if (database == null) { throw new IllegalArgumentException("config cannot be null."); }
        if (target == null) { throw new IllegalArgumentException("target cannot be null."); }

        this.readonly = false;
        this.replicatorType = ReplicatorType.PUSH_AND_PULL;
        this.database = database;
        this.target = target;
    }

    //---------------------------------------------
    // Setters
    //---------------------------------------------

    /**
     * Sets the authenticator to authenticate with a remote target server.
     * Currently there are two types of the authenticators,
     * BasicAuthenticator and SessionAuthenticator, supported.
     *
     * @param authenticator The authenticator.
     * @return The self object.
     */
    @NonNull
    public ReplicatorConfiguration setAuthenticator(@NonNull Authenticator authenticator) {
        if (readonly) { throw new IllegalStateException("ReplicatorConfiguration is readonly mode."); }
        this.authenticator = authenticator;
        return getReplicatorConfiguration();
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
    @NonNull
    public ReplicatorConfiguration setChannels(List<String> channels) {
        if (readonly) { throw new IllegalStateException("ReplicatorConfiguration is readonly mode."); }
        this.channels = channels;
        return getReplicatorConfiguration();
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
    @NonNull
    public ReplicatorConfiguration setContinuous(boolean continuous) {
        if (readonly) { throw new IllegalStateException("ReplicatorConfiguration is readonly mode."); }
        this.continuous = continuous;
        return getReplicatorConfiguration();
    }

    /**
     * Sets a set of document IDs to filter by: if given, only documents
     * with these IDs will be pushed and/or pulled.
     *
     * @param documentIDs The document IDs.
     * @return The self object.
     */
    @NonNull
    public ReplicatorConfiguration setDocumentIDs(List<String> documentIDs) {
        if (readonly) { throw new IllegalStateException("ReplicatorConfiguration is readonly mode."); }
        this.documentIDs = documentIDs;
        return getReplicatorConfiguration();
    }

    /**
     * Sets the extra HTTP headers to send in all requests to the remote target.
     *
     * @param headers The HTTP Headers.
     * @return The self object.
     */
    @NonNull
    public ReplicatorConfiguration setHeaders(Map<String, String> headers) {
        if (readonly) { throw new IllegalStateException("ReplicatorConfiguration is readonly mode."); }
        this.headers = new HashMap<>(headers);
        return getReplicatorConfiguration();
    }

    /**
     * Sets the target server's SSL certificate.
     * <p>
     * !!FIXME: This method stores a mutable array as private data
     *
     * @param pinnedServerCertificate the SSL certificate.
     * @return The self object.
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    @NonNull
    public ReplicatorConfiguration setPinnedServerCertificate(byte[] pinnedServerCertificate) {
        if (readonly) { throw new IllegalStateException("ReplicatorConfiguration is readonly mode."); }
        this.pinnedServerCertificate = pinnedServerCertificate;
        return getReplicatorConfiguration();
    }

    /**
     * Sets a filter object for validating whether the documents can be pulled from the
     * remote endpoint. Only documents for which the object returns true are replicated.
     *
     * @param pullFilter The filter to filter the document to be pulled.
     * @return The self object.
     */
    @NonNull
    public ReplicatorConfiguration setPullFilter(ReplicationFilter pullFilter) {
        if (readonly) { throw new IllegalStateException("ReplicatorConfiguration is readonly mode."); }
        this.pullFilter = pullFilter;
        return getReplicatorConfiguration();
    }

    /**
     * Sets a filter object for validating whether the documents can be pushed
     * to the remote endpoint.
     *
     * @param pushFilter The filter to filter the document to be pushed.
     * @return The self object.
     */
    @NonNull
    public ReplicatorConfiguration setPushFilter(ReplicationFilter pushFilter) {
        if (readonly) { throw new IllegalStateException("ReplicatorConfiguration is readonly mode."); }
        this.pushFilter = pushFilter;
        return getReplicatorConfiguration();
    }

    /**
     * Sets the replicator type indicating the direction of the replicator.
     * The default value is .pushAndPull which is bidrectional.
     *
     * @param replicatorType The replicator type.
     * @return The self object.
     */
    @NonNull
    public ReplicatorConfiguration setReplicatorType(@NonNull ReplicatorType replicatorType) {
        if (replicatorType == null) { throw new IllegalArgumentException("replicatorType cannot be null."); }
        if (readonly) { throw new IllegalStateException("ReplicatorConfiguration is readonly mode."); }
        this.replicatorType = replicatorType;
        return getReplicatorConfiguration();
    }

    //---------------------------------------------
    // Getters
    //---------------------------------------------

    /**
     * Return the Authenticator to authenticate with a remote target.
     */
    public Authenticator getAuthenticator() {
        return authenticator;
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
     * Return the continuous flag indicating whether the replicator should stay
     * active indefinitely to replicate changed documents.
     */
    public boolean isContinuous() {
        return continuous;
    }

    /**
     * Return the local database to replicate with the replication target.
     */
    @NonNull
    public Database getDatabase() {
        return database;
    }

    /**
     * A set of document IDs to filter by: if not nil, only documents with these IDs will be pushed
     * and/or pulled.
     */
    public List<String> getDocumentIDs() {
        return documentIDs;
    }

    /**
     * Return Extra HTTP headers to send in all requests to the remote target.
     */
    public Map<String, String> getHeaders() {
        return headers;
    }

    /**
     * Return the remote target's SSL certificate.
     * <p>
     * !!FIXME: This method returns a writeable copy of its private data
     */
    @SuppressFBWarnings("EI_EXPOSE_REP")
    public byte[] getPinnedServerCertificate() {
        return pinnedServerCertificate;
    }

    /**
     * Gets a filter object for validating whether the documents can be pulled
     * from the remote endpoint.
     */
    public ReplicationFilter getPullFilter() { return pullFilter; }

    /**
     * Gets a filter object for validating whether the documents can be pushed
     * to the remote endpoint.
     */
    public ReplicationFilter getPushFilter() { return pushFilter; }

    /**
     * Return Replicator type indicating the direction of the replicator.
     */
    @NonNull
    public ReplicatorType getReplicatorType() {
        return replicatorType;
    }

    /**
     * Return the replication target to replicate with.
     */
    @NonNull
    public Endpoint getTarget() {
        return target;
    }


    //---------------------------------------------
    // Package level access
    //---------------------------------------------

    abstract Database getTargetDatabase();

    abstract ReplicatorConfiguration getReplicatorConfiguration();

    ReplicatorConfiguration readonlyCopy() {
        final ReplicatorConfiguration config = new ReplicatorConfiguration(getReplicatorConfiguration());
        config.readonly = true;
        return config;
    }

    Map<String, Object> effectiveOptions() {
        final Map<String, Object> options = new HashMap<>();

        if (authenticator != null) { authenticator.authenticate(options); }

        // Add the pinned certificate if any:
        if (pinnedServerCertificate != null) {
            options.put(
                kC4ReplicatorOptionPinnedServerCert,
                pinnedServerCertificate);
        }

        if (documentIDs != null && documentIDs.size() > 0) { options.put(kC4ReplicatorOptionDocIDs, documentIDs); }

        if (channels != null && channels.size() > 0) { options.put(kC4ReplicatorOptionChannels, channels); }

        final Map<String, Object> httpHeaders = new HashMap<>();
        // User-Agent:
        httpHeaders.put("User-Agent", CBLVersion.getUserAgent());
        // headers
        if (headers != null && headers.size() > 0) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                httpHeaders.put(
                    entry.getKey(),
                    entry.getValue());
            }
        }
        options.put(kC4ReplicatorOptionExtraHeaders, httpHeaders);

        return options;
    }

    URI getTargetURI() {
        return (!(target instanceof URLEndpoint)) ? null : ((URLEndpoint) target).getURL();
    }
}
