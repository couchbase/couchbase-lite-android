package com.couchbase.cblite.replicator;

import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;

import android.database.SQLException;
import android.util.Log;

import com.couchbase.cblite.CBLBody;
import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLMisc;
import com.couchbase.cblite.CBLRevision;
import com.couchbase.cblite.CBLRevisionList;
import com.couchbase.cblite.CBLServer;
import com.couchbase.cblite.CBLStatus;
import com.couchbase.cblite.replicator.changetracker.CBLChangeTracker;
import com.couchbase.cblite.replicator.changetracker.CBLChangeTrackerClient;
import com.couchbase.cblite.replicator.changetracker.CBLChangeTracker.TDChangeTrackerMode;
import com.couchbase.cblite.support.HttpClientFactory;
import com.couchbase.cblite.support.CBLBatchProcessor;
import com.couchbase.cblite.support.CBLBatcher;
import com.couchbase.cblite.support.CBLRemoteRequestCompletionBlock;
import com.couchbase.cblite.support.CBLSequenceMap;

public class CBLPuller extends CBLReplicator implements CBLChangeTrackerClient {

    private static final int MAX_OPEN_HTTP_CONNECTIONS = 16;

    protected CBLBatcher<List<Object>> downloadsToInsert;
    protected List<CBLRevision> revsToPull;
    protected CBLChangeTracker changeTracker;
    protected CBLSequenceMap pendingSequences;
    protected volatile int httpConnectionCount;

    public CBLPuller(CBLDatabase db, URL remote, boolean continuous, ScheduledExecutorService workExecutor) {
        this(db, remote, continuous, null, workExecutor);
    }

    public CBLPuller(CBLDatabase db, URL remote, boolean continuous, HttpClientFactory clientFactory, ScheduledExecutorService workExecutor) {
        super(db, remote, continuous, clientFactory, workExecutor);
    }

    @Override
    public void beginReplicating() {
        if(downloadsToInsert == null) {
            downloadsToInsert = new CBLBatcher<List<Object>>(workExecutor, 200, 1000, new CBLBatchProcessor<List<Object>>() {
                @Override
                public void process(List<List<Object>> inbox) {
                    insertRevisions(inbox);
                }
            });
        }
        pendingSequences = new CBLSequenceMap();
        Log.w(CBLDatabase.TAG, this + " starting ChangeTracker with since=" + lastSequence);
        changeTracker = new CBLChangeTracker(remote, continuous ? TDChangeTrackerMode.LongPoll : TDChangeTrackerMode.OneShot, lastSequence, this, this.httpContext);
        if(filterName != null) {
            changeTracker.setFilterName(filterName);
            if(filterParams != null) {
                changeTracker.setFilterParams(filterParams);
            }
        }
        if(!continuous) {
            asyncTaskStarted();
        }
        changeTracker.start();
    }

    @Override
    public void stop() {

        if(!running) {
            return;
        }

        if(changeTracker != null) {
	        changeTracker.setClient(null);  // stop it from calling my changeTrackerStopped()
	        changeTracker.stop();
	        changeTracker = null;
	        if(!continuous) {
	            asyncTaskFinished(1);  // balances asyncTaskStarted() in beginReplicating()
	        }
        }

        synchronized(this) {
            revsToPull = null;
        }

        super.stop();

        if(downloadsToInsert != null) {
            downloadsToInsert.flush();
        }
    }

    @Override
    public void stopped() {
        downloadsToInsert = null;
        super.stopped();
    }

    // Got a _changes feed entry from the CBLChangeTracker.
    @Override
    public void changeTrackerReceivedChange(Map<String, Object> change) {
        String lastSequence = change.get("seq").toString();
        String docID = (String)change.get("id");
        if(docID == null) {
            return;
        }
        if(!CBLDatabase.isValidDocumentId(docID)) {
            Log.w(CBLDatabase.TAG, String.format("%s: Received invalid doc ID from _changes: %s", this, change));
            return;
        }
        boolean deleted = (change.containsKey("deleted") && ((Boolean)change.get("deleted")).equals(Boolean.TRUE));
        List<Map<String,Object>> changes = (List<Map<String,Object>>)change.get("changes");
        for (Map<String, Object> changeDict : changes) {
            String revID = (String)changeDict.get("rev");
            if(revID == null) {
                continue;
            }
            TDPulledRevision rev = new TDPulledRevision(docID, revID, deleted, db);
            rev.setRemoteSequenceID(lastSequence);
            addToInbox(rev);
        }
        setChangesTotal(getChangesTotal() + changes.size());
        while(revsToPull != null && revsToPull.size() > 1000) {
            try {
                Thread.sleep(500);  // <-- TODO: why is this here?
            } catch(InterruptedException e) {

            }
        }
    }

