package com.couchbase.cblite.router;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

import android.util.Log;

import com.couchbase.cblite.CBLAttachment;
import com.couchbase.cblite.CBLBody;
import com.couchbase.cblite.CBLChangesOptions;
import com.couchbase.cblite.CBLDatabase;
import com.couchbase.cblite.CBLFilterBlock;
import com.couchbase.cblite.CBLMisc;
import com.couchbase.cblite.CBLQueryOptions;
import com.couchbase.cblite.CBLRevision;
import com.couchbase.cblite.CBLRevisionList;
import com.couchbase.cblite.CBLServer;
import com.couchbase.cblite.CBLStatus;
import com.couchbase.cblite.CBLView;
import com.couchbase.cblite.CBLViewMapBlock;
import com.couchbase.cblite.CBLViewReduceBlock;
import com.couchbase.cblite.CBLiteVersion;
import com.couchbase.cblite.CBLDatabase.TDContentOptions;
import com.couchbase.cblite.CBLView.TDViewCollation;
import com.couchbase.cblite.replicator.CBLPusher;
import com.couchbase.cblite.replicator.CBLReplicator;


public class CBLRouter implements Observer {

    private CBLServer server;
    private CBLDatabase db;
    private CBLURLConnection connection;
    private Map<String,String> queries;
    private boolean changesIncludesDocs = false;
    private CBLRouterCallbackBlock callbackBlock;
    private boolean responseSent = false;
    private boolean waiting = false;
    private CBLFilterBlock changesFilter;
    private boolean longpoll = false;

    public static String getVersionString() {
        return CBLiteVersion.CBLiteVersionNumber;
    }

    public CBLRouter(CBLServer server, CBLURLConnection connection) {
        this.server = server;
        this.connection = connection;
    }

    public void setCallbackBlock(CBLRouterCallbackBlock callbackBlock) {
        this.callbackBlock = callbackBlock;
    }

    public Map<String,String> getQueries() {
        if(queries == null) {
            String queryString = connection.getURL().getQuery();
            if(queryString != null && queryString.length() > 0) {
                queries = new HashMap<String,String>();
                for (String component : queryString.split("&")) {
                    int location = component.indexOf('=');
                    if(location > 0) {
                        String key = component.substring(0, location);
                        String value = component.substring(location + 1);
                        queries.put(key, value);
                    }
                }

            }
        }
        return queries;
    }

    public String getQuery(String param) {
        Map<String,String> queries = getQueries();
        if(queries != null) {
            String value = queries.get(param);
            if(value != null) {
                return URLDecoder.decode(value);
            }
        }
        return null;
    }

    public boolean getBooleanQuery(String param) {
        String value = getQuery(param);
        return (value != null) && !"false".equals(value) && !"0".equals(value);
    }

    public int getIntQuery(String param, int defaultValue) {
        int result = defaultValue;
        String value = getQuery(param);
        if(value != null) {
            try {
                result = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                //ignore, will return default value
            }
        }

        return result;
    }

    public Object getJSONQuery(String param) {
        String value = getQuery(param);
        if(value == null) {
            return null;
        }
        Object result = null;
        try {
            result = CBLServer.getObjectMapper().readValue(value, Object.class);
        } catch (Exception e) {
            Log.w("Unable to parse JSON Query", e);
        }
        return result;
    }

    public boolean cacheWithEtag(String etag) {
        String eTag = String.format("\"%s\"", etag);
        connection.getResHeader().add("Etag", eTag);
        String requestIfNoneMatch = connection.getRequestProperty("If-None-Match");
        return eTag.equals(requestIfNoneMatch);
    }

    public Map<String,Object> getBodyAsDictionary() {
        try {
            InputStream contentStream = connection.getRequestInputStream();
            Map<String,Object> bodyMap = CBLServer.getObjectMapper().readValue(contentStream, Map.class);
            return bodyMap;
        } catch (IOException e) {
            return null;
        }
    }

    public EnumSet<TDContentOptions> getContentOptions() {
        EnumSet<TDContentOptions> result = EnumSet.noneOf(TDContentOptions.class);
        if(getBooleanQuery("attachments")) {
            result.add(TDContentOptions.TDIncludeAttachments);
        }
        if(getBooleanQuery("local_seq")) {
            result.add(TDContentOptions.TDIncludeLocalSeq);
        }
        if(getBooleanQuery("conflicts")) {
            result.add(TDContentOptions.TDIncludeConflicts);
        }
        if(getBooleanQuery("revs")) {
            result.add(TDContentOptions.TDIncludeRevs);
        }
        if(getBooleanQuery("revs_info")) {
            result.add(TDContentOptions.TDIncludeRevsInfo);
        }
        return result;
    }

    public boolean getQueryOptions(CBLQueryOptions options) {
        // http://wiki.apache.org/couchdb/HTTP_view_API#Querying_Options
        options.setSkip(getIntQuery("skip", options.getSkip()));
        options.setLimit(getIntQuery("limit", options.getLimit()));
        options.setGroupLevel(getIntQuery("group_level", options.getGroupLevel()));
        options.setDescending(getBooleanQuery("descending"));
        options.setIncludeDocs(getBooleanQuery("include_docs"));
        options.setUpdateSeq(getBooleanQuery("update_seq"));
        if(getQuery("inclusive_end") != null) {
            options.setInclusiveEnd(getBooleanQuery("inclusive_end"));
        }
        if(getQuery("reduce") != null) {
            options.setReduce(getBooleanQuery("reduce"));
        }
        options.setGroup(getBooleanQuery("group"));
        options.setContentOptions(getContentOptions());
        options.setStartKey(getJSONQuery("startkey"));
        options.setEndKey(getJSONQuery("endkey"));
        Object key = getJSONQuery("key");
        if(key != null) {
            List<Object> keys = new ArrayList<Object>();
            keys.add(key);
            options.setKeys(keys);
        }
        return true;
    }

    public String getMultipartRequestType() {
        String accept = connection.getRequestProperty("Accept");
        if(accept.startsWith("multipart/")) {
            return accept;
        }
        return null;
    }

    public CBLStatus openDB() {
        if(db == null) {
            return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        }
        if(!db.exists()) {
            return new CBLStatus(CBLStatus.NOT_FOUND);
        }
        if(!db.open()) {
            return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        }
        return new CBLStatus(CBLStatus.OK);
    }

