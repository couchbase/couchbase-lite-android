/**
 * Original iOS version by  Jens Alfke
 * Ported to Android by Marty Schoch
 *
 * Copyright (c) 2012 Couchbase, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.touchdb;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.UUID;

import org.codehaus.jackson.map.ObjectMapper;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.couchbase.touchdb.support.Base64;
import com.couchbase.touchdb.support.DirUtils;

public class TDDatabase extends Observable {

    private String path;
    private SQLiteDatabase database;
    private boolean open = false;
    private int transactionLevel = 0;
    private boolean transactionFailed = false;
    public static final String TAG = "TDDatabase";

    private Map<String, TDView> views;
    private TDBlobStore attachments;

    public static final String SCHEMA = "" +
            "CREATE TABLE docs ( " +
            "        doc_id INTEGER PRIMARY KEY, " +
            "        docid TEXT UNIQUE NOT NULL); " +
            "    CREATE INDEX docs_docid ON docs(docid); " +
            "    CREATE TABLE revs ( " +
            "        sequence INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "        doc_id INTEGER NOT NULL REFERENCES docs(doc_id) ON DELETE CASCADE, " +
            "        revid TEXT NOT NULL, " +
            "        parent INTEGER REFERENCES revs(sequence) ON DELETE SET NULL, " +
            "        current BOOLEAN, " +
            "        deleted BOOLEAN DEFAULT 0, " +
            "        json BLOB); " +
            "    CREATE INDEX revs_by_id ON revs(revid, doc_id); " +
            "    CREATE INDEX revs_current ON revs(doc_id, current); " +
            "    CREATE INDEX revs_parent ON revs(parent); " +
            "    CREATE TABLE views ( " +
            "        view_id INTEGER PRIMARY KEY, " +
            "        name TEXT UNIQUE NOT NULL," +
            "        version TEXT, " +
            "        lastsequence INTEGER DEFAULT 0); " +
            "    CREATE INDEX views_by_name ON views(name); " +
            "    CREATE TABLE maps ( " +
            "        view_id INTEGER NOT NULL REFERENCES views(view_id) ON DELETE CASCADE, " +
            "        sequence INTEGER NOT NULL REFERENCES revs(sequence) ON DELETE CASCADE, " +
            "        key TEXT NOT NULL COLLATE UNICODE, " +
            "        value TEXT); " +
            "    CREATE INDEX maps_keys on maps(view_id, key COLLATE UNICODE); " +
            "    CREATE TABLE attachments ( " +
            "        sequence INTEGER NOT NULL REFERENCES revs(sequence) ON DELETE CASCADE, " +
            "        filename TEXT NOT NULL, " +
            "        key BLOB NOT NULL, " +
            "        type TEXT, " +
            "        length INTEGER NOT NULL); " +
            "    CREATE INDEX attachments_by_sequence on attachments(sequence, filename); " +
            "    CREATE TABLE replicators ( " +
            "        remote TEXT NOT NULL, " +
            "        push BOOLEAN, " +
            "        last_sequence TEXT, " +
            "        UNIQUE (remote, push)); " +
            "    PRAGMA user_version = 1";             // at the end, update user_version


    public static TDDatabase createEmptyDBAtPath(String path) {
        File f = new File(path);
        f.delete();
        TDDatabase result = new TDDatabase(path);
        File af = new File(result.getAttachmentStorePath());
        //recursively delete attachments path
        DirUtils.deleteRecursive(af);
        if(!result.open()) {
            return null;
        }
        return result;
    }

    public TDDatabase(String path) {
        this.path = path;
    }

    public String toString() {
        return this.getClass().getName() + "[" + path + "]";
    }

    public boolean exists() {
        return new File(path).exists();
    }

    public String getAttachmentStorePath() {
        String attachmentStorePath = path;
        int lastDotPosition = attachmentStorePath.lastIndexOf('.');
        if( lastDotPosition > 0 ) {
            attachmentStorePath = attachmentStorePath.substring(0, lastDotPosition);
        }
        attachmentStorePath = attachmentStorePath + File.separator + "attachments";
        return attachmentStorePath;
    }

    public TDBlobStore getAttachments() {
        return attachments;
    }

    // Leave this package protected, so it can only be used
    // TDView uses this accessor
    SQLiteDatabase getDatabase() {
        return database;
    }

    public boolean initialize(String statements) {
        try {
            for (String statement : statements.split(";")) {
                database.execSQL(statement);
            }
        } catch (SQLException e) {
            close();
            return false;
        }
        return true;
    }

    public boolean open() {
        if(open) {
            return true;
        }

        try {
            database = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.CREATE_IF_NECESSARY);
        }
        catch(SQLiteException e) {
            Log.e(TDDatabase.TAG, "Error opening", e);
            return false;
        }

        // Stuff we need to initialize every time the database opens:
        if(!initialize("PRAGMA foreign_keys = ON;")) {
            Log.e(TDDatabase.TAG, "Error turning on foreign keys");
            return false;
        }

        // Check the user_version number we last stored in the database:
        int dbVersion = database.getVersion();

        // Incompatible version changes increment the hundreds' place:
        if(dbVersion >= 100) {
            Log.w(TDDatabase.TAG, "TDDatabase: Database version (" + dbVersion + ") is newer than I know how to work with");
            database.close();
            return false;
        }

        if(dbVersion < 1) {
            // First-time initialization:
            // (Note: Declaring revs.sequence as AUTOINCREMENT means the values will always be
            // monotonically increasing, never reused. See <http://www.sqlite.org/autoinc.html>)
            if(!initialize(SCHEMA)) {
                database.close();
                return false;
            }
        }

        try {
            attachments = new TDBlobStore(getAttachmentStorePath());
        } catch (IllegalArgumentException e) {
            Log.e(TDDatabase.TAG, "Could not initialize attachment store", e);
            database.close();
            return false;
        }


        open = true;
        return true;
    }

    public boolean close() {
        if(database != null && database.isOpen()) {
            database.close();
        }
        open = false;
        return true;
    }

    public boolean deleteDatabase() {
        if(open) {
            if(!close()) {
                return false;
            }
        }
        else if(!exists()) {
            return true;
        }
        File file = new File(path);
        File attachmentsFile = new File(getAttachmentStorePath());

        try {
            boolean deleteStatus = file.delete();
            boolean deleteAttachmentStatus = deleteRecursive(attachmentsFile);
            return deleteStatus && deleteAttachmentStatus;
        } catch (FileNotFoundException e) {
            Log.e(TDDatabase.TAG, "Attachemts store not found");
            return false;
        }
    }

    private static boolean deleteRecursive(File path) throws FileNotFoundException {
        if (!path.exists()) {
            throw new FileNotFoundException(path.getAbsolutePath());
        }
        boolean ret = true;
        if (path.isDirectory()) {
            for (File f : path.listFiles()){
                ret = ret && deleteRecursive(f);
            }
        }
        return ret && path.delete();
    }


    public void beginTransaction() {
        if(++transactionLevel == 1) {
            Log.v(TAG, "Begin transaction...");
            database.beginTransaction();
            transactionFailed = false;
        }
    }

    public void endTransaction() {
        assert(transactionLevel > 0);
        if(--transactionLevel == 0) {
            if(transactionFailed) {
                Log.v(TAG, "Rolling back transaction!");
                database.endTransaction();
            }
            else {
                Log.v(TAG, "Committing transaction");
                database.setTransactionSuccessful();
                database.endTransaction();
            }

        }
        transactionFailed = false;
    }

    public boolean isTransactionFailed() {
        return transactionFailed;
    }

    public void setTransactionFailed(boolean transactionFailed) {
        this.transactionFailed = transactionFailed;
    }

    /*** Getting Documents ***/

    /** Splices the contents of an NSDictionary into JSON data (that already represents a dict), without parsing the JSON. */
    public static byte[] appendDictToJSON(byte[] json, Map<String,Object> dict) {
        if(dict.size() == 0) {
            return json;
        }

        ObjectMapper mapper = new ObjectMapper();
        byte[] extraJSON = null;
        try {
            extraJSON = mapper.writeValueAsBytes(dict);
        } catch (Exception e) {
            Log.e(TDDatabase.TAG, "Error convert extra JSON to bytes", e);
            return null;
        }

        int jsonLength = json.length;
        int extraLength = extraJSON.length;
        if(jsonLength == 2) { // Original JSON was empty
            return extraJSON;
        }
        byte[] newJson = new byte[jsonLength + extraLength - 1];
        System.arraycopy(json, 0, newJson, 0, jsonLength - 1);  // Copy json w/o trailing '}'
        newJson[jsonLength - 1] = ',';  // Add a ','
        System.arraycopy(extraJSON, 1, newJson, jsonLength, extraLength - 1);
        return newJson;
    }

    /** Inserts the _id, _rev and _attachments properties into the JSON data and stores it in rev.
    Rev must already have its revID and sequence properties set. */
    public void expandStoredJSONIntoRevisionWithAttachments(byte[] json, TDRevision rev, boolean withAttachments) {
        if(json == null) {
            return;
        }

        String docId = rev.getDocId();
        String revId = rev.getRevId();
        long sequenceNumber = rev.getSequence();
        assert(sequenceNumber > 0);
        Map<String, Object> attachmentsDict = getAttachmentsDictForSequenceWithContent(sequenceNumber, withAttachments);

        Map<String,Object> extra = new HashMap<String,Object>();
        extra.put("_id", docId);
        extra.put("_rev", revId);
        if(rev.isDeleted()) {
            extra.put("_deleted", true);
        }
        if(attachmentsDict != null) {
            extra.put("_attachments", attachmentsDict);
        }

        rev.setJson(appendDictToJSON(json, extra));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> documentPropertiesFromJSON(byte[] json, String docId, String revId, long sequence) {
        Map<String, Object> result = null;
        ObjectMapper mapper = new ObjectMapper();
        try {
            result = mapper.readValue(json, Map.class);
            result.put("_id", docId);
            result.put("_rev", revId);
            result.put("_attachments", getAttachmentsDictForSequenceWithContent(sequence, false));
        } catch (Exception e) {
            Log.e(TDDatabase.TAG, "Error serializing properties to JSON", e);
        }
        return result;
    }

    public TDRevision getDocumentWithID(String id) {
        return getDocumentWithIDAndRev(id, null);
    }

    public TDRevision getDocumentWithIDAndRev(String id, String rev) {
        TDRevision result = null;
        String sql;

        Cursor cursor = null;
        try {
            cursor = null;
            if(rev != null) {
                sql = "SELECT revid, deleted, json, sequence FROM revs, docs WHERE docs.docid=? AND revs.doc_id=docs.doc_id AND revid=? LIMIT 1";
                String[] args = {id, rev};
                cursor = database.rawQuery(sql, args);
            }
            else {
                sql = "SELECT revid, deleted, json, sequence FROM revs, docs WHERE docs.docid=? AND revs.doc_id=docs.doc_id and current=1 and deleted=0 ORDER BY revid DESC LIMIT 1";
                String[] args = {id};
                cursor = database.rawQuery(sql, args);
            }

            if(cursor.moveToFirst()) {
                if(rev == null) {
                    rev = cursor.getString(0);
                }
                boolean deleted = (cursor.getInt(1) > 0);
                byte[] json = cursor.getBlob(2);
                result = new TDRevision(id, rev, deleted);
                result.setSequence(cursor.getLong(3));
                expandStoredJSONIntoRevisionWithAttachments(json, result, false);
            }
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error getting document with id and rev", e);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    public TDStatus loadRevisionBodyAndAttachments(TDRevision rev, boolean withAttachments) {
        if(rev.getBody() != null) {
            return new TDStatus(TDStatus.OK);
        }
        assert((rev.getDocId() != null) && (rev.getRevId() != null));

        Cursor cursor = null;
        TDStatus result = new TDStatus(TDStatus.NOT_FOUND);
        try {
            String sql = "SELECT sequence, json FROM revs, doc WHERE revid=? AND docs.docid=? AND revs.doc_id=docs.doc_id LIMIT 1";
            String[] args = { rev.getRevId(), rev.getDocId()};
            cursor = database.rawQuery(sql, args);
            if(cursor.moveToFirst()) {
                result.setCode(TDStatus.OK);
                rev.setSequence(cursor.getLong(0));
                expandStoredJSONIntoRevisionWithAttachments(cursor.getBlob(1), rev, withAttachments);
            }
        } catch(SQLException e) {
            Log.e(TDDatabase.TAG, "Error loading revision body", e);
            return new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    public TDStatus compact() {
        // Can't delete any rows because that would lose revision tree history.
        // But we can remove the JSON of non-current revisions, which is most of the space.
        try {
            Log.v(TDDatabase.TAG, "Deleting JSON of old revisions...");
            ContentValues args = new ContentValues();
            args.put("json", (String)null);
            database.update("revs", args, "current=0", null);
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error compacting", e);
            return new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
        }

        Log.v(TDDatabase.TAG, "Deleting old attachments...");
        TDStatus result = garbageCollectAttachments();

        Log.v(TDDatabase.TAG, "Vacuuming SQLite database...");
        try {
            database.execSQL("VACUUM");
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error vacuuming database", e);
            return new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
        }

        return result;
    }

    public static boolean isValidDocumentId(String id) {
        // http://wiki.apache.org/couchdb/HTTP_Document_API#Documents
        if(id != null && id.length() > 0) {
            return true;
        }
        return false;
    }

    public int getDocumentCount() {
        String sql = "SELECT COUNT(DISTINCT doc_id) FROM revs WHERE current=1 AND deleted=0";
        Cursor cursor = null;
        int result = 0;
        try {
            cursor = database.rawQuery(sql, null);
            if(cursor.moveToFirst()) {
                result = cursor.getInt(0);
            }
        } catch(SQLException e) {
            Log.e(TDDatabase.TAG, "Error getting document count", e);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    public long getLastSequence() {
        String sql = "SELECT MAX(sequence) FROM revs";
        Cursor cursor = null;
        long result = 0;
        try {
            cursor = database.rawQuery(sql, null);
            if(cursor.moveToFirst()) {
                result = cursor.getLong(0);
            }
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error getting last sequence", e);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    public static String createUUID() {
        return UUID.randomUUID().toString();
    }

    public String generateDocumentId() {
        return createUUID();
    }

    public String getNextRevisionId(String revisionId) {
     // Revision IDs have a generation count, a hyphen, and a UUID.
        int generation = 0;
        if(revisionId != null) {
            String[] parts = revisionId.split("-");
            if(parts.length > 1) {
                generation = Integer.parseInt(parts[0]);
            }
        }
        String digest = createUUID();  //TODO: Generate canonical digest of body
        return "" + ++generation + "-" + digest;
    }

    public void notifyChange(TDRevision rev, URL source) {
        Map<String,Object> changeNotification = new HashMap<String, Object>();
        changeNotification.put("rev", rev);
        changeNotification.put("seq", rev.getSequence());
        if(source != null) {
            changeNotification.put("source", source);
        }
        setChanged();
        notifyObservers(changeNotification);
    }

    public long insertDocumentID(String docId) {
        long rowId = -1;
        try {
            ContentValues args = new ContentValues();
            args.put("docid", docId);
            rowId = database.insert("docs", null, args);
        } catch (Exception e) {
            Log.e(TDDatabase.TAG, "Error inserting document id", e);
        }
        return rowId;
    }

    public long getDocNumericID(String docId) {
        Cursor cursor = null;
        String[] args = { docId };

        long result = -1;
        try {
            cursor = database.rawQuery("SELECT doc_id FROM docs WHERE docid=?", args);

            if(cursor.moveToFirst()) {
                result = cursor.getLong(0);
            }
            else {
                result = 0;
            }
        } catch (Exception e) {
            Log.e(TDDatabase.TAG, "Error getting doc numeric id", e);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    public long getOrInsertDocNumericID(String docId) {
        long docNumericId = getDocNumericID(docId);
        if(docNumericId == 0) {
            docNumericId = insertDocumentID(docId);
        }
        return docNumericId;
    }

    public byte[] encodeDocumentJSON(TDRevision rev) {
        byte[] result = null;
        Map<String,Object> revProperties = rev.getProperties();
        if(revProperties == null) {
            return result;
        }
        Map<String,Object> properties = new HashMap<String,Object>(revProperties);
        properties.remove("_id");
        properties.remove("_rev");
        properties.remove("_deleted");
        properties.remove("_attachments");
        ObjectMapper mapper = new ObjectMapper();
        try {
            result = mapper.writeValueAsBytes(properties);
        } catch (Exception e) {
            Log.e(TDDatabase.TAG, "Error serializing properties to JSON", e);
        }
        return result;
    }

    public long insertRevision(TDRevision rev, long docNumericID, long parentSequence, boolean current, byte[] data) {
        long rowId = 0;
        try {
            ContentValues args = new ContentValues();
            args.put("doc_id", docNumericID);
            args.put("revid", rev.getRevId());
            args.put("parent", parentSequence);
            args.put("current", current);
            args.put("deleted", rev.isDeleted());
            args.put("json", data);
            rowId = database.insert("revs", null, args);
            rev.setSequence(rowId);
        } catch (Exception e) {
            Log.e(TDDatabase.TAG, "Error inserting revision", e);
        }
        return rowId;
    }

    @SuppressWarnings("unchecked")
    public TDRevision putRevision(TDRevision rev, String prevRevId, TDStatus resultStatus) {
        // prevRevId is the rev ID being replaced, or nil if an insert
        assert(rev.getRevId() == null);
        String docId = rev.getDocId();
        long docNumericID;
        boolean deleted = rev.isDeleted();
        if((rev == null) || ((prevRevId != null) && (docId == null)) || (deleted && (prevRevId == null))) {
            resultStatus.setCode(TDStatus.BAD_REQUEST);
            return null;
        }

        beginTransaction();
        Cursor cursor = null;
        long parentSequence = 0;
        try {
            if(prevRevId != null) {
             // Replacing: make sure given prevRevID is current & find its sequence number:
                docNumericID = getOrInsertDocNumericID(docId);
                if(docNumericID <= 0) {
                    transactionFailed = true;
                    endTransaction();
                    resultStatus.setCode(TDStatus.INTERNAL_SERVER_ERROR);
                    return null;
                }
                String[] args = {Long.toString(docNumericID), prevRevId};
                cursor = database.rawQuery("SELECT sequence FROM revs WHERE doc_id=? AND revid=? and current=1 LIMIT 1", args);

                if(cursor.moveToFirst()) {
                    parentSequence = cursor.getLong(0);
                }
                else {
                    // Not found: either a 404 or a 409, depending on whether there is any current revision
                    if(getDocumentWithID(docId) != null) {
                        transactionFailed = true;
                        endTransaction();
                        resultStatus.setCode(TDStatus.CONFLICT);
                        return null;
                    }
                    else {
                        transactionFailed = true;
                        endTransaction();
                        resultStatus.setCode(TDStatus.NOT_FOUND);
                        return null;
                    }
                }

                // Make replaced rev non-current:
                ContentValues updateContent = new ContentValues();
                updateContent.put("current", 0);
                int rowsUpdated = database.update("revs", updateContent, "sequence=" + parentSequence, null);
                assert(rowsUpdated == 1);
            }
            else if(docId != null) {
                // Inserting first revision, with docID given: make sure docID doesn't exist,
                // or exists but is currently deleted
                docNumericID = getOrInsertDocNumericID(docId);
                if(docNumericID <= 0) {
                    transactionFailed = true;
                    endTransaction();
                    resultStatus.setCode(TDStatus.INTERNAL_SERVER_ERROR);
                    return null;
                }

                String[] args = { docId };
                cursor = database.rawQuery("SELECT sequence, deleted FROM revs WHERE doc_id=? and current=1 ORDER BY revid DESC LIMIT 1", args);

                if(cursor.moveToFirst()) {
                    boolean wasAlreadyDeleted = (cursor.getInt(1) > 0);
                    if(wasAlreadyDeleted) {
                        // Make the deleted revision no longer current:
                        ContentValues updateContent = new ContentValues();
                        updateContent.put("current", 0);
                        int rowsUpdated = database.update("revs", updateContent, "sequence=" + cursor.getLong(0), null);
                        assert(rowsUpdated == 1);
                    }
                    else {
                        // docId already exists, current not deleted, conflict
                        transactionFailed = true;
                        endTransaction();
                        resultStatus.setCode(TDStatus.CONFLICT);
                        return null;
                    }
                }
            }
            else {
                // Inserting first revision, with no docID given: generate a unique docID:
                docId = generateDocumentId();
                docNumericID = insertDocumentID(docId);
                if(docNumericID <= 0) {
                    transactionFailed = true;
                    endTransaction();
                    resultStatus.setCode(TDStatus.INTERNAL_SERVER_ERROR);
                    return null;
                }
            }

            // Bump the revID and update the JSON:
            String newRevId = getNextRevisionId(prevRevId);
            byte[] data = null;
            if(!rev.isDeleted()) {
                data = encodeDocumentJSON(rev);
                if(data == null) {
                    // bad or missing json
                    transactionFailed = true;
                    endTransaction();
                    resultStatus.setCode(TDStatus.BAD_REQUEST);
                    return null;
                }
            }

            TDRevision result = rev.copyWithDocID(docId, newRevId);

            // Now insert the rev itself:
            long newSequence = insertRevision(result, docNumericID, parentSequence, true, data);
            if(newSequence == 0) {
                transactionFailed = true;
                endTransaction();
                resultStatus.setCode(TDStatus.INTERNAL_SERVER_ERROR);
                return null;
            }

            // Store any attachments:
            if(attachments != null) {
                TDStatus status = processAttachmentsForRevision(result, parentSequence);
                if(!status.isSuccessful()) {
                    transactionFailed = true;
                    endTransaction();
                    resultStatus.setCode(TDStatus.INTERNAL_SERVER_ERROR);
                    return null;
                }
            }

            if(deleted) {
                resultStatus.setCode(TDStatus.OK);
            }
            else {
                resultStatus.setCode(TDStatus.CREATED);
            }

            endTransaction();
            notifyChange(result, null);

            return result;

        } catch (SQLException e1) {
            Log.e(TDDatabase.TAG, "Error putting revision", e1);
            transactionFailed = true;
            endTransaction();
            resultStatus.setCode(TDStatus.INTERNAL_SERVER_ERROR);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
    }

    public TDStatus forceInsert(TDRevision rev, List<String> revHistory, URL source) {

        // First look up all locally-known revisions of this document:
        String docId = rev.getDocId();
        long docNumericID = getOrInsertDocNumericID(docId);
        TDRevisionList localRevs = getAllRevisionsOfDocumentID(docId, docNumericID, false);
        if(localRevs == null) {
            return new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
        }

        int historyCount = revHistory.size();
        assert(historyCount >= 1);

        // Walk through the remote history in chronological order, matching each revision ID to
        // a local revision. When the list diverges, start creating blank local revisions to fill
        // in the local history:
        long sequence = 0;
        long parentSequence = 0;

        for(int i = revHistory.size() - 1; i >= 0; --i) {
            parentSequence = sequence;
            String revId = revHistory.get(i);
            TDRevision localRev = localRevs.revWithDocIdAndRevId(docId, revId);
            if(localRev != null) {
                // This revision is known locally. Remember its sequence as the parent of the next one:
                sequence = localRev.getSequence();
                assert(sequence > 0);
            }
            else {
                // This revision isn't known, so add it:
                TDRevision newRev;
                byte[] data = null;
                boolean current = false;
                if(i == 0) {
                    // Hey, this is the leaf revision we're inserting:
                   newRev = rev;
                   if(!rev.isDeleted()) {
                       data = encodeDocumentJSON(rev);
                       if(data == null) {
                           return new TDStatus(TDStatus.BAD_REQUEST);
                       }
                   }
                   current = true;
                }
                else {
                    // It's an intermediate parent, so insert a stub:
                    newRev = new TDRevision(docId, revId, false);
                }

                // Insert it:
                sequence = insertRevision(newRev, docNumericID, parentSequence, current, data);

                if(sequence <= 0) {
                    return new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
                }

                if(i == 0) {
                    // Write any changed attachments for the new revision:
                    TDStatus status = processAttachmentsForRevision(rev, parentSequence);
                    if(!status.isSuccessful()) {
                        return status;
                    }
                }
            }
        }

        // Notify and return:
        notifyChange(rev, source);

        return new TDStatus(TDStatus.CREATED);
    }

    public TDRevisionList changesSince(int lastSeq, TDQueryOptions options) {
        if(options == null) {
            options = new TDQueryOptions();
        }

        String sql = "SELECT sequence, docid, revid, deleted FROM revs, docs "
                        + "WHERE sequence > ? AND current=1 "
                        + "AND revs.doc_id = docs.doc_id "
                        + "ORDER BY sequence LIMIT ?";
        String[] args = {Integer.toString(lastSeq), Integer.toString(options.getLimit())};
        Cursor cursor = null;
        TDRevisionList changes = null;

        try {
            cursor = database.rawQuery(sql, args);
            cursor.moveToFirst();
            changes = new TDRevisionList();
            while(!cursor.isAfterLast()) {
                TDRevision rev = new TDRevision(cursor.getString(1), cursor.getString(2), (cursor.getInt(3) > 0));
                rev.setSequence(cursor.getLong(0));
                changes.add(rev);
                cursor.moveToNext();
            }
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error looking for changes", e);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
        return changes;
    }

    /*** Views ***/

    public TDView getViewNamed(String name) {
        if(views == null) {
            views = new HashMap<String,TDView>();
        }
        TDView view = views.get(name);
        if(view == null) {
            view = new TDView(this, name);
            views.put(name, view);
        }
        return view;
    }

    public List<TDView> getAllViews() {
        Cursor cursor = null;
        List<TDView> result = null;

        try {
            cursor = database.rawQuery("SELECT name FROM views", null);
            cursor.moveToFirst();
            result = new ArrayList<TDView>();
            while(!cursor.isAfterLast()) {
                result.add(getViewNamed(cursor.getString(0)));
                cursor.moveToNext();
            }
        } catch (Exception e) {
            Log.e(TDDatabase.TAG, "Error getting all views", e);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    public TDStatus deleteViewNamed(String name) {
        TDStatus result = new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
        try {
            String[] whereArgs = { name };
            int rowsAffected = database.delete("views", "name=?", whereArgs);
            if(rowsAffected > 0) {
                result.setCode(TDStatus.OK);
            }
            else {
                result.setCode(TDStatus.NOT_FOUND);
            }
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error deleting view", e);
        }
        return result;
    }

    public Map<String,Object> getAllDocs(TDQueryOptions options) {
        if(options == null) {
            options = new TDQueryOptions();
        }

        long updateSeq = 0;
        if(options.isUpdateSeq()) {
            updateSeq = getLastSequence();  // TODO: needs to be atomic with the following SELECT
        }

        List<String> argsList = new ArrayList<String>();
        String cols = "revs.doc_id, docid, revid";
        if(options.isIncludeDocs()) {
            cols = cols + ", json, sequence";
        }

        String additionalWhereClause = "";
        Object minKey = options.getStartKey();
        Object maxKey = options.getEndKey();
        boolean inclusiveMin = true;
        boolean inclusiveMax = options.isInclusiveEnd();
        if(options.isDescending()) {
            minKey = maxKey;
            maxKey = options.getStartKey();
            inclusiveMin = inclusiveMax;
            inclusiveMax = true;
        }

        if(minKey != null) {
            assert(minKey instanceof String);
            if(inclusiveMin) {
                additionalWhereClause += " AND docid >= ?";
            } else {
                additionalWhereClause += " AND docid > ?";
            }
            argsList.add((String)minKey);
        }

        if(maxKey != null) {
            assert(maxKey instanceof String);
            if(inclusiveMax) {
                additionalWhereClause += " AND docid <= ?";
            }
            else {
                additionalWhereClause += " AND docid < ?";
            }
            argsList.add((String)maxKey);
        }


        String order = "ASC";
        if(options.isDescending()) {
            order = "DESC";
        }

        argsList.add(Integer.toString(options.getLimit()));
        argsList.add(Integer.toString(options.getSkip()));
        Cursor cursor = null;
        long lastDocID = 0;
        List<Map<String,Object>> rows = null;

        try {
            cursor = database.rawQuery("SELECT " + cols +
                    " FROM revs, docs " +
                    " WHERE current=1 AND deleted=0 AND docs.doc_id = revs.doc_id" + additionalWhereClause +
                    " ORDER BY docid " + order +
                    ", revid DESC LIMIT ? OFFSET ?", argsList.toArray(new String[argsList.size()]));

            cursor.moveToFirst();
            rows = new ArrayList<Map<String,Object>>();
            while(!cursor.isAfterLast()) {
                long docNumericID = cursor.getLong(0);
                if(docNumericID == lastDocID) {
                    cursor.moveToNext();
                    continue;
                }
                lastDocID = docNumericID;

                String docId = cursor.getString(1);
                String revId = cursor.getString(2);
                Map<String, Object> docContents = null;
                if(options.isIncludeDocs()) {
                    byte[] json = cursor.getBlob(3);
                    long sequence = cursor.getLong(4);
                    docContents = documentPropertiesFromJSON(json, docId, revId, sequence);
                }

                Map<String,Object> valueMap = new HashMap<String,Object>();
                valueMap.put("rev", revId);

                Map<String,Object> change = new HashMap<String, Object>();
                change.put("id", docId);
                change.put("key", docId);
                change.put("value", valueMap);
                if(docContents != null) {
                    change.put("doc", docContents);
                }

                rows.add(change);

                cursor.moveToNext();
            }
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error getting all docs", e);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

        int totalRows = cursor.getCount();  //??? Is this true, or does it ignore limit/offset?
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("rows", rows);
        result.put("total_rows", totalRows);
        result.put("offset", options.getSkip());
        if(updateSeq != 0) {
            result.put("update_seq", updateSeq);
        }


        return result;
    }

    /*** Replication ***/

    public static String quote(String string) {
        return string.replace("'", "''");
    }

    public static String joinQuoted(List<String> strings) {
        if(strings.size() == 0) {
            return "";
        }

        String result = "'";
        boolean first = true;
        for (String string : strings) {
            if(first) {
                first = false;
            }
            else {
                result = result + "','";
            }
            result = result + quote(string);
        }
        result = result + "'";

        return result;
    }

    public boolean findMissingRevisions(TDRevisionList touchRevs) {
        if(touchRevs.size() == 0) {
            return true;
        }

        String quotedDocIds = joinQuoted(touchRevs.getAllDocIds());
        String quotedRevIds = joinQuoted(touchRevs.getAllRevIds());

        String sql = "SELECT docid, revid FROM revs, docs " +
                      "WHERE docid IN (" +
                      quotedDocIds +
                      ") AND revid in (" +
                      quotedRevIds + ")" +
                      " AND revs.doc_id == docs.doc_id";

        Cursor cursor = null;
        try {
            cursor = database.rawQuery(sql, null);
            cursor.moveToFirst();
            while(!cursor.isAfterLast()) {
                TDRevision rev = touchRevs.revWithDocIdAndRevId(cursor.getString(0), cursor.getString(1));

                if(rev != null) {
                    touchRevs.remove(rev);
                }

                cursor.moveToNext();
            }
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error finding missing revisions", e);
            return false;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
        return true;
    }

    public TDRevisionList getAllRevisionsOfDocumentID(String docId, long docNumericID, boolean onlyCurrent) {

        String sql = null;
        if(onlyCurrent) {
            sql = "SELECT sequence, revid, deleted FROM revs " +
                    "WHERE doc_id=? AND current ORDER BY sequence DESC";
        }
        else {
            sql = "SELECT sequence, revid, deleted FROM revs " +
                    "WHERE doc_id=? ORDER BY sequence DESC";
        }

        String[] args = { Long.toString(docNumericID) };
        Cursor cursor = null;

        cursor = database.rawQuery(sql, args);

        TDRevisionList result;
        try {
            cursor.moveToFirst();
            result = new TDRevisionList();
            while(!cursor.isAfterLast()) {
                TDRevision rev = new TDRevision(docId, cursor.getString(1), (cursor.getInt(2) > 0));
                rev.setSequence(cursor.getLong(0));
                result.add(rev);
                cursor.moveToNext();
            }
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error getting all revisions of document", e);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    public TDRevisionList getAllRevisionsOfDocumentID(String docId, boolean onlyCurrent) {
        long docNumericId = getDocNumericID(docId);
        if(docNumericId < 0) {
            return null;
        }
        else if(docNumericId == 0) {
            return new TDRevisionList();
        }
        else {
            return getAllRevisionsOfDocumentID(docId, docNumericId, onlyCurrent);
        }
    }

    public List<TDRevision> getRevisionHistory(TDRevision rev) {
        String docId = rev.getDocId();
        String revId = rev.getRevId();
        assert((docId != null) && (revId != null));

        long docNumericId = getDocNumericID(docId);
        if(docNumericId < 0) {
            return null;
        }
        else if(docNumericId == 0) {
            return new ArrayList<TDRevision>();
        }

        String sql = "SELECT sequence, parent, revid, deleted FROM revs " +
                    "WHERE doc_id=? ORDER BY sequence DESC";
        String[] args = { Long.toString(docNumericId) };
        Cursor cursor = null;

        List<TDRevision> result;
        try {
            cursor = database.rawQuery(sql, args);

            cursor.moveToFirst();
            long lastSequence = 0;
            result = new ArrayList<TDRevision>();
            while(!cursor.isAfterLast()) {
                long sequence = cursor.getLong(0);
                boolean matches = false;
                if(lastSequence == 0) {
                    matches = revId.equals(cursor.getString(2));
                }
                else {
                    matches = (sequence == lastSequence);
                }
                if(matches) {
                    revId = cursor.getString(2);
                    boolean deleted = (cursor.getInt(3) > 0);
                    TDRevision aRev = new TDRevision(docId, revId, deleted);
                    aRev.setSequence(cursor.getLong(0));
                    result.add(aRev);
                    lastSequence = cursor.getLong(1);
                    if(lastSequence == 0) {
                        break;
                    }
                }
                cursor.moveToNext();
            }
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error getting revision history", e);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    /*** Attachments ***/
    public boolean insertAttachmentForSequenceWithNameAndType(byte[] contents, long sequence, String name, String contentType) {
        assert(contents != null);
        assert(sequence > 0);
        assert(name != null);

        TDBlobKey key = new TDBlobKey();
        if(!attachments.storeBlob(contents, key)) {
            return false;
        }

        byte[] keyData = key.getBytes();
        try {
            ContentValues args = new ContentValues();
            args.put("sequence", sequence);
            args.put("filename", name);
            args.put("key", keyData);
            args.put("type", contentType);
            args.put("length", contents.length);
            database.insert("attachments", null, args);
            return true;
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error inserting attachment", e);
            return false;
        }

    }

    public TDStatus copyAttachmentNamedFromSequenceToSequence(String name, long fromSeq, long toSeq) {
        assert(name != null);
        assert(toSeq > 0);
        if(fromSeq < 0) {
            return new TDStatus(TDStatus.NOT_FOUND);
        }

        Cursor cursor = null;

        String[] args = { Long.toString(toSeq), name, Long.toString(fromSeq), name };
        try {
            database.execSQL("INSERT INTO attachments (sequence, filename, key, type, length) " +
                                      "SELECT ?, ?, key, type, length FROM attachments " +
                                        "WHERE sequence=? AND filename=?", args);
            cursor = database.rawQuery("SELECT changes()", null);
            cursor.moveToFirst();
            int rowsUpdated = cursor.getInt(0);
            if(rowsUpdated == 0) {
                return new TDStatus(TDStatus.NOT_FOUND);
            }
            else {
                return new TDStatus(TDStatus.OK);
            }
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error copying attachment", e);
            return new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }


    }

    public TDAttachment getAttachmentForSequence(long sequence, String filename, TDStatus status) {
        assert(sequence > 0);
        assert(filename != null);


        Cursor cursor = null;

        String[] args = { Long.toString(sequence), filename };
        try {
            cursor = database.rawQuery("SELECT key, type FROM attachments WHERE sequence=? AND filename=?", args);

            if(!cursor.moveToFirst()) {
                status.setCode(TDStatus.NOT_FOUND);
                return null;
            }

            byte[] keyData = cursor.getBlob(0);
            //TODO add checks on key here? (ios version)
            TDBlobKey key = new TDBlobKey(keyData);
            byte[] contents = attachments.blobForKey(key);
            if(contents == null) {
                Log.e(TDDatabase.TAG, "Failed to load attachment");
                status.setCode(TDStatus.INTERNAL_SERVER_ERROR);
                return null;
            }
            else {
                status.setCode(TDStatus.OK);
                TDAttachment result = new TDAttachment();
                result.setData(contents);
                result.setContentType(cursor.getString(1));
                return result;
            }


        } catch (SQLException e) {
            status.setCode(TDStatus.INTERNAL_SERVER_ERROR);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

    }

    public Map<String,Object> getAttachmentsDictForSequenceWithContent(long sequence, boolean withContent) {
        assert(sequence > 0);

        Cursor cursor = null;

        String args[] = { Long.toString(sequence) };
        try {
            cursor = database.rawQuery("SELECT filename, key, type, length FROM attachments WHERE sequence=?", args);

            if(!cursor.moveToFirst()) {
                return null;
            }

            Map<String, Object> result = new HashMap<String, Object>();

            while(!cursor.isAfterLast()) {

                byte[] keyData = cursor.getBlob(1);
                TDBlobKey key = new TDBlobKey(keyData);
                String digestString = "sha1-" + Base64.encodeBytes(keyData);
                String dataBase64 = null;
                if(withContent) {
                    byte[] data = attachments.blobForKey(key);
                    if(data != null) {
                        dataBase64 = Base64.encodeBytes(data);
                    }
                    else {
                        Log.w(TDDatabase.TAG, "Error loading attachment");
                    }
                }

                Map<String, Object> attachment = new HashMap<String, Object>();
                attachment.put("stub", (dataBase64 != null) ? null : true);
                if(dataBase64 != null) {
                    attachment.put("data", dataBase64);
                }
                attachment.put("digest", digestString);
                attachment.put("content_type", cursor.getString(2));
                attachment.put("length", cursor.getInt(3));

                result.put(cursor.getString(0), attachment);

                cursor.moveToNext();
            }

            return result;

        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error getting attachments for sequence", e);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
    }

    public TDStatus processAttachmentsForRevision(TDRevision rev, long parentSequence) {
        assert(rev != null);
        long newSequence = rev.getSequence();
        assert(newSequence > parentSequence);

        // If there are no attachments in the new rev, there's nothing to do:
        Map<String,Object> newAttachments = null;
        Map<String,Object> properties = (Map<String,Object>)rev.getProperties();
        if(properties != null) {
            newAttachments = (Map<String,Object>)properties.get("_attachments");
        }
        if(newAttachments == null || newAttachments.size() == 0 || rev.isDeleted()) {
            return new TDStatus(TDStatus.OK);
        }

        for (String name : newAttachments.keySet()) {

            Map<String,Object> newAttach = (Map<String,Object>)newAttachments.get(name);
            String newContentBase64 = (String)newAttach.get("data");
            if(newContentBase64 != null) {
                byte[] newContents;
                try {
                    newContents = Base64.decode(newContentBase64);
                } catch (IOException e) {
                    Log.e(TDDatabase.TAG, "IOExeption parsing base64", e);
                    return new TDStatus(TDStatus.BAD_REQUEST);
                }
                if(newContents == null) {
                    return new TDStatus(TDStatus.BAD_REQUEST);
                }
                if(!insertAttachmentForSequenceWithNameAndType(newContents, newSequence, name, (String)newAttach.get("content_type"))) {
                    return new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
                }
            }
            else {
                // It's just a stub, so copy the previous revision's attachment entry:
                //? Should I enforce that the type and digest (if any) match?
                TDStatus status = copyAttachmentNamedFromSequenceToSequence(name, parentSequence, newSequence);
                if(!status.isSuccessful()) {
                    return status;
                }
            }
        }

        return new TDStatus(TDStatus.OK);
    }

    public TDStatus garbageCollectAttachments() {
        // First delete attachment rows for already-cleared revisions:
        // OPT: Could start after last sequence# we GC'd up to

        try {
            database.execSQL("DELETE FROM attachments WHERE sequence IN " +
                            "(SELECT sequence from revs WHERE json IS null)");
        }
        catch(SQLException e) {
            Log.e(TDDatabase.TAG, "Error deleting attachments", e);
        }


        // Now collect all remaining attachment IDs and tell the store to delete all but these:
        Cursor cursor = null;
        try {
            cursor = database.rawQuery("SELECT DISTINCT key FROM attachments", null);

            cursor.moveToFirst();
            List<TDBlobKey> allKeys = new ArrayList<TDBlobKey>();
            while(!cursor.isAfterLast()) {
                TDBlobKey key = new TDBlobKey(cursor.getBlob(0));
                allKeys.add(key);
                cursor.moveToNext();
            }

            int numDeleted = attachments.deleteBlobsExceptWithKeys(allKeys);
            if(numDeleted < 0) {
                return new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
            }

            Log.v(TDDatabase.TAG, "Deleted " + numDeleted + " attachments");

            return new TDStatus(TDStatus.OK);
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error finding attachment keys in use", e);
            return new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
    }
}