    @Override
    public void changeTrackerStopped(CBLChangeTracker tracker) {
        Log.w(CBLDatabase.TAG, this + ": ChangeTracker stopped");
        //FIXME tracker doesnt have error right now
//        if(error == null && tracker.getError() != null) {
//            error = tracker.getError();
//        }
        changeTracker = null;
        if(batcher != null) {
            batcher.flush();
        }

        asyncTaskFinished(1);
    }

    @Override
    public HttpClient getHttpClient() {
    	HttpClient httpClient = this.clientFacotry.getHttpClient();

        return httpClient;
    }

    /**
     * Process a bunch of remote revisions from the _changes feed at once
     */
    @Override
    public void processInbox(CBLRevisionList inbox) {
        // Ask the local database which of the revs are not known to it:
        //Log.w(CBLDatabase.TAG, String.format("%s: Looking up %s", this, inbox));
        String lastInboxSequence = ((TDPulledRevision)inbox.get(inbox.size()-1)).getRemoteSequenceID();
        int total = getChangesTotal() - inbox.size();
        if(!db.findMissingRevisions(inbox)) {
            Log.w(CBLDatabase.TAG, String.format("%s failed to look up local revs", this));
            inbox = null;
        }
        //introducing this to java version since inbox may now be null everywhere
        int inboxCount = 0;
        if(inbox != null) {
            inboxCount = inbox.size();
        }
        if(getChangesTotal() != total + inboxCount) {
            setChangesTotal(total + inboxCount);
        }

        if(inboxCount == 0) {
            // Nothing to do. Just bump the lastSequence.
            Log.w(CBLDatabase.TAG, String.format("%s no new remote revisions to fetch", this));
            long seq = pendingSequences.addValue(lastInboxSequence);
            pendingSequences.removeSequence(seq);
            setLastSequence(pendingSequences.getCheckpointedValue());
            return;
        }

        Log.v(CBLDatabase.TAG, this + " fetching " + inboxCount + " remote revisions...");
        //Log.v(CBLDatabase.TAG, String.format("%s fetching remote revisions %s", this, inbox));

        // Dump the revs into the queue of revs to pull from the remote db:
        synchronized (this) {
	        if(revsToPull == null) {
	            revsToPull = new ArrayList<CBLRevision>(200);
	        }

	        for(int i=0; i < inbox.size(); i++) {
	            TDPulledRevision rev = (TDPulledRevision)inbox.get(i);
				// FIXME add logic here to pull initial revs in bulk
	            rev.setSequence(pendingSequences.addValue(rev.getRemoteSequenceID()));
	            revsToPull.add(rev);
	        }
		}

        pullRemoteRevisions();
    }

    /**
     * Start up some HTTP GETs, within our limit on the maximum simultaneous number
     *
     * The entire method is not synchronized, only the portion pulling work off the list
     * Important to not hold the synchronized block while we do network access
     */
    public void pullRemoteRevisions() {
        //find the work to be done in a synchronized block
        List<CBLRevision> workToStartNow = new ArrayList<CBLRevision>();
        synchronized (this) {
			while(httpConnectionCount + workToStartNow.size() < MAX_OPEN_HTTP_CONNECTIONS && revsToPull != null && revsToPull.size() > 0) {
				CBLRevision work = revsToPull.remove(0);
				workToStartNow.add(work);
			}
		}

        //actually run it outside the synchronized block
        for(CBLRevision work : workToStartNow) {
            pullRemoteRevision(work);
        }
    }

    /**
     * Fetches the contents of a revision from the remote db, including its parent revision ID.
     * The contents are stored into rev.properties.
     */
    public void pullRemoteRevision(final CBLRevision rev) {
        asyncTaskStarted();
        ++httpConnectionCount;

        // Construct a query. We want the revision history, and the bodies of attachments that have
        // been added since the latest revisions we have locally.
        // See: http://wiki.apache.org/couchdb/HTTP_Document_API#Getting_Attachments_With_a_Document
        StringBuilder path = new StringBuilder("/" + URLEncoder.encode(rev.getDocId()) + "?rev=" + URLEncoder.encode(rev.getRevId()) + "&revs=true&attachments=true");
        List<String> knownRevs = knownCurrentRevIDs(rev);
        if(knownRevs == null) {
            //this means something is wrong, possibly the replicator has shut down
            asyncTaskFinished(1);
            --httpConnectionCount;
            return;
        }
        if(knownRevs.size() > 0) {
            path.append("&atts_since=");
            path.append(joinQuotedEscaped(knownRevs));
        }

        //create a final version of this variable for the log statement inside
        //FIXME find a way to avoid this
        final String pathInside = path.toString();
        sendAsyncMultipartDownloaderRequest("GET", pathInside, null, db, new CBLRemoteRequestCompletionBlock() {

            @Override
            public void onCompletion(Object result, Throwable e) {
                // OK, now we've got the response revision:
                if(result != null) {
                    Map<String,Object> properties = (Map<String,Object>)result;
                    List<String> history = db.parseCouchDBRevisionHistory(properties);
                    if(history != null) {
                        rev.setProperties(properties);
                        // Add to batcher ... eventually it will be fed to -insertRevisions:.
                        List<Object> toInsert = new ArrayList<Object>();
                        toInsert.add(rev);
                        toInsert.add(history);
                        downloadsToInsert.queueObject(toInsert);
                        asyncTaskStarted();
                    } else {
                        Log.w(CBLDatabase.TAG, this + ": Missing revision history in response from " + pathInside);
                        setChangesProcessed(getChangesProcessed() + 1);
                    }
                } else {
                    if(e != null) {
                        Log.e(CBLDatabase.TAG, "Error pulling remote revision", e);
                        error = e;
                    }
                    setChangesProcessed(getChangesProcessed() + 1);
                }

                // Note that we've finished this task; then start another one if there
                // are still revisions waiting to be pulled:
                asyncTaskFinished(1);
                --httpConnectionCount;
                pullRemoteRevisions();
            }
        });

    }