    public static List<String> splitPath(URL url) {
        String pathString = url.getPath();
        if(pathString.startsWith("/")) {
            pathString = pathString.substring(1);
        }
        List<String> result = new ArrayList<String>();
        //we want empty string to return empty list
        if(pathString.length() == 0) {
            return result;
        }
        for (String component : pathString.split("/")) {
            result.add(URLDecoder.decode(component));
        }
        return result;
    }

    public void sendResponse() {
        if(!responseSent) {
            responseSent = true;
            if(callbackBlock != null) {
                callbackBlock.onResponseReady();
            }
        }
    }

    public void start() {
        // Refer to: http://wiki.apache.org/couchdb/Complete_HTTP_API_Reference

        // We're going to map the request into a method call using reflection based on the method and path.
        // Accumulate the method name into the string 'message':
        String method = connection.getRequestMethod();
        if("HEAD".equals(method)) {
            method = "GET";
        }
        String message = String.format("do_%s", method);

        // First interpret the components of the request:
        List<String> path = splitPath(connection.getURL());
        if(path == null) {
            connection.setResponseCode(CBLStatus.BAD_REQUEST);
            try {
                connection.getResponseOutputStream().close();
            } catch (IOException e) {
                Log.e(CBLDatabase.TAG, "Error closing empty output stream");
            }
            sendResponse();
            return;
        }

        int pathLen = path.size();
        if(pathLen > 0) {
            String dbName = path.get(0);
            if(dbName.startsWith("_")) {
                message += dbName;  // special root path, like /_all_dbs
            } else {
                message += "_Database";
                db = server.getDatabaseNamed(dbName);
                if(db == null) {
                    connection.setResponseCode(CBLStatus.BAD_REQUEST);
                    try {
                        connection.getResponseOutputStream().close();
                    } catch (IOException e) {
                        Log.e(CBLDatabase.TAG, "Error closing empty output stream");
                    }
                    sendResponse();
                    return;
                }
            }
        } else {
            message += "Root";
        }

        String docID = null;
        if(db != null && pathLen > 1) {
            message = message.replaceFirst("_Database", "_Document");
            // Make sure database exists, then interpret doc name:
            CBLStatus status = openDB();
            if(!status.isSuccessful()) {
                connection.setResponseCode(status.getCode());
                try {
                    connection.getResponseOutputStream().close();
                } catch (IOException e) {
                    Log.e(CBLDatabase.TAG, "Error closing empty output stream");
                }
                sendResponse();
                return;
            }
            String name = path.get(1);
            if(!name.startsWith("_")) {
                // Regular document
                if(!CBLDatabase.isValidDocumentId(name)) {
                    connection.setResponseCode(CBLStatus.BAD_REQUEST);
                    try {
                        connection.getResponseOutputStream().close();
                    } catch (IOException e) {
                        Log.e(CBLDatabase.TAG, "Error closing empty output stream");
                    }
                    sendResponse();
                    return;
                }
                docID = name;
            } else if("_design".equals(name) || "_local".equals(name)) {
                // "_design/____" and "_local/____" are document names
                if(pathLen <= 2) {
                    connection.setResponseCode(CBLStatus.NOT_FOUND);
                    try {
                        connection.getResponseOutputStream().close();
                    } catch (IOException e) {
                        Log.e(CBLDatabase.TAG, "Error closing empty output stream");
                    }
                    sendResponse();
                    return;
                }
                docID = name + "/" + path.get(2);
                path.set(1, docID);
                path.remove(2);
                pathLen--;
            } else if(name.startsWith("_design") || name.startsWith("_local")) {
                // This is also a document, just with a URL-encoded "/"
                docID = name;
            } else {
                // Special document name like "_all_docs":
                message += name;
                if(pathLen > 2) {
                    List<String> subList = path.subList(2, pathLen-1);
                    StringBuilder sb = new StringBuilder();
                    Iterator<String> iter = subList.iterator();
                    while(iter.hasNext()) {
                        sb.append(iter.next());
                        if(iter.hasNext()) {
                            sb.append("/");
                        }
                    }
                    docID = sb.toString();
                }
            }
        }

        String attachmentName = null;
        if(docID != null && pathLen > 2) {
        	message = message.replaceFirst("_Document", "_Attachment");
        	// Interpret attachment name:
        	attachmentName = path.get(2);
        	if(attachmentName.startsWith("_") && docID.startsWith("_design")) {
        		// Design-doc attribute like _info or _view
        		message = message.replaceFirst("_Attachment", "_DesignDocument");
        		docID = docID.substring(8); // strip the "_design/" prefix
        		attachmentName = pathLen > 3 ? path.get(3) : null;
        	} else {
        		if (pathLen > 3) {
        			List<String> subList = path.subList(2, pathLen);
        			StringBuilder sb = new StringBuilder();
        			Iterator<String> iter = subList.iterator();
        			while(iter.hasNext()) {
        				sb.append(iter.next());
        				if(iter.hasNext()) {
        					//sb.append("%2F");
        					sb.append("/");
        				}
        			}
        			attachmentName = sb.toString();
        		}
        	}
        }

        //Log.d(TAG, "path: " + path + " message: " + message + " docID: " + docID + " attachmentName: " + attachmentName);

        // Send myself a message based on the components:
        CBLStatus status = new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        try {
            Method m = this.getClass().getMethod(message, CBLDatabase.class, String.class, String.class);
            status = (CBLStatus)m.invoke(this, db, docID, attachmentName);
        } catch (NoSuchMethodException msme) {
            try {
                Method m = this.getClass().getMethod("do_UNKNOWN", CBLDatabase.class, String.class, String.class);
                status = (CBLStatus)m.invoke(this, db, docID, attachmentName);
            } catch (Exception e) {
                //default status is internal server error
            }
        } catch (Exception e) {
            //default status is internal server error
            Log.e(CBLDatabase.TAG, "Exception in CBLRouter", e);
        }

        // Configure response headers:
        if(status.isSuccessful() && connection.getResponseBody() == null && connection.getHeaderField("Content-Type") == null) {
            connection.setResponseBody(new CBLBody("{\"ok\":true}".getBytes()));
        }

        if(connection.getResponseBody() != null && connection.getResponseBody().isValidJSON()) {
            connection.getResHeader().add("Content-Type", "application/json");
        }

        // Check for a mismatch between the Accept request header and the response type:
        String accept = connection.getRequestProperty("Accept");
        if(accept != null && !"*/*".equals(accept)) {
            String responseType = connection.getBaseContentType();
            if(responseType != null && accept.indexOf(responseType) < 0) {
                Log.e(CBLDatabase.TAG, String.format("Error 406: Can't satisfy request Accept: %s", accept));
                status = new CBLStatus(CBLStatus.NOT_ACCEPTABLE);
            }
        }

        connection.getResHeader().add("Server", String.format("Couchbase Lite %s", getVersionString()));

        // If response is ready (nonzero status), tell my client about it:
        if(status.getCode() != 0) {
            connection.setResponseCode(status.getCode());

            if(connection.getResponseBody() != null) {
                ByteArrayInputStream bais = new ByteArrayInputStream(connection.getResponseBody().getJson());
                connection.setResponseInputStream(bais);
            } else {

                try {
                    connection.getResponseOutputStream().close();
                } catch (IOException e) {
                    Log.e(CBLDatabase.TAG, "Error closing empty output stream");
                }
            }
            sendResponse();
        }
    }

