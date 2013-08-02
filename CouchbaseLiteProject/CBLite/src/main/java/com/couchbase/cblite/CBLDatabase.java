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

package com.couchbase.cblite;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import com.couchbase.cblite.CBLDatabase.TDContentOptions;
import com.couchbase.cblite.replicator.CBLPuller;
import com.couchbase.cblite.replicator.CBLPusher;
import com.couchbase.cblite.replicator.CBLReplicator;
import com.couchbase.cblite.support.Base64;
import com.couchbase.cblite.support.FileDirUtils;
import com.couchbase.cblite.support.HttpClientFactory;
import com.couchbase.touchdb.TDCollateJSON;

/**
 * A CBLite database.
 */
public class CBLDatabase extends Observable {

    private String path;
    private String name;
    private SQLiteDatabase database;
    private boolean open = false;
    private int transactionLevel = 0;
    public static final String TAG = "CBLDatabase";

    private Map<String, CBLView> views;
    private Map<String, CBLFilterBlock> filters;
    private Map<String, CBLValidationBlock> validations;
    private Map<String, CBLBlobStoreWriter> pendingAttachmentsByDigest;
    private List<CBLReplicator> activeReplicators;
    private CBLBlobStore attachments;

    // Length that constitutes a 'big' attachment
    public static int kBigAttachmentLength = (16*1024);

    /**
     * Options for what metadata to include in document bodies
     */
    public enum TDContentOptions {
        TDIncludeAttachments, TDIncludeConflicts, TDIncludeRevs, TDIncludeRevsInfo, TDIncludeLocalSeq, TDNoBody, TDBigAttachmentsFollow
    }

    private static final Set<String> KNOWN_SPECIAL_KEYS;

    static {
        KNOWN_SPECIAL_KEYS = new HashSet<String>();
        KNOWN_SPECIAL_KEYS.add("_id");
        KNOWN_SPECIAL_KEYS.add("_rev");
        KNOWN_SPECIAL_KEYS.add("_attachments");
        KNOWN_SPECIAL_KEYS.add("_deleted");
        KNOWN_SPECIAL_KEYS.add("_revisions");
        KNOWN_SPECIAL_KEYS.add("_revs_info");
        KNOWN_SPECIAL_KEYS.add("_conflicts");
        KNOWN_SPECIAL_KEYS.add("_deleted_conflicts");
    }

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
            "    CREATE TABLE localdocs ( " +
            "        docid TEXT UNIQUE NOT NULL, " +
            "        revid TEXT NOT NULL, " +
            "        json BLOB); " +
            "    CREATE INDEX localdocs_by_docid ON localdocs(docid); " +
            "    CREATE TABLE views ( " +
            "        view_id INTEGER PRIMARY KEY, " +
            "        name TEXT UNIQUE NOT NULL," +
            "        version TEXT, " +
            "        lastsequence INTEGER DEFAULT 0); " +
            "    CREATE INDEX views_by_name ON views(name); " +
            "    CREATE TABLE maps ( " +
            "        view_id INTEGER NOT NULL REFERENCES views(view_id) ON DELETE CASCADE, " +
            "        sequence INTEGER NOT NULL REFERENCES revs(sequence) ON DELETE CASCADE, " +
            "        key TEXT NOT NULL COLLATE JSON, " +
            "        value TEXT); " +
            "    CREATE INDEX maps_keys on maps(view_id, key COLLATE JSON); " +
            "    CREATE TABLE attachments ( " +
            "        sequence INTEGER NOT NULL REFERENCES revs(sequence) ON DELETE CASCADE, " +
            "        filename TEXT NOT NULL, " +
            "        key BLOB NOT NULL, " +
            "        type TEXT, " +
            "        length INTEGER NOT NULL, " +
            "        revpos INTEGER DEFAULT 0); " +
            "    CREATE INDEX attachments_by_sequence on attachments(sequence, filename); " +
            "    CREATE TABLE replicators ( " +
            "        remote TEXT NOT NULL, " +
            "        push BOOLEAN, " +
            "        last_sequence TEXT, " +
            "        UNIQUE (remote, push)); " +
            "    PRAGMA user_version = 3";             // at the end, update user_version

    /*************************************************************************************************/
    /*** CBLDatabase                                                                                ***/
    /*************************************************************************************************/

    public String getAttachmentStorePath() {
        String attachmentStorePath = path;
        int lastDotPosition = attachmentStorePath.lastIndexOf('.');
        if( lastDotPosition > 0 ) {
            attachmentStorePath = attachmentStorePath.substring(0, lastDotPosition);
        }
        attachmentStorePath = attachmentStorePath + File.separator + "attachments";
        return attachmentStorePath;
    }

    public static CBLDatabase createEmptyDBAtPath(String path) {
        if(!FileDirUtils.removeItemIfExists(path)) {
            return null;
        }
        CBLDatabase result = new CBLDatabase(path);
        File af = new File(result.getAttachmentStorePath());
        //recursively delete attachments path
        if(!FileDirUtils.deleteRecursive(af)) {
            return null;
        }
        if(!result.open()) {
            return null;
        }
        return result;
    }

    public CBLDatabase(String path) {
        assert(path.startsWith("/")); //path must be absolute
        this.path = path;
        this.name = FileDirUtils.getDatabaseNameFromPath(path);
    }

    public String toString() {
        return this.getClass().getName() + "[" + path + "]";
    }

    public boolean exists() {
        return new File(path).exists();
    }

    /**
     * Replaces the database with a copy of another database.
     *
     * This is primarily used to install a canned database on first launch of an app, in which case you should first check .exists to avoid replacing the database if it exists already. The canned database would have been copied into your app bundle at build time.
     *
     * @param databasePath  Path of the database file that should replace this one.
     * @param attachmentsPath  Path of the associated attachments directory, or nil if there are no attachments.
     * @return  true if the database was copied, IOException if an error occurs
     **/
    public boolean replaceWithDatabase(String databasePath, String attachmentsPath) throws IOException {
        String dstAttachmentsPath = this.getAttachmentStorePath();
        File sourceFile = new File(databasePath);
        File destFile = new File(path);
        FileDirUtils.copyFile(sourceFile, destFile);
        File attachmentsFile = new File(dstAttachmentsPath);
        FileDirUtils.deleteRecursive(attachmentsFile);
        attachmentsFile.mkdirs();
        if(attachmentsPath != null) {
            FileDirUtils.copyFolder(new File(attachmentsPath), attachmentsFile);
        }
        return true;
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
            TDCollateJSON.registerCustomCollators(database);
        }
        catch(SQLiteException e) {
            Log.e(CBLDatabase.TAG, "Error opening", e);
            return false;
        }

        // Stuff we need to initialize every time the database opens:
        if(!initialize("PRAGMA foreign_keys = ON;")) {
            Log.e(CBLDatabase.TAG, "Error turning on foreign keys");
            return false;
        }

        // Check the user_version number we last stored in the database:
        int dbVersion = database.getVersion();

        // Incompatible version changes increment the hundreds' place:
        if(dbVersion >= 100) {
            Log.w(CBLDatabase.TAG, "CBLDatabase: Database version (" + dbVersion + ") is newer than I know how to work with");
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
            dbVersion = 3;
        }

        if (dbVersion < 2) {
            // Version 2: added attachments.revpos
            String upgradeSql = "ALTER TABLE attachments ADD COLUMN revpos INTEGER DEFAULT 0; " +
                                "PRAGMA user_version = 2";
            if(!initialize(upgradeSql)) {
                database.close();
                return false;
            }
            dbVersion = 2;
        }

        if (dbVersion < 3) {
            String upgradeSql = "CREATE TABLE localdocs ( " +
                    "docid TEXT UNIQUE NOT NULL, " +
                    "revid TEXT NOT NULL, " +
                    "json BLOB); " +
                    "CREATE INDEX localdocs_by_docid ON localdocs(docid); " +
                    "PRAGMA user_version = 3";
            if(!initialize(upgradeSql)) {
                database.close();
                return false;
            }
            dbVersion = 3;
        }

        if (dbVersion < 4) {
            String upgradeSql = "CREATE TABLE info ( " +
                    "key TEXT PRIMARY KEY, " +
                    "value TEXT); " +
                    "INSERT INTO INFO (key, value) VALUES ('privateUUID', '" + CBLMisc.TDCreateUUID() + "'); " +
                    "INSERT INTO INFO (key, value) VALUES ('publicUUID',  '" + CBLMisc.TDCreateUUID() + "'); " +
                    "PRAGMA user_version = 4";
            if(!initialize(upgradeSql)) {
                database.close();
                return false;
            }
        }

        try {
            attachments = new CBLBlobStore(getAttachmentStorePath());
        } catch (IllegalArgumentException e) {
            Log.e(CBLDatabase.TAG, "Could not initialize attachment store", e);
            database.close();
            return false;
        }