    /**
     * This will be called when _revsToInsert fills up:
     */
    public void insertRevisions(List<List<Object>> revs) {
        Log.i(CBLDatabase.TAG, this + " inserting " + revs.size() + " revisions...");
        //Log.v(CBLDatabase.TAG, String.format("%s inserting %s", this, revs));

        /* Updating self.lastSequence is tricky. It needs to be the received sequence ID of the revision for which we've successfully received and inserted (or rejected) it and all previous received revisions. That way, next time we can start tracking remote changes from that sequence ID and know we haven't missed anything. */
        /* FIX: The current code below doesn't quite achieve that: it tracks the latest sequence ID we've successfully processed, but doesn't handle failures correctly across multiple calls to -insertRevisions. I think correct behavior will require keeping an NSMutableIndexSet to track the fake-sequences of all processed revisions; then we can find the first missing index in that set and not advance lastSequence past the revision with that fake-sequence. */
        Collections.sort(revs, new Comparator<List<Object>>() {

            public int compare(List<Object> list1, List<Object> list2) {
                CBLRevision reva = (CBLRevision)list1.get(0);
                CBLRevision revb = (CBLRevision)list2.get(0);
                return CBLMisc.TDSequenceCompare(reva.getSequence(), revb.getSequence());
            }

        });

        if(db == null) {
            asyncTaskFinished(revs.size());
            return;
        }
        db.beginTransaction();
        boolean success = false;
        try {
            for (List<Object> revAndHistory : revs) {
                TDPulledRevision rev = (TDPulledRevision)revAndHistory.get(0);
                long fakeSequence = rev.getSequence();
                List<String> history = (List<String>)revAndHistory.get(1);
                // Insert the revision:
                CBLStatus status = db.forceInsert(rev, history, remote);
                if(!status.isSuccessful()) {
                    if(status.getCode() == CBLStatus.FORBIDDEN) {
                        Log.i(CBLDatabase.TAG, this + ": Remote rev failed validation: " + rev);
                    } else {
                        Log.w(CBLDatabase.TAG, this + " failed to write " + rev + ": status=" + status.getCode());
                        error = new HttpResponseException(status.getCode(), null);
                        continue;
                    }
                }

                pendingSequences.removeSequence(fakeSequence);
            }

            Log.w(CBLDatabase.TAG, this + " finished inserting " + revs.size() + " revisions");

            setLastSequence(pendingSequences.getCheckpointedValue());

            success = true;
        } catch(SQLException e) {
            Log.w(CBLDatabase.TAG, this + ": Exception inserting revisions", e);
        } finally {
            db.endTransaction(success);
            asyncTaskFinished(revs.size());
        }

        setChangesProcessed(getChangesProcessed() + revs.size());
    }

    List<String> knownCurrentRevIDs(CBLRevision rev) {
        if(db != null) {
            return db.getAllRevisionsOfDocumentID(rev.getDocId(), true).getAllRevIds();
        }
        return null;
    }

    public String joinQuotedEscaped(List<String> strings) {
        if(strings.size() == 0) {
            return "[]";
        }
        byte[] json = null;
        try {
            json = CBLServer.getObjectMapper().writeValueAsBytes(strings);
        } catch (Exception e) {
            Log.w(CBLDatabase.TAG, "Unable to serialize json", e);
        }
        return URLEncoder.encode(new String(json));
    }

}

/**
 * A revision received from a remote server during a pull. Tracks the opaque remote sequence ID.
 */
class TDPulledRevision extends CBLRevision {

    public TDPulledRevision(CBLBody body, CBLDatabase database) {
        super(body, database);
    }

    public TDPulledRevision(String docId, String revId, boolean deleted, CBLDatabase database) {
        super(docId, revId, deleted, database);
    }

    public TDPulledRevision(Map<String, Object> properties, CBLDatabase database) {
        super(properties, database);
    }

    protected String remoteSequenceID;

    public String getRemoteSequenceID() {
        return remoteSequenceID;
    }

    public void setRemoteSequenceID(String remoteSequenceID) {
        this.remoteSequenceID = remoteSequenceID;
    }

}