    public void stop() {
        callbackBlock = null;
        if(db != null) {
            db.deleteObserver(this);
        }
    }

    public CBLStatus do_UNKNOWN(CBLDatabase db, String docID, String attachmentName) {
        return new CBLStatus(CBLStatus.BAD_REQUEST);
    }

    /*************************************************************************************************/
    /*** CBLRouter+Handlers                                                                         ***/
    /*************************************************************************************************/

    public void setResponseLocation(URL url) {
        String location = url.toExternalForm();
        String query = url.getQuery();
        if(query != null) {
            int startOfQuery = location.indexOf(query);
            if(startOfQuery > 0) {
                location = location.substring(0, startOfQuery);
            }
        }
        connection.getResHeader().add("Location", location);
    }

    /** SERVER REQUESTS: **/

    public CBLStatus do_GETRoot(CBLDatabase _db, String _docID, String _attachmentName) {
        Map<String,Object> info = new HashMap<String,Object>();
        info.put("CBLite", "Welcome");
        info.put("couchdb", "Welcome"); // for compatibility
        info.put("version", getVersionString());
        connection.setResponseBody(new CBLBody(info));
        return new CBLStatus(CBLStatus.OK);
    }

    public CBLStatus do_GET_all_dbs(CBLDatabase _db, String _docID, String _attachmentName) {
        List<String> dbs = server.allDatabaseNames();
        connection.setResponseBody(new CBLBody(dbs));
        return new CBLStatus(CBLStatus.OK);
    }

    public CBLStatus do_GET_session(CBLDatabase _db, String _docID, String _attachmentName) {
        // Send back an "Admin Party"-like response
        Map<String,Object> session= new HashMap<String,Object>();
        Map<String,Object> userCtx = new HashMap<String,Object>();
        String[] roles = {"_admin"};
        session.put("ok", true);
        userCtx.put("name", null);
        userCtx.put("roles", roles);
        session.put("userCtx", userCtx);
        connection.setResponseBody(new CBLBody(session));
        return new CBLStatus(CBLStatus.OK);
    }

    public CBLStatus do_POST_replicate(CBLDatabase _db, String _docID, String _attachmentName) {
        // Extract the parameters from the JSON request body:
        // http://wiki.apache.org/couchdb/Replication
        Map<String,Object> body = getBodyAsDictionary();
        if(body == null) {
            return new CBLStatus(CBLStatus.BAD_REQUEST);
        }
        String source = (String)body.get("source");
        String target = (String)body.get("target");
        Boolean createTargetBoolean = (Boolean)body.get("create_target");
        boolean createTarget = (createTargetBoolean != null && createTargetBoolean.booleanValue());
        Boolean continuousBoolean = (Boolean)body.get("continuous");
        boolean continuous = (continuousBoolean != null && continuousBoolean.booleanValue());
        Boolean cancelBoolean = (Boolean)body.get("cancel");
        boolean cancel = (cancelBoolean != null && cancelBoolean.booleanValue());

        // Map the 'source' and 'target' JSON params to a local database and remote URL:
        if(source == null || target == null) {
            return new CBLStatus(CBLStatus.BAD_REQUEST);
        }
        boolean push = false;
        CBLDatabase db = server.getExistingDatabaseNamed(source);
        String remoteStr = null;
        if(db != null) {
            remoteStr = target;
            push = true;
        } else {
            remoteStr = source;
            if(createTarget && !cancel) {
                db = server.getDatabaseNamed(target);
                if(!db.open()) {
                    return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
                }
            } else {
                db = server.getExistingDatabaseNamed(target);
            }
            if(db == null) {
                return new CBLStatus(CBLStatus.NOT_FOUND);
            }
        }

        URL remote = null;
        try {
            remote = new URL(remoteStr);
        } catch (MalformedURLException e) {
            return new CBLStatus(CBLStatus.BAD_REQUEST);
        }
        if(remote == null || !remote.getProtocol().startsWith("http")) {
            return new CBLStatus(CBLStatus.BAD_REQUEST);
        }

        if(!cancel) {
            // Start replication:
            CBLReplicator repl = db.getReplicator(remote, server.getDefaultHttpClientFactory(), push, continuous, server.getWorkExecutor());
            if(repl == null) {
                return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
            }

            String filterName = (String)body.get("filter");
            if(filterName != null) {
                repl.setFilterName(filterName);
                Map<String,Object> filterParams = (Map<String,Object>)body.get("query_params");
                if(filterParams != null) {
                    repl.setFilterParams(filterParams);
                }
            }

            if(push) {
                ((CBLPusher)repl).setCreateTarget(createTarget);
            }
            repl.start();
            Map<String,Object> result = new HashMap<String,Object>();
            result.put("session_id", repl.getSessionID());
            connection.setResponseBody(new CBLBody(result));
        } else {
            // Cancel replication:
            CBLReplicator repl = db.getActiveReplicator(remote, push);
            if(repl == null) {
                return new CBLStatus(CBLStatus.NOT_FOUND);
            }
            repl.stop();
        }
        return new CBLStatus(CBLStatus.OK);
    }

    public CBLStatus do_GET_uuids(CBLDatabase _db, String _docID, String _attachmentName) {
        int count = Math.min(1000, getIntQuery("count", 1));
        List<String> uuids = new ArrayList<String>(count);
        for(int i=0; i<count; i++) {
            uuids.add(CBLDatabase.generateDocumentId());
        }
        Map<String,Object> result = new HashMap<String,Object>();
        result.put("uuids", uuids);
        connection.setResponseBody(new CBLBody(result));
        return new CBLStatus(CBLStatus.OK);
    }