        open = true;
        return true;
    }

    public boolean close() {
        if(!open) {
            return false;
        }

        if(views != null) {
            for (CBLView view : views.values()) {
                view.databaseClosing();
            }
        }
        views = null;

        if(activeReplicators != null) {
            for(CBLReplicator replicator : activeReplicators) {
                replicator.databaseClosing();
            }
            activeReplicators = null;
        }

        if(database != null && database.isOpen()) {
            database.close();
        }
        open = false;
        transactionLevel = 0;
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

        boolean deleteStatus = file.delete();
        //recursively delete attachments path
        boolean deleteAttachmentStatus = FileDirUtils.deleteRecursive(attachmentsFile);
        return deleteStatus && deleteAttachmentStatus;
    }

    public String getPath() {
        return path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    // Leave this package protected, so it can only be used
    // CBLView uses this accessor
    SQLiteDatabase getDatabase() {
        return database;
    }

    public CBLBlobStore getAttachments() {
        return attachments;
    }

    public CBLBlobStoreWriter getAttachmentWriter() {
        return new CBLBlobStoreWriter(getAttachments());
    }

    public long totalDataSize() {
        File f = new File(path);
        long size = f.length() + attachments.totalDataSize();
        return size;
    }

    /**
     * Begins a database transaction. Transactions can nest.
     * Every beginTransaction() must be balanced by a later endTransaction()
     */
    public boolean beginTransaction() {
        try {
            database.beginTransaction();
            ++transactionLevel;
            //Log.v(TAG, "Begin transaction (level " + Integer.toString(transactionLevel) + ")...");
        } catch (SQLException e) {
            return false;
        }
        return true;
    }

    /**
     * Commits or aborts (rolls back) a transaction.
     *
     * @param commit If true, commits; if false, aborts and rolls back, undoing all changes made since the matching -beginTransaction call, *including* any committed nested transactions.
     */
    public boolean endTransaction(boolean commit) {
        assert(transactionLevel > 0);

        if(commit) {
            //Log.v(TAG, "Committing transaction (level " + Integer.toString(transactionLevel) + ")...");
            database.setTransactionSuccessful();
            database.endTransaction();
        }
        else {
            Log.v(TAG, "CANCEL transaction (level " + Integer.toString(transactionLevel) + ")...");
            try {
                database.endTransaction();
            } catch (SQLException e) {
                return false;
            }
        }

        --transactionLevel;
        return true;
    }

    /**
     * Compacts the database storage by removing the bodies and attachments of obsolete revisions.
     */
    public CBLStatus compact() {
        // Can't delete any rows because that would lose revision tree history.
        // But we can remove the JSON of non-current revisions, which is most of the space.
        try {
            Log.v(CBLDatabase.TAG, "Deleting JSON of old revisions...");
            ContentValues args = new ContentValues();
            args.put("json", (String)null);
            database.update("revs", args, "current=0", null);
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error compacting", e);
            return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        }

        Log.v(CBLDatabase.TAG, "Deleting old attachments...");
        CBLStatus result = garbageCollectAttachments();

        Log.v(CBLDatabase.TAG, "Vacuuming SQLite database...");
        try {
            database.execSQL("VACUUM");
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error vacuuming database", e);
            return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        }

        return result;
    }

    public String privateUUID() {
        String result = null;
        Cursor cursor = null;
        try {
            cursor = database.rawQuery("SELECT value FROM info WHERE key='privateUUID'", null);
            if(cursor.moveToFirst()) {
                result = cursor.getString(0);
            }
        } catch(SQLException e) {
            Log.e(TAG, "Error querying privateUUID", e);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    public String publicUUID() {
        String result = null;
        Cursor cursor = null;
        try {
            cursor = database.rawQuery("SELECT value FROM info WHERE key='publicUUID'", null);
            if(cursor.moveToFirst()) {
                result = cursor.getString(0);
            }
        } catch(SQLException e) {
            Log.e(TAG, "Error querying privateUUID", e);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    /** GETTING DOCUMENTS: **/

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
            Log.e(CBLDatabase.TAG, "Error getting document count", e);
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
            Log.e(CBLDatabase.TAG, "Error getting last sequence", e);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    /** Splices the contents of an NSDictionary into JSON data (that already represents a dict), without parsing the JSON. */
    public  byte[] appendDictToJSON(byte[] json, Map<String,Object> dict) {
        if(dict.size() == 0) {
            return json;
        }

        byte[] extraJSON = null;
        try {
            extraJSON = CBLServer.getObjectMapper().writeValueAsBytes(dict);
        } catch (Exception e) {
            Log.e(CBLDatabase.TAG, "Error convert extra JSON to bytes", e);
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
    public Map<String,Object> extraPropertiesForRevision(CBLRevision rev, EnumSet<TDContentOptions> contentOptions) {

        String docId = rev.getDocId();
        String revId = rev.getRevId();
        long sequenceNumber = rev.getSequence();
        assert(revId != null);
        assert(sequenceNumber > 0);

        // Get attachment metadata, and optionally the contents:
        Map<String, Object> attachmentsDict = getAttachmentsDictForSequenceWithContent(sequenceNumber, contentOptions);

        // Get more optional stuff to put in the properties:
        //OPT: This probably ends up making redundant SQL queries if multiple options are enabled.
        Long localSeq = null;
        if(contentOptions.contains(TDContentOptions.TDIncludeLocalSeq)) {
            localSeq = sequenceNumber;
        }

        Map<String,Object> revHistory = null;
        if(contentOptions.contains(TDContentOptions.TDIncludeRevs)) {
            revHistory = getRevisionHistoryDict(rev);
        }

        List<Object> revsInfo = null;
        if(contentOptions.contains(TDContentOptions.TDIncludeRevsInfo)) {
            revsInfo = new ArrayList<Object>();
            List<CBLRevision> revHistoryFull = getRevisionHistory(rev);
            for (CBLRevision historicalRev : revHistoryFull) {
                Map<String,Object> revHistoryItem = new HashMap<String,Object>();
                String status = "available";
                if(historicalRev.isDeleted()) {
                    status = "deleted";
                }
                // TODO: Detect missing revisions, set status="missing"
                revHistoryItem.put("rev", historicalRev.getRevId());
                revHistoryItem.put("status", status);
                revsInfo.add(revHistoryItem);
            }
        }

        List<String> conflicts = null;
        if(contentOptions.contains(TDContentOptions.TDIncludeConflicts)) {
            CBLRevisionList revs = getAllRevisionsOfDocumentID(docId, true);
            if(revs.size() > 1) {
                conflicts = new ArrayList<String>();
                for (CBLRevision historicalRev : revs) {
                    if(!historicalRev.equals(rev)) {
                        conflicts.add(historicalRev.getRevId());
                    }
                }
            }
        }

        Map<String,Object> result = new HashMap<String,Object>();
        result.put("_id", docId);
        result.put("_rev", revId);
        if(rev.isDeleted()) {
            result.put("_deleted", true);
        }
        if(attachmentsDict != null) {
            result.put("_attachments", attachmentsDict);
        }
        if(localSeq != null) {
            result.put("_local_seq", localSeq);
        }
        if(revHistory != null) {
            result.put("_revisions", revHistory);
        }
        if(revsInfo != null) {
            result.put("_revs_info", revsInfo);
        }
        if(conflicts != null) {
            result.put("_conflicts", conflicts);
        }

        return result;
    }

    /** Inserts the _id, _rev and _attachments properties into the JSON data and stores it in rev.
    Rev must already have its revID and sequence properties set. */
    public void expandStoredJSONIntoRevisionWithAttachments(byte[] json, CBLRevision rev, EnumSet<TDContentOptions> contentOptions) {
        Map<String,Object> extra = extraPropertiesForRevision(rev, contentOptions);
        if(json != null) {
            rev.setJson(appendDictToJSON(json, extra));
        }
        else {
            rev.setProperties(extra);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> documentPropertiesFromJSON(byte[] json, String docId, String revId, long sequence, EnumSet<TDContentOptions> contentOptions) {

        CBLRevision rev = new CBLRevision(docId, revId, false);
        rev.setSequence(sequence);
        Map<String,Object> extra = extraPropertiesForRevision(rev, contentOptions);
        if(json == null) {
            return extra;
        }

      Map<String,Object> docProperties = null;
      try {
          docProperties = CBLServer.getObjectMapper().readValue(json, Map.class);
          docProperties.putAll(extra);
          return docProperties;
      } catch (Exception e) {
          Log.e(CBLDatabase.TAG, "Error serializing properties to JSON", e);
      }

      return docProperties;
    }

    public CBLRevision getDocumentWithIDAndRev(String id, String rev, EnumSet<TDContentOptions> contentOptions) {
        CBLRevision result = null;
        String sql;

        Cursor cursor = null;
        try {
            cursor = null;
            String cols = "revid, deleted, sequence";
            if(!contentOptions.contains(TDContentOptions.TDNoBody)) {
                cols += ", json";
            }
            if(rev != null) {
                sql = "SELECT " + cols + " FROM revs, docs WHERE docs.docid=? AND revs.doc_id=docs.doc_id AND revid=? LIMIT 1";
                String[] args = {id, rev};
                cursor = database.rawQuery(sql, args);
            }
            else {
                sql = "SELECT " + cols + " FROM revs, docs WHERE docs.docid=? AND revs.doc_id=docs.doc_id and current=1 and deleted=0 ORDER BY revid DESC LIMIT 1";
                String[] args = {id};
                cursor = database.rawQuery(sql, args);
            }

            if(cursor.moveToFirst()) {
                if(rev == null) {
                    rev = cursor.getString(0);
                }
                boolean deleted = (cursor.getInt(1) > 0);
                result = new CBLRevision(id, rev, deleted);
                result.setSequence(cursor.getLong(2));
                if(!contentOptions.equals(EnumSet.of(TDContentOptions.TDNoBody))) {
                    byte[] json = null;
                    if(!contentOptions.contains(TDContentOptions.TDNoBody)) {
                        json = cursor.getBlob(3);
                    }
                    expandStoredJSONIntoRevisionWithAttachments(json, result, contentOptions);
                }
            }
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error getting document with id and rev", e);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    public boolean existsDocumentWithIDAndRev(String docId, String revId) {
        return getDocumentWithIDAndRev(docId, revId, EnumSet.of(TDContentOptions.TDNoBody)) != null;
    }

    public CBLStatus loadRevisionBody(CBLRevision rev, EnumSet<TDContentOptions> contentOptions) {
        if(rev.getBody() != null) {
            return new CBLStatus(CBLStatus.OK);
        }
        assert((rev.getDocId() != null) && (rev.getRevId() != null));

        Cursor cursor = null;
        CBLStatus result = new CBLStatus(CBLStatus.NOT_FOUND);
        try {
            String sql = "SELECT sequence, json FROM revs, docs WHERE revid=? AND docs.docid=? AND revs.doc_id=docs.doc_id LIMIT 1";
            String[] args = { rev.getRevId(), rev.getDocId()};
            cursor = database.rawQuery(sql, args);
            if(cursor.moveToFirst()) {
                result.setCode(CBLStatus.OK);
                rev.setSequence(cursor.getLong(0));
                expandStoredJSONIntoRevisionWithAttachments(cursor.getBlob(1), rev, contentOptions);
            }
        } catch(SQLException e) {
            Log.e(CBLDatabase.TAG, "Error loading revision body", e);
            return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
        return result;
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
            Log.e(CBLDatabase.TAG, "Error getting doc numeric id", e);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    /** HISTORY: **/

    /**
     * Returns all the known revisions (or all current/conflicting revisions) of a document.
     */
    public CBLRevisionList getAllRevisionsOfDocumentID(String docId, long docNumericID, boolean onlyCurrent) {

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

        CBLRevisionList result;
        try {
            cursor.moveToFirst();
            result = new CBLRevisionList();
            while(!cursor.isAfterLast()) {
                CBLRevision rev = new CBLRevision(docId, cursor.getString(1), (cursor.getInt(2) > 0));
                rev.setSequence(cursor.getLong(0));
                result.add(rev);
                cursor.moveToNext();
            }
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error getting all revisions of document", e);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    public CBLRevisionList getAllRevisionsOfDocumentID(String docId, boolean onlyCurrent) {
        long docNumericId = getDocNumericID(docId);
        if(docNumericId < 0) {
            return null;
        }
        else if(docNumericId == 0) {
            return new CBLRevisionList();
        }
        else {
            return getAllRevisionsOfDocumentID(docId, docNumericId, onlyCurrent);
        }
    }

    public List<String> getConflictingRevisionIDsOfDocID(String docID) {
        long docIdNumeric = getDocNumericID(docID);
        if(docIdNumeric < 0) {
            return null;
        }

        List<String> result = new ArrayList<String>();
        Cursor cursor = null;
        try {
            String[] args = { Long.toString(docIdNumeric) };
            cursor = database.rawQuery("SELECT revid FROM revs WHERE doc_id=? AND current " +
                                           "ORDER BY revid DESC OFFSET 1", args);
            cursor.moveToFirst();
            while(!cursor.isAfterLast()) {
                result.add(cursor.getString(0));
                cursor.moveToNext();
            }

        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error getting all revisions of document", e);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    public String findCommonAncestorOf(CBLRevision rev, List<String> revIDs) {
        String result = null;

    	if (revIDs.size() == 0)
    		return null;
    	String docId = rev.getDocId();
    	long docNumericID = getDocNumericID(docId);
    	if (docNumericID <= 0)
    		return null;
    	String quotedRevIds = joinQuoted(revIDs);
    	String sql = "SELECT revid FROM revs " +
    			"WHERE doc_id=? and revid in (" + quotedRevIds + ") and revid <= ? " +
    			"ORDER BY revid DESC LIMIT 1";
    	String[] args = { Long.toString(docNumericID) };

    	Cursor cursor = null;
    	try {
    		cursor = database.rawQuery(sql, args);
    		cursor.moveToFirst();
            if(!cursor.isAfterLast()) {
                result = cursor.getString(0);
    		}

    	} catch (SQLException e) {
    		Log.e(CBLDatabase.TAG, "Error getting all revisions of document", e);
    	} finally {
    		if(cursor != null) {
    			cursor.close();
    		}
    	}

    	return result;
    }

    /**
     * Returns an array of TDRevs in reverse chronological order, starting with the given revision.
     */
    public List<CBLRevision> getRevisionHistory(CBLRevision rev) {
        String docId = rev.getDocId();
        String revId = rev.getRevId();
        assert((docId != null) && (revId != null));

        long docNumericId = getDocNumericID(docId);
        if(docNumericId < 0) {
            return null;
        }
        else if(docNumericId == 0) {
            return new ArrayList<CBLRevision>();
        }

        String sql = "SELECT sequence, parent, revid, deleted FROM revs " +
                    "WHERE doc_id=? ORDER BY sequence DESC";
        String[] args = { Long.toString(docNumericId) };
        Cursor cursor = null;

        List<CBLRevision> result;
        try {
            cursor = database.rawQuery(sql, args);

            cursor.moveToFirst();
            long lastSequence = 0;
            result = new ArrayList<CBLRevision>();
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
                    CBLRevision aRev = new CBLRevision(docId, revId, deleted);
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
            Log.e(CBLDatabase.TAG, "Error getting revision history", e);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    // Splits a revision ID into its generation number and opaque suffix string
    public static int parseRevIDNumber(String rev) {
        int result = -1;
        int dashPos = rev.indexOf("-");
        if(dashPos >= 0) {
            try {
                result = Integer.parseInt(rev.substring(0, dashPos));
            } catch (NumberFormatException e) {
                // ignore, let it return -1
            }
        }
        return result;
    }

    // Splits a revision ID into its generation number and opaque suffix string
    public static String parseRevIDSuffix(String rev) {
        String result = null;
        int dashPos = rev.indexOf("-");
        if(dashPos >= 0) {
            result = rev.substring(dashPos + 1);
        }
        return result;
    }

    public static Map<String,Object> makeRevisionHistoryDict(List<CBLRevision> history) {
        if(history == null) {
            return null;
        }

        // Try to extract descending numeric prefixes:
        List<String> suffixes = new ArrayList<String>();
        int start = -1;
        int lastRevNo = -1;
        for (CBLRevision rev : history) {
            int revNo = parseRevIDNumber(rev.getRevId());
            String suffix = parseRevIDSuffix(rev.getRevId());
            if(revNo > 0 && suffix.length() > 0) {
                if(start < 0) {
                    start = revNo;
                }
                else if(revNo != lastRevNo - 1) {
                    start = -1;
                    break;
                }
                lastRevNo = revNo;
                suffixes.add(suffix);
            }
            else {
                start = -1;
                break;
            }
        }

        Map<String,Object> result = new HashMap<String,Object>();
        if(start == -1) {
            // we failed to build sequence, just stuff all the revs in list
            suffixes = new ArrayList<String>();
            for (CBLRevision rev : history) {
                suffixes.add(rev.getRevId());
            }
        }
        else {
            result.put("start", start);
        }
        result.put("ids", suffixes);

        return result;
    }

    /**
     * Returns the revision history as a _revisions dictionary, as returned by the REST API's ?revs=true option.
     */
    public Map<String,Object> getRevisionHistoryDict(CBLRevision rev) {
        return makeRevisionHistoryDict(getRevisionHistory(rev));
    }

    public CBLRevisionList changesSince(long lastSeq, CBLChangesOptions options, CBLFilterBlock filter) {
        // http://wiki.apache.org/couchdb/HTTP_database_API#Changes
        if(options == null) {
            options = new CBLChangesOptions();
        }

        boolean includeDocs = options.isIncludeDocs() || (filter != null);
        String additionalSelectColumns =  "";
        if(includeDocs) {
            additionalSelectColumns = ", json";
        }

        String sql = "SELECT sequence, revs.doc_id, docid, revid, deleted" + additionalSelectColumns + " FROM revs, docs "
                        + "WHERE sequence > ? AND current=1 "
                        + "AND revs.doc_id = docs.doc_id "
                        + "ORDER BY revs.doc_id, revid DESC";
        String[] args = {Long.toString(lastSeq)};
        Cursor cursor = null;
        CBLRevisionList changes = null;

        try {
            cursor = database.rawQuery(sql, args);
            cursor.moveToFirst();
            changes = new CBLRevisionList();
            long lastDocId = 0;
            while(!cursor.isAfterLast()) {
                if(!options.isIncludeConflicts()) {
                    // Only count the first rev for a given doc (the rest will be losing conflicts):
                    long docNumericId = cursor.getLong(1);
                    if(docNumericId == lastDocId) {
                        cursor.moveToNext();
                        continue;
                    }
                    lastDocId = docNumericId;
                }

                CBLRevision rev = new CBLRevision(cursor.getString(2), cursor.getString(3), (cursor.getInt(4) > 0));
                rev.setSequence(cursor.getLong(0));
                if(includeDocs) {
                    expandStoredJSONIntoRevisionWithAttachments(cursor.getBlob(5), rev, options.getContentOptions());
                }
                if((filter == null) || (filter.filter(rev))) {
                    changes.add(rev);
                }
                cursor.moveToNext();
            }
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error looking for changes", e);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

        if(options.isSortBySequence()) {
            changes.sortBySequence();
        }
        changes.limit(options.getLimit());
        return changes;
    }

    /**
     * Define or clear a named filter function.
     *
     * These aren't used directly by CBLDatabase, but they're looked up by CBLRouter when a _changes request has a ?filter parameter.
     */
    public void defineFilter(String filterName, CBLFilterBlock filter) {
        if(filters == null) {
            filters = new HashMap<String,CBLFilterBlock>();
        }
        if (filter != null) {
            filters.put(filterName, filter);
        }
        else {
            filters.remove(filterName);
        }
    }

    public CBLFilterBlock getFilterNamed(String filterName) {
        CBLFilterBlock result = null;
        if(filters != null) {
            result = filters.get(filterName);
        }
        return result;
    }

    /** VIEWS: **/

    public CBLView registerView(CBLView view) {
        if(view == null) {
            return null;
        }
        if(views == null) {
            views = new HashMap<String,CBLView>();
        }
        views.put(view.getName(), view);
        return view;
    }

    public CBLView getViewNamed(String name) {
        CBLView view = null;
        if(views != null) {
            view = views.get(name);
        }
        if(view != null) {
            return view;
        }
        return registerView(new CBLView(this, name));
    }

    public CBLView getExistingViewNamed(String name) {
        CBLView view = null;
        if(views != null) {
            view = views.get(name);
        }
        if(view != null) {
            return view;
        }
        view = new CBLView(this, name);
        if(view.getViewId() == 0) {
            return null;
        }

        return registerView(view);
    }

    public List<CBLView> getAllViews() {
        Cursor cursor = null;
        List<CBLView> result = null;

        try {
            cursor = database.rawQuery("SELECT name FROM views", null);
            cursor.moveToFirst();
            result = new ArrayList<CBLView>();
            while(!cursor.isAfterLast()) {
                result.add(getViewNamed(cursor.getString(0)));
                cursor.moveToNext();
            }
        } catch (Exception e) {
            Log.e(CBLDatabase.TAG, "Error getting all views", e);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    public CBLStatus deleteViewNamed(String name) {
        CBLStatus result = new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        try {
            String[] whereArgs = { name };
            int rowsAffected = database.delete("views", "name=?", whereArgs);
            if(rowsAffected > 0) {
                result.setCode(CBLStatus.OK);
            }
            else {
                result.setCode(CBLStatus.NOT_FOUND);
            }
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error deleting view", e);
        }
        return result;
    }

    //FIX: This has a lot of code in common with -[CBLView queryWithOptions:status:]. Unify the two!
    public Map<String,Object> getDocsWithIDs(List<String> docIDs, CBLQueryOptions options) {
        if(options == null) {
            options = new CBLQueryOptions();
        }

        long updateSeq = 0;
        if(options.isUpdateSeq()) {
            updateSeq = getLastSequence();  // TODO: needs to be atomic with the following SELECT
        }

        // Generate the SELECT statement, based on the options:
        String additionalCols = "";
        if(options.isIncludeDocs()) {
            additionalCols = ", json, sequence";
        }
        String sql = "SELECT revs.doc_id, docid, revid, deleted" + additionalCols + " FROM revs, docs WHERE";

        if(docIDs != null) {
            sql += " docid IN (" + joinQuoted(docIDs) + ")";
        } else {
            sql += " deleted=0";
        }

        sql += " AND current=1 AND docs.doc_id = revs.doc_id";

        List<String> argsList = new ArrayList<String>();
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
                sql += " AND docid >= ?";
            } else {
                sql += " AND docid > ?";
            }
            argsList.add((String)minKey);
        }

        if(maxKey != null) {
            assert(maxKey instanceof String);
            if(inclusiveMax) {
                sql += " AND docid <= ?";
            }
            else {
                sql += " AND docid < ?";
            }
            argsList.add((String)maxKey);
        }


        String order = "ASC";
        if(options.isDescending()) {
            order = "DESC";
        }

        sql += " ORDER BY docid " + order + ", revid DESC LIMIT ? OFFSET ?";

        argsList.add(Integer.toString(options.getLimit()));
        argsList.add(Integer.toString(options.getSkip()));
        Cursor cursor = null;
        long lastDocID = 0;
        List<Map<String,Object>> rows = null;

        try {
            cursor = database.rawQuery(sql, argsList.toArray(new String[argsList.size()]));

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
                boolean deleted = cursor.getInt(3) > 0;
                if(options.isIncludeDocs() && !deleted) {
                    byte[] json = cursor.getBlob(4);
                    long sequence = cursor.getLong(5);
                    docContents = documentPropertiesFromJSON(json, docId, revId, sequence, options.getContentOptions());
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
                if(deleted) {
                    change.put("deleted", true);
                }

                rows.add(change);

                cursor.moveToNext();
            }
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error getting all docs", e);
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

    public Map<String,Object> getAllDocs(CBLQueryOptions options) {
        return getDocsWithIDs(null, options);
    }

    /*************************************************************************************************/
    /*** CBLDatabase+Attachments                                                                    ***/
    /*************************************************************************************************/

    public CBLStatus insertAttachmentForSequenceWithNameAndType(InputStream contentStream, long sequence, String name, String contentType, int revpos) {
        assert(sequence > 0);
        assert(name != null);

        CBLBlobKey key = new CBLBlobKey();
        if(!attachments.storeBlobStream(contentStream, key)) {
            return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        }

        byte[] keyData = key.getBytes();
        try {
            ContentValues args = new ContentValues();
            args.put("sequence", sequence);
            args.put("filename", name);
            args.put("key", keyData);
            args.put("type", contentType);
            args.put("length", attachments.getSizeOfBlob(key));
            args.put("revpos", revpos);
            database.insert("attachments", null, args);
            return new CBLStatus(CBLStatus.CREATED);
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error inserting attachment", e);
            return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        }
    }

    public CBLStatus copyAttachmentNamedFromSequenceToSequence(String name, long fromSeq, long toSeq) {
        assert(name != null);
        assert(toSeq > 0);
        if(fromSeq < 0) {
            return new CBLStatus(CBLStatus.NOT_FOUND);
        }

        Cursor cursor = null;

        String[] args = { Long.toString(toSeq), name, Long.toString(fromSeq), name };
        try {
            database.execSQL("INSERT INTO attachments (sequence, filename, key, type, length, revpos) " +
                                      "SELECT ?, ?, key, type, length, revpos FROM attachments " +
                                        "WHERE sequence=? AND filename=?", args);
            cursor = database.rawQuery("SELECT changes()", null);
            cursor.moveToFirst();
            int rowsUpdated = cursor.getInt(0);
            if(rowsUpdated == 0) {
                // Oops. This means a glitch in our attachment-management or pull code,
                // or else a bug in the upstream server.
                Log.w(CBLDatabase.TAG, "Can't find inherited attachment " + name + " from seq# " + Long.toString(fromSeq) + " to copy to " + Long.toString(toSeq));
                return new CBLStatus(CBLStatus.NOT_FOUND);
            }
            else {
                return new CBLStatus(CBLStatus.OK);
            }
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error copying attachment", e);
            return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Returns the content and MIME type of an attachment
     */
    public CBLAttachment getAttachmentForSequence(long sequence, String filename, CBLStatus status) {
        assert(sequence > 0);
        assert(filename != null);


        Cursor cursor = null;

        String[] args = { Long.toString(sequence), filename };
        try {
            cursor = database.rawQuery("SELECT key, type FROM attachments WHERE sequence=? AND filename=?", args);

            if(!cursor.moveToFirst()) {
                status.setCode(CBLStatus.NOT_FOUND);
                return null;
            }

            byte[] keyData = cursor.getBlob(0);
            //TODO add checks on key here? (ios version)
            CBLBlobKey key = new CBLBlobKey(keyData);
            InputStream contentStream = attachments.blobStreamForKey(key);
            if(contentStream == null) {
                Log.e(CBLDatabase.TAG, "Failed to load attachment");
                status.setCode(CBLStatus.INTERNAL_SERVER_ERROR);
                return null;
            }
            else {
                status.setCode(CBLStatus.OK);
                CBLAttachment result = new CBLAttachment();
                result.setContentStream(contentStream);
                result.setContentType(cursor.getString(1));
                return result;
            }


        } catch (SQLException e) {
            status.setCode(CBLStatus.INTERNAL_SERVER_ERROR);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

    }

    /**
     * Constructs an "_attachments" dictionary for a revision, to be inserted in its JSON body.
     */
    public Map<String,Object> getAttachmentsDictForSequenceWithContent(long sequence, EnumSet<TDContentOptions> contentOptions) {
        assert(sequence > 0);

        Cursor cursor = null;

        String args[] = { Long.toString(sequence) };
        try {
            cursor = database.rawQuery("SELECT filename, key, type, length, revpos FROM attachments WHERE sequence=?", args);

            if(!cursor.moveToFirst()) {
                return null;
            }

            Map<String, Object> result = new HashMap<String, Object>();

            while(!cursor.isAfterLast()) {

                boolean dataSuppressed = false;
                int length = cursor.getInt(3);

                byte[] keyData = cursor.getBlob(1);
                CBLBlobKey key = new CBLBlobKey(keyData);
                String digestString = "sha1-" + Base64.encodeBytes(keyData);
                String dataBase64 = null;
                if(contentOptions.contains(TDContentOptions.TDIncludeAttachments)) {
                    if (contentOptions.contains(TDContentOptions.TDBigAttachmentsFollow) &&
                            length >= CBLDatabase.kBigAttachmentLength) {
                        dataSuppressed = true;
                    }
                    else {
                        byte[] data = attachments.blobForKey(key);

                        if(data != null) {
                            dataBase64 = Base64.encodeBytes(data);  // <-- very expensive
                        }
                        else {
                            Log.w(CBLDatabase.TAG, "Error loading attachment");
                        }

                    }

                }

                Map<String, Object> attachment = new HashMap<String, Object>();



                if(dataBase64 == null || dataSuppressed == true) {
                    attachment.put("stub", true);
                }

                if(dataBase64 != null) {
                    attachment.put("data", dataBase64);
                }

                if (dataSuppressed == true) {
                    attachment.put("follows", true);
                }

                attachment.put("digest", digestString);
                attachment.put("content_type", cursor.getString(2));
                attachment.put("length", length);
                attachment.put("revpos", cursor.getInt(4));

                String filename = cursor.getString(0);
                result.put(filename, attachment);

                cursor.moveToNext();
            }

            return result;

        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error getting attachments for sequence", e);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Modifies a CBLRevision's body by changing all attachments with revpos < minRevPos into stubs.
     *
     * @param rev
     * @param minRevPos
     */
    public void stubOutAttachmentsIn(CBLRevision rev, int minRevPos)
    {
        if (minRevPos <= 1) {
            return;
        }
        Map<String, Object> properties = (Map<String,Object>)rev.getProperties();
        Map<String, Object> attachments = null;
        if(properties != null) {
            attachments = (Map<String,Object>)properties.get("_attachments");
        }
        Map<String, Object> editedProperties = null;
        Map<String, Object> editedAttachments = null;
        for (String name : attachments.keySet()) {
            Map<String,Object> attachment = (Map<String,Object>)attachments.get(name);
            int revPos = (Integer) attachment.get("revpos");
            Object stub = attachment.get("stub");
            if (revPos > 0 && revPos < minRevPos && (stub == null)) {
                // Strip this attachment's body. First make its dictionary mutable:
                if (editedProperties == null) {
                    editedProperties = new HashMap<String,Object>(properties);
                    editedAttachments = new HashMap<String,Object>(attachments);
                    editedProperties.put("_attachments", editedAttachments);
                }
                // ...then remove the 'data' and 'follows' key:
                Map<String,Object> editedAttachment = new HashMap<String,Object>(attachment);
                editedAttachment.remove("data");
                editedAttachment.remove("follows");
                editedAttachment.put("stub", true);
                editedAttachments.put(name,editedAttachment);
                Log.d(CBLDatabase.TAG, "Stubbed out attachment" + rev + " " + name + ": revpos" + revPos + " " + minRevPos);
            }
        }
        if (editedProperties != null)
            rev.setProperties(editedProperties);
    }

    /**
     * Given a newly-added revision, adds the necessary attachment rows to the database and stores inline attachments into the blob store.
     */
    public CBLStatus processAttachmentsForRevision(CBLRevision rev, long parentSequence) {
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
            return new CBLStatus(CBLStatus.OK);
        }

        for (String name : newAttachments.keySet()) {

            CBLStatus status = new CBLStatus();
            Map<String,Object> newAttach = (Map<String,Object>)newAttachments.get(name);
            String newContentBase64 = (String)newAttach.get("data");
            if(newContentBase64 != null) {
                // New item contains data, so insert it. First decode the data:
                byte[] newContents;
                try {
                    newContents = Base64.decode(newContentBase64);
                } catch (IOException e) {
                    Log.e(CBLDatabase.TAG, "IOExeption parsing base64", e);
                    return new CBLStatus(CBLStatus.BAD_REQUEST);
                }
                if(newContents == null) {
                    return new CBLStatus(CBLStatus.BAD_REQUEST);
                }

                // Now determine the revpos, i.e. generation # this was added in. Usually this is
                // implicit, but a rev being pulled in replication will have it set already.
                int generation = rev.getGeneration();
                assert(generation > 0);
                Object revposObj = newAttach.get("revpos");
                int revpos = generation;
                if(revposObj != null && revposObj instanceof Integer) {
                    revpos = ((Integer)revposObj).intValue();
                }

                if(revpos > generation) {
                    return new CBLStatus(CBLStatus.BAD_REQUEST);
                }

                // Finally insert the attachment:
                status = insertAttachmentForSequenceWithNameAndType(new ByteArrayInputStream(newContents), newSequence, name, (String)newAttach.get("content_type"), revpos);
            }
            else {
                // It's just a stub, so copy the previous revision's attachment entry:
                //? Should I enforce that the type and digest (if any) match?
                status = copyAttachmentNamedFromSequenceToSequence(name, parentSequence, newSequence);
            }
            if(!status.isSuccessful()) {
                return status;
            }
        }

        return new CBLStatus(CBLStatus.OK);
    }

    /**
     * Updates or deletes an attachment, creating a new document revision in the process.
     * Used by the PUT / DELETE methods called on attachment URLs.
     */
    public CBLRevision updateAttachment(String filename, InputStream contentStream, String contentType, String docID, String oldRevID, CBLStatus status) {
        status.setCode(CBLStatus.BAD_REQUEST);
        if(filename == null || filename.length() == 0 || (contentStream != null && contentType == null) || (oldRevID != null && docID == null) || (contentStream != null && docID == null)) {
            return null;
        }

        beginTransaction();
        try {
            CBLRevision oldRev = new CBLRevision(docID, oldRevID, false);
            if(oldRevID != null) {
                // Load existing revision if this is a replacement:
                CBLStatus loadStatus = loadRevisionBody(oldRev, EnumSet.noneOf(TDContentOptions.class));
                status.setCode(loadStatus.getCode());
                if(!status.isSuccessful()) {
                    if(status.getCode() == CBLStatus.NOT_FOUND && existsDocumentWithIDAndRev(docID, null)) {
                        status.setCode(CBLStatus.CONFLICT);  // if some other revision exists, it's a conflict
                    }
                    return null;
                }

                Map<String,Object> attachments = (Map<String, Object>) oldRev.getProperties().get("_attachments");
                if(contentStream == null && attachments != null && !attachments.containsKey(filename)) {
                    status.setCode(CBLStatus.NOT_FOUND);
                    return null;
                }
                // Remove the _attachments stubs so putRevision: doesn't copy the rows for me
                // OPT: Would be better if I could tell loadRevisionBody: not to add it
                if(attachments != null) {
                    Map<String,Object> properties = new HashMap<String,Object>(oldRev.getProperties());
                    properties.remove("_attachments");
                    oldRev.setBody(new CBLBody(properties));
                }
            } else {
                // If this creates a new doc, it needs a body:
                oldRev.setBody(new CBLBody(new HashMap<String,Object>()));
            }

            // Create a new revision:
            CBLRevision newRev = putRevision(oldRev, oldRevID, false, status);
            if(newRev == null) {
                return null;
            }

            if(oldRevID != null) {
                // Copy all attachment rows _except_ for the one being updated:
                String[] args = { Long.toString(newRev.getSequence()), Long.toString(oldRev.getSequence()), filename };
                database.execSQL("INSERT INTO attachments "
                        + "(sequence, filename, key, type, length, revpos) "
                        + "SELECT ?, filename, key, type, length, revpos FROM attachments "
                        + "WHERE sequence=? AND filename != ?", args);
            }

            if(contentStream != null) {
                // If not deleting, add a new attachment entry:
                CBLStatus insertStatus = insertAttachmentForSequenceWithNameAndType(contentStream, newRev.getSequence(),
                        filename, contentType, newRev.getGeneration());
                status.setCode(insertStatus.getCode());

                if(!status.isSuccessful()) {
                    return null;
                }
            }

            status.setCode((contentStream != null) ? CBLStatus.CREATED : CBLStatus.OK);
            return newRev;

        } catch(SQLException e) {
            Log.e(TAG, "Error uploading attachment", e);
            status.setCode(CBLStatus.INTERNAL_SERVER_ERROR);
            return null;
        } finally {
            endTransaction(status.isSuccessful());
        }
    }

    public void rememberAttachmentWritersForDigests(Map<String, CBLBlobStoreWriter> blobsByDigest) {
        if (pendingAttachmentsByDigest == null) {
            pendingAttachmentsByDigest = new HashMap<String, CBLBlobStoreWriter>();
            pendingAttachmentsByDigest.putAll(blobsByDigest);
        }
    }


        /**
         * Deletes obsolete attachments from the database and blob store.
         */
    public CBLStatus garbageCollectAttachments() {
        // First delete attachment rows for already-cleared revisions:
        // OPT: Could start after last sequence# we GC'd up to

        try {
            database.execSQL("DELETE FROM attachments WHERE sequence IN " +
                            "(SELECT sequence from revs WHERE json IS null)");
        }
        catch(SQLException e) {
            Log.e(CBLDatabase.TAG, "Error deleting attachments", e);
        }

        // Now collect all remaining attachment IDs and tell the store to delete all but these:
        Cursor cursor = null;
        try {
            cursor = database.rawQuery("SELECT DISTINCT key FROM attachments", null);

            cursor.moveToFirst();
            List<CBLBlobKey> allKeys = new ArrayList<CBLBlobKey>();
            while(!cursor.isAfterLast()) {
                CBLBlobKey key = new CBLBlobKey(cursor.getBlob(0));
                allKeys.add(key);
                cursor.moveToNext();
            }

            int numDeleted = attachments.deleteBlobsExceptWithKeys(allKeys);
            if(numDeleted < 0) {
                return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
            }

            Log.v(CBLDatabase.TAG, "Deleted " + numDeleted + " attachments");

            return new CBLStatus(CBLStatus.OK);
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error finding attachment keys in use", e);
            return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
    }

    /*************************************************************************************************/
    /*** CBLDatabase+Insertion                                                                      ***/
    /*************************************************************************************************/

    /** DOCUMENT & REV IDS: **/

    public static boolean isValidDocumentId(String id) {
        // http://wiki.apache.org/couchdb/HTTP_Document_API#Documents
        if(id == null || id.length() == 0) {
            return false;
        }
        if(id.charAt(0) == '_') {
            return  (id.startsWith("_design/"));
        }
        return true;
        // "_local/*" is not a valid document ID. Local docs have their own API and shouldn't get here.
    }

    public static String generateDocumentId() {
        return CBLMisc.TDCreateUUID();
    }

    public String generateNextRevisionID(String revisionId) {
        // Revision IDs have a generation count, a hyphen, and a UUID.
        int generation = 0;
        if(revisionId != null) {
            generation = CBLRevision.generationFromRevID(revisionId);
            if(generation == 0) {
                return null;
            }
        }
        String digest = CBLMisc.TDCreateUUID();  //TODO: Generate canonical digest of body
        return Integer.toString(generation + 1) + "-" + digest;
    }

    public long insertDocumentID(String docId) {
        long rowId = -1;
        try {
            ContentValues args = new ContentValues();
            args.put("docid", docId);
            rowId = database.insert("docs", null, args);
        } catch (Exception e) {
            Log.e(CBLDatabase.TAG, "Error inserting document id", e);
        }
        return rowId;
    }

    public long getOrInsertDocNumericID(String docId) {
        long docNumericId = getDocNumericID(docId);
        if(docNumericId == 0) {
            docNumericId = insertDocumentID(docId);
        }
        return docNumericId;
    }

    /**
     * Parses the _revisions dict from a document into an array of revision ID strings
     */
    public static List<String> parseCouchDBRevisionHistory(Map<String,Object> docProperties) {
        Map<String,Object> revisions = (Map<String,Object>)docProperties.get("_revisions");
        if(revisions == null) {
            return null;
        }
        List<String> revIDs = (List<String>)revisions.get("ids");
        Integer start = (Integer)revisions.get("start");
        if(start != null) {
            for(int i=0; i < revIDs.size(); i++) {
                String revID = revIDs.get(i);
                revIDs.set(i, Integer.toString(start--) + "-" + revID);
            }
        }
        return revIDs;
    }

    /** INSERTION: **/

    public byte[] encodeDocumentJSON(CBLRevision rev) {

        Map<String,Object> origProps = rev.getProperties();
        if(origProps == null) {
            return null;
        }

        // Don't allow any "_"-prefixed keys. Known ones we'll ignore, unknown ones are an error.
        Map<String,Object> properties = new HashMap<String,Object>(origProps.size());
        for (String key : origProps.keySet()) {
            if(key.startsWith("_")) {
                if(!KNOWN_SPECIAL_KEYS.contains(key)) {
                    Log.e(TAG, "CBLDatabase: Invalid top-level key '" + key + "' in document to be inserted");
                    return null;
                }
            } else {
                properties.put(key, origProps.get(key));
            }
        }

        byte[] json = null;
        try {
            json = CBLServer.getObjectMapper().writeValueAsBytes(properties);
        } catch (Exception e) {
            Log.e(CBLDatabase.TAG, "Error serializing " + rev + " to JSON", e);
        }
        return json;
    }

    public void notifyChange(CBLRevision rev, URL source) {
        Map<String,Object> changeNotification = new HashMap<String, Object>();
        changeNotification.put("rev", rev);
        changeNotification.put("seq", rev.getSequence());
        if(source != null) {
            changeNotification.put("source", source);
        }
        setChanged();
        notifyObservers(changeNotification);
    }

    public long insertRevision(CBLRevision rev, long docNumericID, long parentSequence, boolean current, byte[] data) {
        long rowId = 0;
        try {
            ContentValues args = new ContentValues();
            args.put("doc_id", docNumericID);
            args.put("revid", rev.getRevId());
            if(parentSequence != 0) {
                args.put("parent", parentSequence);
            }
            args.put("current", current);
            args.put("deleted", rev.isDeleted());
            args.put("json", data);
            rowId = database.insert("revs", null, args);
            rev.setSequence(rowId);
        } catch (Exception e) {
            Log.e(CBLDatabase.TAG, "Error inserting revision", e);
        }
        return rowId;
    }

    private CBLRevision putRevision(CBLRevision rev, String prevRevId, CBLStatus resultStatus) {
        return putRevision(rev, prevRevId, false, resultStatus);
    }

    /**
     * Stores a new (or initial) revision of a document.
     *
     * This is what's invoked by a PUT or POST. As with those, the previous revision ID must be supplied when necessary and the call will fail if it doesn't match.
     *
     * @param rev The revision to add. If the docID is null, a new UUID will be assigned. Its revID must be null. It must have a JSON body.
     * @param prevRevId The ID of the revision to replace (same as the "?rev=" parameter to a PUT), or null if this is a new document.
     * @param allowConflict If false, an error status 409 will be returned if the insertion would create a conflict, i.e. if the previous revision already has a child.
     * @param resultStatus On return, an HTTP status code indicating success or failure.
     * @return A new CBLRevision with the docID, revID and sequence filled in (but no body).
     */
    @SuppressWarnings("unchecked")
    public CBLRevision putRevision(CBLRevision rev, String prevRevId, boolean allowConflict, CBLStatus resultStatus) {
        // prevRevId is the rev ID being replaced, or nil if an insert
        String docId = rev.getDocId();
        boolean deleted = rev.isDeleted();
        if((rev == null) || ((prevRevId != null) && (docId == null)) || (deleted && (docId == null))
                || ((docId != null) && !isValidDocumentId(docId))) {
            resultStatus.setCode(CBLStatus.BAD_REQUEST);
            return null;
        }

        resultStatus.setCode(CBLStatus.INTERNAL_SERVER_ERROR);
        beginTransaction();
        Cursor cursor = null;

        //// PART I: In which are performed lookups and validations prior to the insert...

        long docNumericID = (docId != null) ? getDocNumericID(docId) : 0;
        long parentSequence = 0;
        try {
            if(prevRevId != null) {
                // Replacing: make sure given prevRevID is current & find its sequence number:
                if(docNumericID <= 0) {
                    resultStatus.setCode(CBLStatus.NOT_FOUND);
                    return null;
                }

                String[] args = {Long.toString(docNumericID), prevRevId};
                String additionalWhereClause = "";
                if(!allowConflict) {
                    additionalWhereClause = "AND current=1";
                }

                cursor = database.rawQuery("SELECT sequence FROM revs WHERE doc_id=? AND revid=? " + additionalWhereClause + " LIMIT 1", args);

                if(cursor.moveToFirst()) {
                    parentSequence = cursor.getLong(0);
                }

                if(parentSequence == 0) {
                    // Not found: either a 404 or a 409, depending on whether there is any current revision
                    if(!allowConflict && existsDocumentWithIDAndRev(docId, null)) {
                        resultStatus.setCode(CBLStatus.CONFLICT);
                        return null;
                    }
                    else {
                        resultStatus.setCode(CBLStatus.NOT_FOUND);
                        return null;
                    }
                }

                if(validations != null && validations.size() > 0) {
                    // Fetch the previous revision and validate the new one against it:
                    CBLRevision prevRev = new CBLRevision(docId, prevRevId, false);
                    CBLStatus status = validateRevision(rev, prevRev);
                    if(!status.isSuccessful()) {
                        resultStatus.setCode(status.getCode());
                        return null;
                    }
                }

                // Make replaced rev non-current:
                ContentValues updateContent = new ContentValues();
                updateContent.put("current", 0);
                database.update("revs", updateContent, "sequence=" + parentSequence, null);
            }
            else {
                // Inserting first revision.
                if(deleted && (docId != null)) {
                    // Didn't specify a revision to delete: 404 or a 409, depending
                    if(existsDocumentWithIDAndRev(docId, null)) {
                        resultStatus.setCode(CBLStatus.CONFLICT);
                        return null;
                    }
                    else {
                        resultStatus.setCode(CBLStatus.NOT_FOUND);
                        return null;
                    }
                }

                // Validate:
                CBLStatus status = validateRevision(rev, null);
                if(!status.isSuccessful()) {
                    resultStatus.setCode(status.getCode());
                    return null;
                }

                if(docId != null) {
                    // Inserting first revision, with docID given (PUT):
                    if(docNumericID <= 0) {
                        // Doc doesn't exist at all; create it:
                        docNumericID = insertDocumentID(docId);
                        if(docNumericID <= 0) {
                            return null;
                        }
                    } else {
                        // Doc exists; check whether current winning revision is deleted:
                        String[] args = { Long.toString(docNumericID) };
                        cursor = database.rawQuery("SELECT sequence, deleted FROM revs WHERE doc_id=? and current=1 ORDER BY revid DESC LIMIT 1", args);

                        if(cursor.moveToFirst()) {
                            boolean wasAlreadyDeleted = (cursor.getInt(1) > 0);
                            if(wasAlreadyDeleted) {
                                // Make the deleted revision no longer current:
                                ContentValues updateContent = new ContentValues();
                                updateContent.put("current", 0);
                                database.update("revs", updateContent, "sequence=" + cursor.getLong(0), null);
                            }
                            else if (!allowConflict) {
                                // docId already exists, current not deleted, conflict
                                resultStatus.setCode(CBLStatus.CONFLICT);
                                return null;
                            }
                        }
                    }
                }
                else {
                    // Inserting first revision, with no docID given (POST): generate a unique docID:
                    docId = CBLDatabase.generateDocumentId();
                    docNumericID = insertDocumentID(docId);
                    if(docNumericID <= 0) {
                        return null;
                    }
                }
            }

            //// PART II: In which insertion occurs...

            // Bump the revID and update the JSON:
            String newRevId = generateNextRevisionID(prevRevId);
            byte[] data = null;
            if(!rev.isDeleted()) {
                data = encodeDocumentJSON(rev);
                if(data == null) {
                    // bad or missing json
                    resultStatus.setCode(CBLStatus.BAD_REQUEST);
                    return null;
                }
            }

            rev = rev.copyWithDocID(docId, newRevId);

            // Now insert the rev itself:
            long newSequence = insertRevision(rev, docNumericID, parentSequence, true, data);
            if(newSequence == 0) {
                return null;
            }

            // Store any attachments:
            if(attachments != null) {
                CBLStatus status = processAttachmentsForRevision(rev, parentSequence);
                if(!status.isSuccessful()) {
                    resultStatus.setCode(status.getCode());
                    return null;
                }
            }

            // Success!
            if(deleted) {
                resultStatus.setCode(CBLStatus.OK);
            }
            else {
                resultStatus.setCode(CBLStatus.CREATED);
            }

        } catch (SQLException e1) {
            Log.e(CBLDatabase.TAG, "Error putting revision", e1);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
            endTransaction(resultStatus.isSuccessful());
        }

        //// EPILOGUE: A change notification is sent...
        notifyChange(rev, null);
        return rev;
    }

    /**
     * Inserts an already-existing revision replicated from a remote database.
     *
     * It must already have a revision ID. This may create a conflict! The revision's history must be given; ancestor revision IDs that don't already exist locally will create phantom revisions with no content.
     */
    public CBLStatus forceInsert(CBLRevision rev, List<String> revHistory, URL source) {

        String docId = rev.getDocId();
        String revId = rev.getRevId();
        if(!isValidDocumentId(docId) || (revId == null)) {
            return new CBLStatus(CBLStatus.BAD_REQUEST);
        }

        int historyCount = 0;
        if (revHistory != null) {
            historyCount = revHistory.size();
        }
        if(historyCount == 0) {
            revHistory = new ArrayList<String>();
            revHistory.add(revId);
            historyCount = 1;
        } else if(!revHistory.get(0).equals(rev.getRevId())) {
            return new CBLStatus(CBLStatus.BAD_REQUEST);
        }

        boolean success = false;
        beginTransaction();
        try {
            // First look up all locally-known revisions of this document:
            long docNumericID = getOrInsertDocNumericID(docId);
            CBLRevisionList localRevs = getAllRevisionsOfDocumentID(docId, docNumericID, false);
            if(localRevs == null) {
                return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
            }

            // Walk through the remote history in chronological order, matching each revision ID to
            // a local revision. When the list diverges, start creating blank local revisions to fill
            // in the local history:
            long sequence = 0;
            long localParentSequence = 0;
            for(int i = revHistory.size() - 1; i >= 0; --i) {
                revId = revHistory.get(i);
                CBLRevision localRev = localRevs.revWithDocIdAndRevId(docId, revId);
                if(localRev != null) {
                    // This revision is known locally. Remember its sequence as the parent of the next one:
                    sequence = localRev.getSequence();
                    assert(sequence > 0);
                    localParentSequence = sequence;
                }
                else {
                    // This revision isn't known, so add it:
                    CBLRevision newRev;
                    byte[] data = null;
                    boolean current = false;
                    if(i == 0) {
                        // Hey, this is the leaf revision we're inserting:
                       newRev = rev;
                       if(!rev.isDeleted()) {
                           data = encodeDocumentJSON(rev);
                           if(data == null) {
                               return new CBLStatus(CBLStatus.BAD_REQUEST);
                           }
                       }
                       current = true;
                    }
                    else {
                        // It's an intermediate parent, so insert a stub:
                        newRev = new CBLRevision(docId, revId, false);
                    }

                    // Insert it:
                    sequence = insertRevision(newRev, docNumericID, sequence, current, data);

                    if(sequence <= 0) {
                        return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
                    }

                    if(i == 0) {
                        // Write any changed attachments for the new revision:
                        CBLStatus status = processAttachmentsForRevision(rev, localParentSequence);
                        if(!status.isSuccessful()) {
                            return status;
                        }
                    }
                }
            }

            // Mark the latest local rev as no longer current:
            if(localParentSequence > 0 && localParentSequence != sequence) {
                ContentValues args = new ContentValues();
                args.put("current", 0);
                String[] whereArgs = { Long.toString(localParentSequence) };
                try {
                    database.update("revs", args, "sequence=?", whereArgs);
                } catch (SQLException e) {
                    return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
                }
            }

            success = true;
        } catch(SQLException e) {
            endTransaction(success);
            return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        } finally {
            endTransaction(success);
        }

        // Notify and return:
        notifyChange(rev, source);
        return new CBLStatus(CBLStatus.CREATED);
    }

    /** VALIDATION **/

    /**
     * Define or clear a named document validation function.
     */
    public void defineValidation(String name, CBLValidationBlock validationBlock) {
        if(validations == null) {
            validations = new HashMap<String, CBLValidationBlock>();
        }
        if (validationBlock != null) {
            validations.put(name, validationBlock);
        }
        else {
            validations.remove(name);
        }
    }

    public CBLValidationBlock getValidationNamed(String name) {
        CBLValidationBlock result = null;
        if(validations != null) {
            result = validations.get(name);
        }
        return result;
    }

    public CBLStatus validateRevision(CBLRevision newRev, CBLRevision oldRev) {
        CBLStatus result = new CBLStatus(CBLStatus.OK);
        if(validations == null || validations.size() == 0) {
            return result;
        }
        TDValidationContextImpl context = new TDValidationContextImpl(this, oldRev);
        for (String validationName : validations.keySet()) {
            CBLValidationBlock validation = getValidationNamed(validationName);
            if(!validation.validate(newRev, context)) {
                result.setCode(context.getErrorType().getCode());
                break;
            }
        }
        return result;
    }

    /*************************************************************************************************/
    /*** CBLDatabase+Replication                                                                    ***/
    /*************************************************************************************************/

    //TODO implement missing replication methods

    public List<CBLReplicator> getActiveReplicators() {
        return activeReplicators;
    }

    public CBLReplicator getActiveReplicator(URL remote, boolean push) {
        if(activeReplicators != null) {
            for (CBLReplicator replicator : activeReplicators) {
                if(replicator.getRemote().equals(remote) && replicator.isPush() == push  && replicator.isRunning()) {
                    return replicator;
                }
            }
        }
        return null;
    }

    public CBLReplicator getReplicator(URL remote, boolean push, boolean continuous, ScheduledExecutorService workExecutor) {
        CBLReplicator replicator = getReplicator(remote, null, push, continuous, workExecutor);

    	return replicator;
    }
    
    public CBLReplicator getReplicator(String sessionId) {
    	if(activeReplicators != null) {
            for (CBLReplicator replicator : activeReplicators) {
                if(replicator.getSessionID().equals(sessionId)) {
                    return replicator;
                }
            }
        }
        return null;
    }

    public CBLReplicator getReplicator(URL remote, HttpClientFactory httpClientFactory, boolean push, boolean continuous, ScheduledExecutorService workExecutor) {
        CBLReplicator result = getActiveReplicator(remote, push);
        if(result != null) {
            return result;
        }
        result = push ? new CBLPusher(this, remote, continuous, httpClientFactory, workExecutor) : new CBLPuller(this, remote, continuous, httpClientFactory, workExecutor);

        if(activeReplicators == null) {
            activeReplicators = new ArrayList<CBLReplicator>();
        }
        activeReplicators.add(result);
        return result;
    }

    public String lastSequenceWithRemoteURL(URL url, boolean push) {
        Cursor cursor = null;
        String result = null;
        try {
            String[] args = { url.toExternalForm(), Integer.toString(push ? 1 : 0) };
            cursor = database.rawQuery("SELECT last_sequence FROM replicators WHERE remote=? AND push=?", args);
            if(cursor.moveToFirst()) {
                result = cursor.getString(0);
            }
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error getting last sequence", e);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    public boolean setLastSequence(String lastSequence, URL url, boolean push) {
        ContentValues values = new ContentValues();
        values.put("remote", url.toExternalForm());
        values.put("push", push);
        values.put("last_sequence", lastSequence);
        long newId = database.insertWithOnConflict("replicators", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return (newId == -1);
    }

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

    public boolean findMissingRevisions(CBLRevisionList touchRevs) {
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
                CBLRevision rev = touchRevs.revWithDocIdAndRevId(cursor.getString(0), cursor.getString(1));

                if(rev != null) {
                    touchRevs.remove(rev);
                }

                cursor.moveToNext();
            }
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error finding missing revisions", e);
            return false;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
        return true;
    }

    /*************************************************************************************************/
    /*** CBLDatabase+LocalDocs                                                                      ***/
    /*************************************************************************************************/

    public CBLRevision getLocalDocument(String docID, String revID) {
        CBLRevision result = null;
        Cursor cursor = null;
        try {
            String[] args = { docID };
            cursor = database.rawQuery("SELECT revid, json FROM localdocs WHERE docid=?", args);
            if(cursor.moveToFirst()) {
                String gotRevID = cursor.getString(0);
                if(revID != null && (!revID.equals(gotRevID))) {
                    return null;
                }
                byte[] json = cursor.getBlob(1);
                Map<String,Object> properties = null;
                try {
                    properties = CBLServer.getObjectMapper().readValue(json, Map.class);
                    properties.put("_id", docID);
                    properties.put("_rev", gotRevID);
                    result = new CBLRevision(docID, gotRevID, false);
                    result.setProperties(properties);
                } catch (Exception e) {
                    Log.w(TAG, "Error parsing local doc JSON", e);
                    return null;
                }

            }
            return result;
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error getting local document", e);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
    }

    public CBLRevision putLocalRevision(CBLRevision revision, String prevRevID, CBLStatus status) {
        String docID = revision.getDocId();
        if(!docID.startsWith("_local/")) {
            status.setCode(CBLStatus.BAD_REQUEST);
            return null;
        }

        if(!revision.isDeleted()) {
            // PUT:
            byte[] json = encodeDocumentJSON(revision);
            String newRevID;
            if(prevRevID != null) {
                int generation = CBLRevision.generationFromRevID(prevRevID);
                if(generation == 0) {
                    status.setCode(CBLStatus.BAD_REQUEST);
                    return null;
                }
                newRevID = Integer.toString(++generation) + "-local";
                ContentValues values = new ContentValues();
                values.put("revid", newRevID);
                values.put("json", json);
                String[] whereArgs = { docID, prevRevID };
                try {
                    int rowsUpdated = database.update("localdocs", values, "docid=? AND revid=?", whereArgs);
                    if(rowsUpdated == 0) {
                        status.setCode(CBLStatus.CONFLICT);
                        return null;
                    }
                } catch (SQLException e) {
                    status.setCode(CBLStatus.INTERNAL_SERVER_ERROR);
                    return null;
                }
            } else {
                newRevID = "1-local";
                ContentValues values = new ContentValues();
                values.put("docid", docID);
                values.put("revid", newRevID);
                values.put("json", json);
                try {
                    database.insertWithOnConflict("localdocs", null, values, SQLiteDatabase.CONFLICT_IGNORE);
                } catch (SQLException e) {
                    status.setCode(CBLStatus.INTERNAL_SERVER_ERROR);
                    return null;
                }
            }
            status.setCode(CBLStatus.CREATED);
            return revision.copyWithDocID(docID, newRevID);
        }
        else {
            // DELETE:
            CBLStatus deleteStatus = deleteLocalDocument(docID, prevRevID);
            status.setCode(deleteStatus.getCode());
            return (status.isSuccessful()) ? revision : null;
        }
    }

    public CBLStatus deleteLocalDocument(String docID, String revID) {
        if(docID == null) {
            return new CBLStatus(CBLStatus.BAD_REQUEST);
        }
        if(revID == null) {
            // Didn't specify a revision to delete: 404 or a 409, depending
            return (getLocalDocument(docID, null) != null) ? new CBLStatus(CBLStatus.CONFLICT) : new CBLStatus(CBLStatus.NOT_FOUND);
        }
        String[] whereArgs = { docID, revID };
        try {
            int rowsDeleted = database.delete("localdocs", "docid=? AND revid=?", whereArgs);
            if(rowsDeleted == 0) {
                return (getLocalDocument(docID, null) != null) ? new CBLStatus(CBLStatus.CONFLICT) : new CBLStatus(CBLStatus.NOT_FOUND);
            }
            return new CBLStatus(CBLStatus.OK);
        } catch (SQLException e) {
            return new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        }
    }

}

class TDValidationContextImpl implements CBLValidationContext {

    private CBLDatabase database;
    private CBLRevision currentRevision;
    private CBLStatus errorType;
    private String errorMessage;

    public TDValidationContextImpl(CBLDatabase database, CBLRevision currentRevision) {
        this.database = database;
        this.currentRevision = currentRevision;
        this.errorType = new CBLStatus(CBLStatus.FORBIDDEN);
        this.errorMessage = "invalid document";
    }

    @Override
    public CBLRevision getCurrentRevision() {
        if(currentRevision != null) {
            database.loadRevisionBody(currentRevision, EnumSet.noneOf(TDContentOptions.class));
        }
        return currentRevision;
    }

    @Override
    public CBLStatus getErrorType() {
        return errorType;
    }

    @Override
    public void setErrorType(CBLStatus status) {
        this.errorType = status;
    }

    @Override
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public void setErrorMessage(String message) {
        this.errorMessage = message;
    }

}
