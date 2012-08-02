package com.couchbase.touchdb.replicator;

import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import org.apache.http.client.HttpResponseException;

import android.util.Log;

import com.couchbase.touchdb.TDDatabase;
import com.couchbase.touchdb.TDFilterBlock;
import com.couchbase.touchdb.TDRevision;
import com.couchbase.touchdb.TDRevisionList;
import com.couchbase.touchdb.TDStatus;
import com.couchbase.touchdb.support.HttpClientFactory;
import com.couchbase.touchdb.support.TDRemoteRequestCompletionBlock;

public class TDPusher extends TDReplicator implements Observer {

    private boolean createTarget;
    private boolean observing;
    private TDFilterBlock filter;

    public TDPusher(TDDatabase db, URL remote, boolean continuous) {
        this(db, remote, continuous, null);
    }

    public TDPusher(TDDatabase db, URL remote, boolean continuous, HttpClientFactory clientFactory) {
        super(db, remote, continuous, clientFactory);
        createTarget = false;
        observing = false;
    }

    public void setCreateTarget(boolean createTarget) {
        this.createTarget = createTarget;
    }

    public void setFilter(TDFilterBlock filter) {
        this.filter = filter;
    }

    @Override
    public boolean isPush() {
        return true;
    }

    @Override
    public void maybeCreateRemoteDB() {
        if(!createTarget) {
            return;
        }
        Log.v(TDDatabase.TAG, "Remote db might not exist; creating it...");
        sendAsyncRequest("PUT", "", null, new TDRemoteRequestCompletionBlock() {

            @Override
            public void onCompletion(Object result, Throwable e) {
                if(e != null && e instanceof HttpResponseException && ((HttpResponseException)e).getStatusCode() != 412) {
                    Log.e(TDDatabase.TAG, "Failed to create remote db", e);
                    error = e;
                    stop();
                } else {
                    Log.v(TDDatabase.TAG, "Created remote db");
                    createTarget = false;
                    beginReplicating();
                }
            }

        });
    }

    @Override
    public void beginReplicating() {
        // If we're still waiting to create the remote db, do nothing now. (This method will be
        // re-invoked after that request finishes; see maybeCreateRemoteDB() above.)
        if(createTarget) {
            return;
        }

        if(filterName != null) {
            filter = db.getFilterNamed(filterName);
        }
        if(filterName != null && filter == null) {
            Log.w(TDDatabase.TAG, String.format("%s: No TDFilterBlock registered for filter '%s'; ignoring", this, filterName));;
        }

        // Process existing changes since the last push:
        long lastSequenceLong = 0;
        if(lastSequence != null) {
            lastSequenceLong = Long.parseLong(lastSequence);
        }
        TDRevisionList changes = db.changesSince(lastSequenceLong, null, filter);
        if(changes.size() > 0) {
            processInbox(changes);
        }

        // Now listen for future changes (in continuous mode):
        if(continuous) {
            observing = true;
            db.addObserver(this);
            asyncTaskStarted();  // prevents stopped() from being called when other tasks finish
        }
    }

    @Override
    public void stop() {
        stopObserving();
        super.stop();
    }

    private void stopObserving() {
        if(observing) {
            observing = false;
            db.deleteObserver(this);
            asyncTaskFinished(1);
        }
    }

    @Override
    public void update(Observable observable, Object data) {
        //make sure this came from where we expected
        if(observable == db) {
            Map<String,Object> change = (Map<String,Object>)data;
            // Skip revisions that originally came from the database I'm syncing to:
            URL source = (URL)change.get("source");
            if(source != null && source.equals(remote.toExternalForm())) {
                return;
            }
            TDRevision rev = (TDRevision)change.get("rev");
            if(rev != null && ((filter == null) || filter.filter(rev))) {
                addToInbox(rev);
            }
        }

    }