    public CBLStatus do_GET_active_tasks(CBLDatabase _db, String _docID, String _attachmentName) {
        // http://wiki.apache.org/couchdb/HttpGetActiveTasks
        List<Map<String,Object>> activities = new ArrayList<Map<String,Object>>();
        for (CBLDatabase db : server.allOpenDatabases()) {
            List<CBLReplicator> activeReplicators = db.getActiveReplicators();
            if(activeReplicators != null) {
                for (CBLReplicator replicator : activeReplicators) {
                    String source = replicator.getRemote().toExternalForm();
                    String target = db.getName();
                    if(replicator.isPush()) {
                        String tmp = source;
                        source = target;
                        target = tmp;
                    }
                    int processed = replicator.getChangesProcessed();
                    int total = replicator.getChangesTotal();
                    String status = String.format("Processed %d / %d changes", processed, total);
                    int progress = (total > 0) ? Math.round(100 * processed / (float)total) : 0;
                    Map<String,Object> activity = new HashMap<String,Object>();
                    activity.put("type", "Replication");
                    activity.put("task", replicator.getSessionID());
                    activity.put("source", source);
                    activity.put("target", target);
                    activity.put("status", status);
                    activity.put("progress", progress);
                    activities.add(activity);
                }
            }
        }
        connection.setResponseBody(new CBLBody(activities));
        return new CBLStatus(CBLStatus.OK);
    }

    /** DATABASE REQUESTS: **/

    public CBLStatus do_GET_Database(CBLDatabase _db, String _docID, String _attachmentName) {
        // http://wiki.apache.org/couchdb/HTTP_database_API#Database_Information
        CBLStatus status = openDB();
        if(!status.isSuccessful()) {
            return status;
        }
        int num_docs = db.getDocumentCount();
        long update_seq = db.getLastSequence();
        Map<String, Object> result = new HashMap<String,Object>();
        result.put("db_name", db.getName());
        result.put("db_uuid", db.publicUUID());
        result.put("doc_count", num_docs);
        result.put("update_seq", update_seq);
        result.put("disk_size", db.totalDataSize());
        connection.setResponseBody(new CBLBody(result));
        return new CBLStatus(CBLStatus.OK);
    }

    public CBLStatus do_PUT_Database(CBLDatabase _db, String _docID, String _attachmentName) {
        if(db.exists()) {
            return new CBLStatus(CBLStatus.PRECONDITION_FAILED);
        }
        if(!db.open()) {
            return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        }
        setResponseLocation(connection.getURL());
        return new CBLStatus(CBLStatus.CREATED);
    }

    public CBLStatus do_DELETE_Database(CBLDatabase _db, String _docID, String _attachmentName) {
        if(getQuery("rev") != null) {
            return new CBLStatus(CBLStatus.BAD_REQUEST);  // CouchDB checks for this; probably meant to be a document deletion
        }
        return server.deleteDatabaseNamed(db.getName()) ? new CBLStatus(CBLStatus.OK) : new CBLStatus(CBLStatus.NOT_FOUND);
    }

    public CBLStatus do_POST_Database(CBLDatabase _db, String _docID, String _attachmentName) {
        CBLStatus status = openDB();
        if(!status.isSuccessful()) {
            return status;
        }
        return update(db, null, getBodyAsDictionary(), false);
    }

    public CBLStatus do_GET_Document_all_docs(CBLDatabase _db, String _docID, String _attachmentName) {
        CBLQueryOptions options = new CBLQueryOptions();
        if(!getQueryOptions(options)) {
            return new CBLStatus(CBLStatus.BAD_REQUEST);
        }
        Map<String,Object> result = db.getAllDocs(options);
        if(result == null) {
            return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        }
        connection.setResponseBody(new CBLBody(result));
        return new CBLStatus(CBLStatus.OK);
    }

    public CBLStatus do_POST_Document_all_docs(CBLDatabase _db, String _docID, String _attachmentName) {
        CBLQueryOptions options = new CBLQueryOptions();
        if (!getQueryOptions(options)) {
                return new CBLStatus(CBLStatus.BAD_REQUEST);
        }

        Map<String, Object> body = getBodyAsDictionary();
        if (body == null) {
                return new CBLStatus(CBLStatus.BAD_REQUEST);
        }

        Map<String, Object> result = null;
        if (body.containsKey("keys") && body.get("keys") instanceof ArrayList) {
                ArrayList<String> keys = (ArrayList<String>) body.get("keys");
                result = db.getDocsWithIDs(keys, options);
        } else {
                result = db.getAllDocs(options);
        }

        if (result == null) {
                return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        }
        connection.setResponseBody(new CBLBody(result));
        return new CBLStatus(CBLStatus.OK);
    }

    public CBLStatus do_POST_Document_bulk_docs(CBLDatabase _db, String _docID, String _attachmentName) {
    	Map<String,Object> bodyDict = getBodyAsDictionary();
        if(bodyDict == null) {
            return new CBLStatus(CBLStatus.BAD_REQUEST);
        }
        List<Map<String,Object>> docs = (List<Map<String, Object>>) bodyDict.get("docs");

        boolean allObj = false;
        if(getQuery("all_or_nothing") == null || (getQuery("all_or_nothing") != null && (new Boolean(getQuery("all_or_nothing"))))) {
        	allObj = true;
        }
        //   allowConflict If false, an error status 409 will be returned if the insertion would create a conflict, i.e. if the previous revision already has a child.
        boolean allOrNothing = (allObj && allObj != false);
        boolean noNewEdits = true;
        if(getQuery("new_edits") == null || (getQuery("new_edits") != null && (new Boolean(getQuery("new_edits"))))) {
        	noNewEdits = false;
        }
        boolean ok = false;
        db.beginTransaction();
        List<Map<String,Object>> results = new ArrayList<Map<String,Object>>();
        try {
            for (Map<String, Object> doc : docs) {
                String docID = (String) doc.get("_id");
                CBLRevision rev = null;
                CBLStatus status = new CBLStatus(CBLStatus.BAD_REQUEST);
                CBLBody docBody = new CBLBody(doc);
                if (noNewEdits) {
                    rev = new CBLRevision(docBody, db);
                    if(rev.getRevId() == null || rev.getDocId() == null || !rev.getDocId().equals(docID)) {
                        status =  new CBLStatus(CBLStatus.BAD_REQUEST);
                    } else {
                        List<String> history = CBLDatabase.parseCouchDBRevisionHistory(doc);
                        status = db.forceInsert(rev, history, null);
                    }
                } else {
                    CBLStatus outStatus = new CBLStatus();
                    rev = update(db, docID, docBody, false, allOrNothing, outStatus);
                    status.setCode(outStatus.getCode());
                }
                Map<String, Object> result = null;
                if(status.isSuccessful()) {
                    result = new HashMap<String, Object>();
                    result.put("ok", true);
                    result.put("id", docID);
                    if (rev != null) {
                        result.put("rev", rev.getRevId());
                    }
                } else if(allOrNothing) {
                    return status;  // all_or_nothing backs out if there's any error
                } else if(status.getCode() == CBLStatus.FORBIDDEN) {
                    result = new HashMap<String, Object>();
                    result.put("error", "validation failed");
                    result.put("id", docID);
                } else if(status.getCode() == CBLStatus.CONFLICT) {
                    result = new HashMap<String, Object>();
                    result.put("error", "conflict");
                    result.put("id", docID);
                } else {
                    return status;  // abort the whole thing if something goes badly wrong
                }
                if(result != null) {
                    results.add(result);
                }
            }
            Log.w(CBLDatabase.TAG, String.format("%s finished inserting %d revisions in bulk", this, docs.size()));
            ok = true;
        } catch (Exception e) {
            Log.w(CBLDatabase.TAG, String.format("%s: Exception inserting revisions in bulk", this), e);
        } finally {
            db.endTransaction(ok);
        }
        Log.d(CBLDatabase.TAG, "results: " + results.toString());
        connection.setResponseBody(new CBLBody(results));
        return new CBLStatus(CBLStatus.CREATED);
    }

