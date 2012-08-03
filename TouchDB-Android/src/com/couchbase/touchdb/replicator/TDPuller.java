package com.couchbase.touchdb.replicator;

import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;

import android.database.SQLException;
import android.util.Log;

import com.couchbase.touchdb.TDBody;
import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDMisc;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDRevisionList;
import com.couchbase.touchdb.TDServer;
import com.couchbase.touchdb.TDStatus;
import com.couchbase.touchdb.replicator.changetracker.TDChangeTracker;
import com.couchbase.touchdb.replicator.changetracker.TDChangeTracker.TDChangeTrackerMode;
import com.couchbase.touchdb.replicator.changetracker.TDChangeTrackerClient;
import com.couchbase.touchdb.support.HttpClientFactory;
import com.couchbase.touchdb.support.TDBatchProcessor;
import com.couchbase.touchdb.support.TDBatcher;
import com.couchbase.touchdb.support.TDRemoteRequestCompletionBlock;

public class TDPuller extends TDReplicator implements TDChangeTrackerClient {

    private static final int MAX_OPEN_HTTP_CONNECTIONS = 16;

    protected TDBatcher<List<Object>> downloadsToInsert;
    protected List<TDRevision> revsToPull;
    protected long nextFakeSequence;
    protected long maxInsertedFakeSequence;
    protected TDChangeTracker changeTracker;

    protected int httpConnectionCount;

    public TDPuller(TDDatabase db, URL remote, boolean continuous) {
        this(db, remote, continuous, null);
    }

    public TDPuller(TDDatabase db, URL remote, boolean continuous, HttpClientFactory clientFactory) {
        super(db, remote, continuous, clientFactory);
    }

    @Override
    public void beginReplicating() {
        if(downloadsToInsert == null) {
            downloadsToInsert = new TDBatcher<List<Object>>(db.getHandler(), 200, 1000, new TDBatchProcessor<List<Object>>() {
                @Override
                public void process(List<List<Object>> inbox) {
                    insertRevisions(inbox);
                }
            });
        }
        nextFakeSequence = maxInsertedFakeSequence = 0;
        Log.w(TDDatabase.TAG, this + " starting ChangeTracker with since=" + lastSequence);
        changeTracker = new TDChangeTracker(remote, continuous ? TDChangeTrackerMode.LongPoll : TDChangeTrackerMode.OneShot, lastSequence, this);
        if(filterName != null) {
            changeTracker.setFilterName(filterName);
            if(filterParams != null) {
                changeTracker.setFilterParams(filterParams);
            }
        }
        changeTracker.start();
        asyncTaskStarted();
    }

    @Override
    public void stop() {

        if(!running) {
            return;
        }

        changeTracker.setClient(null);  // stop it from calling my changeTrackerStopped()
        changeTracker.stop();
        changeTracker = null;

        synchronized(this) {
            revsToPull = null;
        }

        super.stop();

        downloadsToInsert.flush();
    }

    @Override
    public void stopped() {

        downloadsToInsert.flush();
        downloadsToInsert.close();

        super.stopped();
    }

    // Got a _changes feed entry from the TDChangeTracker.
    @Override
    public void changeTrackerReceivedChange(Map<String, Object> change) {
        String lastSequence = change.get("seq").toString();
        String docID = (String)change.get("id");
        if(docID == null) {
            return;
        }
        if(!TDDatabase.isValidDocumentId(docID)) {
            Log.w(TDDatabase.TAG, String.format("%s: Received invalid doc ID from _changes: %s", this, change));
            return;
        }
        boolean deleted = (change.containsKey("deleted") && ((Boolean)change.get("deleted")).equals(Boolean.TRUE));
        List<Map<String,Object>> changes = (List<Map<String,Object>>)change.get("changes");
        for (Map<String, Object> changeDict : changes) {
            String revID = (String)changeDict.get("rev");
            if(revID == null) {
                continue;
            }
            TDPulledRevision rev = new TDPulledRevision(docID, revID, deleted);
            rev.setRemoteSequenceID(lastSequence);
            rev.setSequence(++nextFakeSequence);
            addToInbox(rev);
        }
        setChangesTotal(getChangesTotal() + changes.size());
    }

    @Override
    public void changeTrackerStopped(TDChangeTracker tracker) {
        Log.w(TDDatabase.TAG, this + ": ChangeTracker stopped");
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
    public void processInbox(TDRevisionList inbox) {
        // Ask the local database which of the revs are not known to it:
        //Log.w(TDDatabase.TAG, String.format("%s: Looking up %s", this, inbox));
        String lastInboxSequence = ((TDPulledRevision)inbox.get(inbox.size()-1)).getRemoteSequenceID();
        int total = getChangesTotal() - inbox.size();
        if(!db.findMissingRevisions(inbox)) {
            Log.w(TDDatabase.TAG, String.format("%s failed to look up local revs", this));
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
            Log.w(TDDatabase.TAG, String.format("%s no new remote revisions to fetch", this));
            setLastSequence(lastInboxSequence);
            return;
        }

        Log.v(TDDatabase.TAG, this + " fetching " + inboxCount + " remote revisions...");
        //Log.v(TDDatabase.TAG, String.format("%s fetching remote revisions %s", this, inbox));

        // Dump the revs into the queue of revs to pull from the remote db:
        if(revsToPull == null) {
            revsToPull = new ArrayList<TDRevision>(200);
        }
        revsToPull.addAll(inbox);

        pullRemoteRevisions();

        //TEST
        //adding wait here to prevent revsToPull from getting too large
        while(revsToPull != null && revsToPull.size() > 1000) {
            pullRemoteRevisions();
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                //wake up
            }
        }
    }