    @Override
    public void processInbox(final TDRevisionList inbox) {
        final long lastInboxSequence = inbox.get(inbox.size()-1).getSequence();
        // Generate a set of doc/rev IDs in the JSON format that _revs_diff wants:
        Map<String,List<String>> diffs = new HashMap<String,List<String>>();
        for (TDRevision rev : inbox) {
            String docID = rev.getDocId();
            List<String> revs = diffs.get(docID);
            if(revs == null) {
                revs = new ArrayList<String>();
                diffs.put(docID, revs);
            }
            revs.add(rev.getRevId());
        }

        // Call _revs_diff on the target db:
        asyncTaskStarted();
        sendAsyncRequest("POST", "/_revs_diff", diffs, new TDRemoteRequestCompletionBlock() {

            @Override
            public void onCompletion(Object response, Throwable e) {
                Map<String,Object> results = (Map<String,Object>)response;
                if(e != null) {
                    error = e;
                    stop();
                } else if(results.size() != 0) {
                    // Go through the list of local changes again, selecting the ones the destination server
                    // said were missing and mapping them to a JSON dictionary in the form _bulk_docs wants:
                    List<Object> docsToSend = new ArrayList<Object>();
                    for(TDRevision rev : inbox) {
                        Map<String,Object> properties = null;
                        Map<String,Object> resultDoc = (Map<String,Object>)results.get(rev.getDocId());
                        if(resultDoc != null) {
                            List<String> revs = (List<String>)resultDoc.get("missing");
                            if(revs != null && revs.contains(rev.getRevId())) {
                                //remote server needs this revision
                                // Get the revision's properties
                                if(rev.isDeleted()) {
                                    properties = new HashMap<String,Object>();
                                    properties.put("_id", rev.getDocId());
                                    properties.put("_rev", rev.getRevId());
                                    properties.put("_deleted", true);
                                } else {
                                    // OPT: Shouldn't include all attachment bodies, just ones that have changed
                                    // OPT: Should send docs with many or big attachments as multipart/related
                                    TDStatus status = db.loadRevisionBody(rev, EnumSet.of(TDDatabase.TDContentOptions.TDIncludeAttachments));
                                    if(!status.isSuccessful()) {
                                        Log.w(TDDatabase.TAG, String.format("%s: Couldn't get local contents of %s", this, rev));
                                    } else {
                                        properties = new HashMap<String,Object>(rev.getProperties());
                                    }
                                }
                                if(properties != null) {
                                    // Add the _revisions list:
                                    properties.put("_revisions", db.getRevisionHistoryDict(rev));
                                    //now add it to the docs to send
                                    docsToSend.add(properties);
                                }
                            }
                        }
                    }

                    // Post the revisions to the destination. "new_edits":false means that the server should
                    // use the given _rev IDs instead of making up new ones.
                    final int numDocsToSend = docsToSend.size();
                    Map<String,Object> bulkDocsBody = new HashMap<String,Object>();
                    bulkDocsBody.put("docs", docsToSend);
                    bulkDocsBody.put("new_edits", false);
                    Log.i(TDDatabase.TAG, String.format("%s: Sending %d revisions", this, numDocsToSend));
                    Log.v(TDDatabase.TAG, String.format("%s: Sending %s", this, inbox));
                    setChangesTotal(getChangesTotal() + numDocsToSend);
                    asyncTaskStarted();
                    sendAsyncRequest("POST", "/_bulk_docs", bulkDocsBody, new TDRemoteRequestCompletionBlock() {

                        @Override
                        public void onCompletion(Object result, Throwable e) {
                            if(e != null) {
                                error = e;
                            } else {
                                Log.v(TDDatabase.TAG, String.format("%s: Sent %s", this, inbox));
                                setLastSequence(String.format("%d", lastInboxSequence));
                            }
                            setChangesProcessed(getChangesProcessed() + numDocsToSend);
                            asyncTaskFinished(1);
                        }
                    });

                } else {
                    // If none of the revisions are new to the remote, just bump the lastSequence:
                    setLastSequence(String.format("%d", lastInboxSequence));
                }
                asyncTaskFinished(1);
            }

        });
    }

}