    public CBLStatus do_POST_Document_revs_diff(CBLDatabase _db, String _docID, String _attachmentName) {
        // http://wiki.apache.org/couchdb/HttpPostRevsDiff
        // Collect all of the input doc/revision IDs as TDRevisions:
        CBLRevisionList revs = new CBLRevisionList();
        Map<String, Object> body = getBodyAsDictionary();
        if(body == null) {
            return new CBLStatus(CBLStatus.BAD_JSON);
        }
        for (String docID : body.keySet()) {
            List<String> revIDs = (List<String>)body.get(docID);
            for (String revID : revIDs) {
                CBLRevision rev = new CBLRevision(docID, revID, false, db);
                revs.add(rev);
            }
        }

        // Look them up, removing the existing ones from revs:
        if(!db.findMissingRevisions(revs)) {
            return new CBLStatus(CBLStatus.DB_ERROR);
        }

        // Return the missing revs in a somewhat different format:
        Map<String, Object> diffs = new HashMap<String, Object>();
        for (CBLRevision rev : revs) {
            String docID = rev.getDocId();

            List<String> missingRevs = null;
            Map<String, Object> idObj = (Map<String, Object>)diffs.get(docID);
            if(idObj != null) {
                missingRevs = (List<String>)idObj.get("missing");
            } else {
                idObj = new HashMap<String, Object>();
            }

            if(missingRevs == null) {
                missingRevs = new ArrayList<String>();
                idObj.put("missing", missingRevs);
                diffs.put(docID, idObj);
            }
            missingRevs.add(rev.getRevId());
        }

        // FIXME add support for possible_ancestors

        connection.setResponseBody(new CBLBody(diffs));
        return new CBLStatus(CBLStatus.OK);
    }

    public CBLStatus do_POST_Document_compact(CBLDatabase _db, String _docID, String _attachmentName) {
    	CBLStatus status = _db.compact();
    	if (status.getCode() < 300) {
    		CBLStatus outStatus = new CBLStatus();
    		outStatus.setCode(202);	// CouchDB returns 202 'cause it's an async operation
            return outStatus;
    	} else {
    		return status;
    	}
    }

    public CBLStatus do_POST_Document_ensure_full_commit(CBLDatabase _db, String _docID, String _attachmentName) {
        return new CBLStatus(CBLStatus.OK);
    }

    /** CHANGES: **/

    public Map<String,Object> changesDictForRevision(CBLRevision rev) {
        Map<String,Object> changesDict = new HashMap<String, Object>();
        changesDict.put("rev", rev.getRevId());

        List<Map<String,Object>> changes = new ArrayList<Map<String,Object>>();
        changes.add(changesDict);

        Map<String,Object> result = new HashMap<String,Object>();
        result.put("seq", rev.getSequence());
        result.put("id", rev.getDocId());
        result.put("changes", changes);
        if(rev.isDeleted()) {
            result.put("deleted", true);
        }
        if(changesIncludesDocs) {
            result.put("doc", rev.getProperties());
        }
        return result;
    }

    public Map<String,Object> responseBodyForChanges(List<CBLRevision> changes, long since) {
        List<Map<String,Object>> results = new ArrayList<Map<String,Object>>();
        for (CBLRevision rev : changes) {
            Map<String,Object> changeDict = changesDictForRevision(rev);
            results.add(changeDict);
        }
        if(changes.size() > 0) {
            since = changes.get(changes.size() - 1).getSequence();
        }
        Map<String,Object> result = new HashMap<String,Object>();
        result.put("results", results);
        result.put("last_seq", since);
        return result;
    }

    public Map<String, Object> responseBodyForChangesWithConflicts(List<CBLRevision> changes, long since) {
        // Assumes the changes are grouped by docID so that conflicts will be adjacent.
        List<Map<String,Object>> entries = new ArrayList<Map<String, Object>>();
        String lastDocID = null;
        Map<String, Object> lastEntry = null;
        for (CBLRevision rev : changes) {
            String docID = rev.getDocId();
            if(docID.equals(lastDocID)) {
                Map<String,Object> changesDict = new HashMap<String, Object>();
                changesDict.put("rev", rev.getRevId());
                List<Map<String,Object>> inchanges = (List<Map<String,Object>>)lastEntry.get("changes");
                inchanges.add(changesDict);
            } else {
                lastEntry = changesDictForRevision(rev);
                entries.add(lastEntry);
                lastDocID = docID;
            }
        }
        // After collecting revisions, sort by sequence:
        Collections.sort(entries, new Comparator<Map<String,Object>>() {
           public int compare(Map<String,Object> e1, Map<String,Object> e2) {
               return CBLMisc.TDSequenceCompare((Long)e1.get("seq"), (Long)e2.get("seq"));
           }
        });

        Long lastSeq = (Long)entries.get(entries.size() - 1).get("seq");
        if(lastSeq == null) {
            lastSeq = since;
        }

        Map<String,Object> result = new HashMap<String,Object>();
        result.put("results", entries);
        result.put("last_seq", lastSeq);
        return result;
    }