    /**
     * Start up some HTTP GETs, within our limit on the maximum simultaneous number
     *
     * Needs to be synchronized because multiple RemoteRequest theads call this upon completion
     * to keep the process moving, need to synchronize check for size with removal
     */
    public synchronized void pullRemoteRevisions() {
        while(httpConnectionCount < MAX_OPEN_HTTP_CONNECTIONS && revsToPull != null && revsToPull.size() > 0) {
            pullRemoteRevision(revsToPull.get(0));
            revsToPull.remove(0);
        }
    }

    /**
     * Fetches the contents of a revision from the remote db, including its parent revision ID.
     * The contents are stored into rev.properties.
     */
    public void pullRemoteRevision(final TDRevision rev) {
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
        sendAsyncRequest("GET", pathInside, null, new TDRemoteRequestCompletionBlock() {

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
                        Log.w(TDDatabase.TAG, this + ": Missing revision history in response from " + pathInside);
                        setChangesProcessed(getChangesProcessed() + 1);
                    }
                } else {
                    if(e != null) {
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
        Log.i(TDDatabase.TAG, this + " inserting " + revs.size() + " revisions...");
        //Log.v(TDDatabase.TAG, String.format("%s inserting %s", this, revs));

        /* Updating self.lastSequence is tricky. It needs to be the received sequence ID of the revision for which we've successfully received and inserted (or rejected) it and all previous received revisions. That way, next time we can start tracking remote changes from that sequence ID and know we haven't missed anything. */
        /* FIX: The current code below doesn't quite achieve that: it tracks the latest sequence ID we've successfully processed, but doesn't handle failures correctly across multiple calls to -insertRevisions. I think correct behavior will require keeping an NSMutableIndexSet to track the fake-sequences of all processed revisions; then we can find the first missing index in that set and not advance lastSequence past the revision with that fake-sequence. */
        Collections.sort(revs, new Comparator<List<Object>>() {

            public int compare(List<Object> list1, List<Object> list2) {
                TDRevision reva = (TDRevision)list1.get(0);
                TDRevision revb = (TDRevision)list2.get(0);
                return TDMisc.TDSequenceCompare(reva.getSequence(), revb.getSequence());
            }

        });

        boolean allGood = true;
        TDPulledRevision lastGoodRev = null;

        if(db == null) {
            return;
        }
        db.beginTransaction();
        boolean success = false;
        try {
            for (List<Object> revAndHistory : revs) {
                TDPulledRevision rev = (TDPulledRevision)revAndHistory.get(0);
                List<String> history = (List<String>)revAndHistory.get(1);
                // Insert the revision:
                TDStatus status = db.forceInsert(rev, history, remote);
                if(!status.isSuccessful()) {
                    if(status.getCode() == TDStatus.FORBIDDEN) {
                        Log.i(TDDatabase.TAG, this + ": Remote rev failed validation: " + rev);
                    } else {
                        Log.w(TDDatabase.TAG, this + " failed to write " + rev + ": status=" + status.getCode());
                        error = new HttpResponseException(status.getCode(), null);
                        allGood = false; // stop advancing lastGoodRev
                    }
                }

                if(allGood) {
                    lastGoodRev = rev;
                }
            }

            // Now update lastSequence from the latest consecutively inserted revision:
            long lastGoodFakeSequence = lastGoodRev.getSequence();
            if(lastGoodFakeSequence > maxInsertedFakeSequence) {
                maxInsertedFakeSequence = lastGoodFakeSequence;
                setLastSequence(lastGoodRev.getRemoteSequenceID());
            }

            Log.w(TDDatabase.TAG, this + " finished inserting " + revs.size() + " revisions");
            success = true;
        } catch(SQLException e) {
            Log.w(TDDatabase.TAG, this + ": Exception inserting revisions", e);
        } finally {
            db.endTransaction(success);
            asyncTaskFinished(revs.size());
        }

        setChangesProcessed(getChangesProcessed() + revs.size());
    }

    List<String> knownCurrentRevIDs(TDRevision rev) {
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
            json = TDServer.getObjectMapper().writeValueAsBytes(strings);
        } catch (Exception e) {
            Log.w(TDDatabase.TAG, "Unable to serialize json", e);
        }
        return URLEncoder.encode(new String(json));
    }

}

/**
 * A revision received from a remote server during a pull. Tracks the opaque remote sequence ID.
 */
class TDPulledRevision extends TDRevision {

    public TDPulledRevision(TDBody body) {
        super(body);
    }

    public TDPulledRevision(String docId, String revId, boolean deleted) {
        super(docId, revId, deleted);
    }

    public TDPulledRevision(Map<String, Object> properties) {
        super(properties);
    }

    protected String remoteSequenceID;

    public String getRemoteSequenceID() {
        return remoteSequenceID;
    }

    public void setRemoteSequenceID(String remoteSequenceID) {
        this.remoteSequenceID = remoteSequenceID;
    }

}