    public void sendContinuousChange(CBLRevision rev) {
        Map<String,Object> changeDict = changesDictForRevision(rev);
        try {
            String jsonString = CBLServer.getObjectMapper().writeValueAsString(changeDict);
            if(callbackBlock != null) {
                byte[] json = (jsonString + "\n").getBytes();
                OutputStream os = connection.getResponseOutputStream();
                try {
                    os.write(json);
                    os.flush();
                } catch (Exception e) {
                    Log.e(CBLDatabase.TAG, "IOException writing to internal streams", e);
                }
            }
        } catch (Exception e) {
            Log.w("Unable to serialize change to JSON", e);
        }
    }

    @Override
    public void update(Observable observable, Object changeObject) {
        if(observable == db) {
            //make sure we're listening to the right events
            Map<String,Object> changeNotification = (Map<String,Object>)changeObject;

            CBLRevision rev = (CBLRevision)changeNotification.get("rev");

            if(changesFilter != null && !changesFilter.filter(rev)) {
                return;
            }

            if(longpoll) {
                Log.w(CBLDatabase.TAG, "CBLRouter: Sending longpoll response");
                sendResponse();
                List<CBLRevision> revs = new ArrayList<CBLRevision>();
                revs.add(rev);
                Map<String,Object> body = responseBodyForChanges(revs, 0);
                if(callbackBlock != null) {
                    byte[] data = null;
                    try {
                        data = CBLServer.getObjectMapper().writeValueAsBytes(body);
                    } catch (Exception e) {
                        Log.w(CBLDatabase.TAG, "Error serializing JSON", e);
                    }
                    OutputStream os = connection.getResponseOutputStream();
                    try {
                        os.write(data);
                        os.close();
                    } catch (IOException e) {
                        Log.e(CBLDatabase.TAG, "IOException writing to internal streams", e);
                    }
                }
            } else {
                Log.w(CBLDatabase.TAG, "CBLRouter: Sending continous change chunk");
                sendContinuousChange(rev);
            }

        }

    }

    public CBLStatus do_GET_Document_changes(CBLDatabase _db, String docID, String _attachmentName) {
        // http://wiki.apache.org/couchdb/HTTP_database_API#Changes
        CBLChangesOptions options = new CBLChangesOptions();
        changesIncludesDocs = getBooleanQuery("include_docs");
        options.setIncludeDocs(changesIncludesDocs);
        String style = getQuery("style");
        if(style != null && style.equals("all_docs")) {
            options.setIncludeConflicts(true);
        }
        options.setContentOptions(getContentOptions());
        options.setSortBySequence(!options.isIncludeConflicts());
        options.setLimit(getIntQuery("limit", options.getLimit()));

        int since = getIntQuery("since", 0);

        String filterName = getQuery("filter");
        if(filterName != null) {
            changesFilter = db.getFilterNamed(filterName);
            if(changesFilter == null) {
                return new CBLStatus(CBLStatus.NOT_FOUND);
            }
        }

        CBLRevisionList changes = db.changesSince(since, options, changesFilter);

        if(changes == null) {
            return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        }

        String feed = getQuery("feed");
        longpoll = "longpoll".equals(feed);
        boolean continuous = !longpoll && "continuous".equals(feed);

        if(continuous || (longpoll && changes.size() == 0)) {
            connection.setChunked(true);
            connection.setResponseCode(CBLStatus.OK);
            sendResponse();
            if(continuous) {
                for (CBLRevision rev : changes) {
                    sendContinuousChange(rev);
                }
            }
            db.addObserver(this);
         // Don't close connection; more data to come
            return new CBLStatus(0);
        } else {
            if(options.isIncludeConflicts()) {
                connection.setResponseBody(new CBLBody(responseBodyForChangesWithConflicts(changes, since)));
            } else {
                connection.setResponseBody(new CBLBody(responseBodyForChanges(changes, since)));
            }
            return new CBLStatus(CBLStatus.OK);
        }
    }

    /** DOCUMENT REQUESTS: **/

    public String getRevIDFromIfMatchHeader() {
        String ifMatch = connection.getRequestProperty("If-Match");
        if(ifMatch == null) {
            return null;
        }
        // Value of If-Match is an ETag, so have to trim the quotes around it:
        if(ifMatch.length() > 2 && ifMatch.startsWith("\"") && ifMatch.endsWith("\"")) {
            return ifMatch.substring(1,ifMatch.length() - 2);
        } else {
            return null;
        }
    }

    public String setResponseEtag(CBLRevision rev) {
        String eTag = String.format("\"%s\"", rev.getRevId());
        connection.getResHeader().add("Etag", eTag);
        return eTag;
    }

    public CBLStatus do_GET_Document(CBLDatabase _db, String docID, String _attachmentName) {
        // http://wiki.apache.org/couchdb/HTTP_Document_API#GET
        boolean isLocalDoc = docID.startsWith("_local");
        EnumSet<TDContentOptions> options = getContentOptions();
        String openRevsParam = getQuery("open_revs");
        if(openRevsParam == null || isLocalDoc) {
            // Regular GET:
            String revID = getQuery("rev");  // often null
            CBLRevision rev = null;
            if(isLocalDoc) {
                rev = db.getLocalDocument(docID, revID);
            } else {
                rev = db.getDocumentWithIDAndRev(docID, revID, options);
                // Handle ?atts_since query by stubbing out older attachments:
                //?atts_since parameter - value is a (URL-encoded) JSON array of one or more revision IDs.
                // The response will include the content of only those attachments that changed since the given revision(s).
                //(You can ask for this either in the default JSON or as multipart/related, as previously described.)
                List<String> attsSince = (List<String>)getJSONQuery("atts_since");
                if (attsSince != null) {
                    String ancestorId = db.findCommonAncestorOf(rev, attsSince);
                    if (ancestorId != null) {
                        int generation = CBLRevision.generationFromRevID(ancestorId);
                        db.stubOutAttachmentsIn(rev, generation + 1);
                	}
                }
            }
            if(rev == null) {
                return new CBLStatus(CBLStatus.NOT_FOUND);
            }
            if(cacheWithEtag(rev.getRevId())) {
                return new CBLStatus(CBLStatus.NOT_MODIFIED);  // set ETag and check conditional GET
            }

            connection.setResponseBody(rev.getBody());
        } else {
            List<Map<String,Object>> result = null;
            if(openRevsParam.equals("all")) {
                // Get all conflicting revisions:
                CBLRevisionList allRevs = db.getAllRevisionsOfDocumentID(docID, true);
                result = new ArrayList<Map<String,Object>>(allRevs.size());
                for (CBLRevision rev : allRevs) {
                    CBLStatus status = db.loadRevisionBody(rev, options);
                    if(status.isSuccessful()) {
                        Map<String, Object> dict = new HashMap<String,Object>();
                        dict.put("ok", rev.getProperties());
                        result.add(dict);
                    } else if(status.getCode() != CBLStatus.INTERNAL_SERVER_ERROR) {
                        Map<String, Object> dict = new HashMap<String,Object>();
                        dict.put("missing", rev.getRevId());
                        result.add(dict);
                    } else {
                        return status;  // internal error getting revision
                    }
                }
            } else {
                // ?open_revs=[...] returns an array of revisions of the document:
                List<String> openRevs = (List<String>)getJSONQuery("open_revs");
                if(openRevs == null) {
                    return new CBLStatus(CBLStatus.BAD_REQUEST);
                }
                result = new ArrayList<Map<String,Object>>(openRevs.size());
                for (String revID : openRevs) {
                    CBLRevision rev = db.getDocumentWithIDAndRev(docID, revID, options);
                    if(rev != null) {
                        Map<String, Object> dict = new HashMap<String,Object>();
                        dict.put("ok", rev.getProperties());
                        result.add(dict);
                    } else {
                        Map<String, Object> dict = new HashMap<String,Object>();
                        dict.put("missing", revID);
                        result.add(dict);
                    }
                }
            }
            String acceptMultipart  = getMultipartRequestType();
            if(acceptMultipart != null) {
                //FIXME figure out support for multipart
                throw new UnsupportedOperationException();
            } else {
                connection.setResponseBody(new CBLBody(result));
            }
        }
        return new CBLStatus(CBLStatus.OK);
    }

    public CBLStatus do_GET_Attachment(CBLDatabase _db, String docID, String _attachmentName) {
    	// http://wiki.apache.org/couchdb/HTTP_Document_API#GET
        EnumSet<TDContentOptions> options = getContentOptions();
        options.add(TDContentOptions.TDNoBody);
    	String revID = getQuery("rev");  // often null
    	CBLRevision rev = db.getDocumentWithIDAndRev(docID, revID, options);
    	if(rev == null) {
    		return new CBLStatus(CBLStatus.NOT_FOUND);
    	}
    	if(cacheWithEtag(rev.getRevId())) {
    		return new CBLStatus(CBLStatus.NOT_MODIFIED);  // set ETag and check conditional GET
    	}

    	String type = null;
    	CBLStatus status = new CBLStatus();
    	String acceptEncoding = connection.getRequestProperty("Accept-Encoding");
    	CBLAttachment contents = db.getAttachmentForSequence(rev.getSequence(), _attachmentName, status);

    	if (contents == null) {
    		return new CBLStatus(CBLStatus.NOT_FOUND);
    	}
    	type = contents.getContentType();
    	if (type != null) {
    		connection.getResHeader().add("Content-Type", type);
    	}
    	if (acceptEncoding != null && acceptEncoding.equals("gzip")) {
    		connection.getResHeader().add("Content-Encoding", acceptEncoding);
    	}

        connection.setResponseInputStream(contents.getContentStream());
        return new CBLStatus(CBLStatus.OK);
    }

    /**
     * NOTE this departs from the iOS version, returning revision, passing status back by reference
     */
    public CBLRevision update(CBLDatabase _db, String docID, CBLBody body, boolean deleting, boolean allowConflict, CBLStatus outStatus) {
        boolean isLocalDoc = docID != null && docID.startsWith(("_local"));
        String prevRevID = null;

        if(!deleting) {
            Boolean deletingBoolean = (Boolean)body.getPropertyForKey("deleted");
            deleting = (deletingBoolean != null && deletingBoolean.booleanValue());
            if(docID == null) {
                if(isLocalDoc) {
                    outStatus.setCode(CBLStatus.METHOD_NOT_ALLOWED);
                    return null;
                }
                // POST's doc ID may come from the _id field of the JSON body, else generate a random one.
                docID = (String)body.getPropertyForKey("_id");
                if(docID == null) {
                    if(deleting) {
                        outStatus.setCode(CBLStatus.BAD_REQUEST);
                        return null;
                    }
                    docID = CBLDatabase.generateDocumentId();
                }
            }
            // PUT's revision ID comes from the JSON body.
            prevRevID = (String)body.getPropertyForKey("_rev");
        } else {
            // DELETE's revision ID comes from the ?rev= query param
            prevRevID = getQuery("rev");
        }

        // A backup source of revision ID is an If-Match header:
        if(prevRevID == null) {
            prevRevID = getRevIDFromIfMatchHeader();
        }

        CBLRevision rev = new CBLRevision(docID, null, deleting, db);
        rev.setBody(body);

        CBLRevision result = null;
        CBLStatus tmpStatus = new CBLStatus();
        if(isLocalDoc) {
            result = _db.putLocalRevision(rev, prevRevID, tmpStatus);
        } else {
            result = _db.putRevision(rev, prevRevID, allowConflict, tmpStatus);
        }
        outStatus.setCode(tmpStatus.getCode());
        return result;
    }

    public CBLStatus update(CBLDatabase _db, String docID, Map<String,Object> bodyDict, boolean deleting) {
        CBLBody body = new CBLBody(bodyDict);
        CBLStatus status = new CBLStatus();
        CBLRevision rev = update(_db, docID, body, deleting, false, status);
        if(status.isSuccessful()) {
            cacheWithEtag(rev.getRevId());  // set ETag
            if(!deleting) {
                URL url = connection.getURL();
                String urlString = url.toExternalForm();
                if(docID != null) {
                    urlString += "/" + rev.getDocId();
                    try {
                        url = new URL(urlString);
                    } catch (MalformedURLException e) {
                        Log.w("Malformed URL", e);
                    }
                }
                setResponseLocation(url);
            }
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("ok", true);
            result.put("id", rev.getDocId());
            result.put("rev", rev.getRevId());
            connection.setResponseBody(new CBLBody(result));
        }
        return status;
    }

    public CBLStatus do_PUT_Document(CBLDatabase _db, String docID, String _attachmentName) {
        Map<String,Object> bodyDict = getBodyAsDictionary();
        if(bodyDict == null) {
            return new CBLStatus(CBLStatus.BAD_REQUEST);
        }

        if(getQuery("new_edits") == null || (getQuery("new_edits") != null && (new Boolean(getQuery("new_edits"))))) {
            // Regular PUT
            return update(_db, docID, bodyDict, false);
        } else {
            // PUT with new_edits=false -- forcible insertion of existing revision:
            CBLBody body = new CBLBody(bodyDict);
            CBLRevision rev = new CBLRevision(body, _db);
            if(rev.getRevId() == null || rev.getDocId() == null || !rev.getDocId().equals(docID)) {
                return new CBLStatus(CBLStatus.BAD_REQUEST);
            }
            List<String> history = CBLDatabase.parseCouchDBRevisionHistory(body.getProperties());
            return db.forceInsert(rev, history, null);
        }
    }

    public CBLStatus do_DELETE_Document(CBLDatabase _db, String docID, String _attachmentName) {
        return update(_db, docID, null, true);
    }

    public CBLStatus updateAttachment(String attachment, String docID, InputStream contentStream) {
        CBLStatus status = new CBLStatus();
        String revID = getQuery("rev");
        if(revID == null) {
            revID = getRevIDFromIfMatchHeader();
        }
        CBLRevision rev = db.updateAttachment(attachment, contentStream, connection.getRequestProperty("content-type"),
                docID, revID, status);
        if(status.isSuccessful()) {
            Map<String, Object> resultDict = new HashMap<String, Object>();
            resultDict.put("ok", true);
            resultDict.put("id", rev.getDocId());
            resultDict.put("rev", rev.getRevId());
            connection.setResponseBody(new CBLBody(resultDict));
            cacheWithEtag(rev.getRevId());
            if(contentStream != null) {
                setResponseLocation(connection.getURL());
            }
        }
        return status;
    }

    public CBLStatus do_PUT_Attachment(CBLDatabase _db, String docID, String _attachmentName) {
        return updateAttachment(_attachmentName, docID, connection.getRequestInputStream());
    }

    public CBLStatus do_DELETE_Attachment(CBLDatabase _db, String docID, String _attachmentName) {
        return updateAttachment(_attachmentName, docID, null);
    }

    /** VIEW QUERIES: **/

    public CBLView compileView(String viewName, Map<String,Object> viewProps) {
        String language = (String)viewProps.get("language");
        if(language == null) {
            language = "javascript";
        }
        String mapSource = (String)viewProps.get("map");
        if(mapSource == null) {
            return null;
        }
        CBLViewMapBlock mapBlock = CBLView.getCompiler().compileMapFunction(mapSource, language);
        if(mapBlock == null) {
            Log.w(CBLDatabase.TAG, String.format("View %s has unknown map function: %s", viewName, mapSource));
            return null;
        }
        String reduceSource = (String)viewProps.get("reduce");
        CBLViewReduceBlock reduceBlock = null;
        if(reduceSource != null) {
            reduceBlock = CBLView.getCompiler().compileReduceFunction(reduceSource, language);
            if(reduceBlock == null) {
                Log.w(CBLDatabase.TAG, String.format("View %s has unknown reduce function: %s", viewName, reduceBlock));
                return null;
            }
        }

        CBLView view = db.getViewNamed(viewName);
        view.setMapReduceBlocks(mapBlock, reduceBlock, "1");
        String collation = (String)viewProps.get("collation");
        if("raw".equals(collation)) {
            view.setCollation(TDViewCollation.TDViewCollationRaw);
        }
        return view;
    }

    public CBLStatus queryDesignDoc(String designDoc, String viewName, List<Object> keys) {
        String tdViewName = String.format("%s/%s", designDoc, viewName);
        CBLView view = db.getExistingViewNamed(tdViewName);
        if(view == null || view.getMapBlock() == null) {
            // No TouchDB view is defined, or it hasn't had a map block assigned;
            // see if there's a CouchDB view definition we can compile:
            CBLRevision rev = db.getDocumentWithIDAndRev(String.format("_design/%s", designDoc), null, EnumSet.noneOf(TDContentOptions.class));
            if(rev == null) {
                return new CBLStatus(CBLStatus.NOT_FOUND);
            }
            Map<String,Object> views = (Map<String,Object>)rev.getProperties().get("views");
            Map<String,Object> viewProps = (Map<String,Object>)views.get(viewName);
            if(viewProps == null) {
                return new CBLStatus(CBLStatus.NOT_FOUND);
            }
            // If there is a CouchDB view, see if it can be compiled from source:
            view = compileView(tdViewName, viewProps);
            if(view == null) {
                return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
            }
        }

        CBLQueryOptions options = new CBLQueryOptions();

        //if the view contains a reduce block, it should default to reduce=true
        if(view.getReduceBlock() != null) {
            options.setReduce(true);
        }

        if(!getQueryOptions(options)) {
            return new CBLStatus(CBLStatus.BAD_REQUEST);
        }
        if(keys != null) {
            options.setKeys(keys);
        }

        CBLStatus status = view.updateIndex();
        if(!status.isSuccessful()) {
            return status;
        }

        long lastSequenceIndexed = view.getLastSequenceIndexed();

        // Check for conditional GET and set response Etag header:
        if(keys == null) {
            long eTag = options.isIncludeDocs() ? db.getLastSequence() : lastSequenceIndexed;
            if(cacheWithEtag(String.format("%d", eTag))) {
                return new CBLStatus(CBLStatus.NOT_MODIFIED);
            }
        }

        List<Map<String,Object>> rows = view.queryWithOptions(options, status);
        if(rows == null) {
            return status;
        }

        Map<String,Object> responseBody = new HashMap<String,Object>();
        responseBody.put("rows", rows);
        responseBody.put("total_rows", rows.size());
        responseBody.put("offset", options.getSkip());
        if(options.isUpdateSeq()) {
            responseBody.put("update_seq", lastSequenceIndexed);
        }
        connection.setResponseBody(new CBLBody(responseBody));
        return new CBLStatus(CBLStatus.OK);
    }

    public CBLStatus do_GET_DesignDocument(CBLDatabase _db, String designDocID, String viewName) {
        return queryDesignDoc(designDocID, viewName, null);
    }

    public CBLStatus do_POST_DesignDocument(CBLDatabase _db, String designDocID, String viewName) {
    	Map<String,Object> bodyDict = getBodyAsDictionary();
    	if(bodyDict == null) {
    		return new CBLStatus(CBLStatus.BAD_REQUEST);
    	}
    	List<Object> keys = (List<Object>) bodyDict.get("keys");
    	return queryDesignDoc(designDocID, viewName, keys);
    }

    @Override
    public String toString() {
        String url = "Unknown";
        if(connection != null && connection.getURL() != null) {
            url = connection.getURL().toExternalForm();
        }
        return String.format("CBLRouter [%s]", url);
    }
}
