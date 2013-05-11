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

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * Represents a view available in a database.
 */
public class CBLView {

    public static final int REDUCE_BATCH_SIZE = 100;

    public enum TDViewCollation {
        TDViewCollationUnicode, TDViewCollationRaw, TDViewCollationASCII
    }

    private CBLDatabase db;
    private String name;
    private int viewId;
    private CBLViewMapBlock mapBlock;
    private CBLViewReduceBlock reduceBlock;
    private TDViewCollation collation;
    private static CBLViewCompiler compiler;

    public CBLView(CBLDatabase db, String name) {
        this.db = db;
        this.name = name;
        this.viewId = -1; // means 'unknown'
        this.collation = TDViewCollation.TDViewCollationUnicode;
    }

    public CBLDatabase getDb() {
        return db;
    };

    public String getName() {
        return name;
    }

    public CBLViewMapBlock getMapBlock() {
        return mapBlock;
    }

    public CBLViewReduceBlock getReduceBlock() {
        return reduceBlock;
    }

    public TDViewCollation getCollation() {
        return collation;
    }

    public void setCollation(TDViewCollation collation) {
        this.collation = collation;
    }

    /**
     * Is the view's index currently out of date?
     */
    public boolean isStale() {
        return (getLastSequenceIndexed() < db.getLastSequence());
    }

    public int getViewId() {
        if (viewId < 0) {
            String sql = "SELECT view_id FROM views WHERE name=?";
            String[] args = { name };
            Cursor cursor = null;
            try {
                cursor = db.getDatabase().rawQuery(sql, args);
                if (cursor.moveToFirst()) {
                    viewId = cursor.getInt(0);
                } else {
                    viewId = 0;
                }
            } catch (SQLException e) {
                Log.e(CBLDatabase.TAG, "Error getting view id", e);
                viewId = 0;
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
        return viewId;
    }

    public long getLastSequenceIndexed() {
        String sql = "SELECT lastSequence FROM views WHERE name=?";
        String[] args = { name };
        Cursor cursor = null;
        long result = -1;
        try {
            cursor = db.getDatabase().rawQuery(sql, args);
            if (cursor.moveToFirst()) {
                result = cursor.getLong(0);
            }
        } catch (Exception e) {
            Log.e(CBLDatabase.TAG, "Error getting last sequence indexed");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    public boolean setMapReduceBlocks(CBLViewMapBlock mapBlock,
            CBLViewReduceBlock reduceBlock, String version) {
        assert (mapBlock != null);
        assert (version != null);

        this.mapBlock = mapBlock;
        this.reduceBlock = reduceBlock;

        if(!db.open()) {
            return false;
        }

        // Update the version column in the db. This is a little weird looking
        // because we want to
        // avoid modifying the db if the version didn't change, and because the
        // row might not exist yet.
        SQLiteDatabase database = db.getDatabase();

        // Older Android doesnt have reliable insert or ignore, will to 2 step
        // FIXME review need for change to execSQL, manual call to changes()

        String sql = "SELECT name, version FROM views WHERE name=?";
        String[] args = { name };
        Cursor cursor = null;

        try {
            cursor = db.getDatabase().rawQuery(sql, args);
            if (!cursor.moveToFirst()) {
                // no such record, so insert
                ContentValues insertValues = new ContentValues();
                insertValues.put("name", name);
                insertValues.put("version", version);
                database.insert("views", null, insertValues);
                return true;
            }

            ContentValues updateValues = new ContentValues();
            updateValues.put("version", version);
            updateValues.put("lastSequence", 0);

            String[] whereArgs = { name, version };
            int rowsAffected = database.update("views", updateValues,
                    "name=? AND version!=?", whereArgs);

            return (rowsAffected > 0);
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error setting map block", e);
            return false;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

    }

    public void removeIndex() {
        if (getViewId() < 0) {
            return;
        }

        boolean success = false;
        try {
            db.beginTransaction();

            String[] whereArgs = { Integer.toString(getViewId()) };
            db.getDatabase().delete("maps", "view_id=?", whereArgs);

            ContentValues updateValues = new ContentValues();
            updateValues.put("lastSequence", 0);
            db.getDatabase().update("views", updateValues, "view_id=?",
                    whereArgs);

            success = true;
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error removing index", e);
        } finally {
            db.endTransaction(success);
        }
    }

    public void deleteView() {
        db.deleteViewNamed(name);
        viewId = 0;
    }

    public void databaseClosing() {
        db = null;
        viewId = 0;
    }

    /*** Indexing ***/

    public String toJSONString(Object object) {
        if (object == null) {
            return null;
        }
        String result = null;
        try {
            result = CBLServer.getObjectMapper().writeValueAsString(object);
        } catch (Exception e) {
            // ignore
        }
        return result;
    }

    public Object fromJSON(byte[] json) {
        if (json == null) {
            return null;
        }
        Object result = null;
        try {
            result = CBLServer.getObjectMapper().readValue(json, Object.class);
        } catch (Exception e) {
            // ignore
        }
        return result;
    }

    /**
     * Updates the view's index (incrementally) if necessary.
     * @return 200 if updated, 304 if already up-to-date, else an error code
     */
    @SuppressWarnings("unchecked")
    public CBLStatus updateIndex() {
        Log.v(CBLDatabase.TAG, "Re-indexing view " + name + " ...");
        assert (mapBlock != null);

        if (getViewId() < 0) {
            return new CBLStatus(CBLStatus.NOT_FOUND);
        }

        db.beginTransaction();
        CBLStatus result = new CBLStatus(CBLStatus.INTERNAL_SERVER_ERROR);
        Cursor cursor = null;

        try {

            long lastSequence = getLastSequenceIndexed();
            long dbMaxSequence = db.getLastSequence();
            if(lastSequence == dbMaxSequence) {
                result.setCode(CBLStatus.NOT_MODIFIED);
                return result;
            }

            // First remove obsolete emitted results from the 'maps' table:
            long sequence = lastSequence;
            if (lastSequence < 0) {
                return result;
            }

            if (lastSequence == 0) {
                // If the lastSequence has been reset to 0, make sure to remove
                // any leftover rows:
                String[] whereArgs = { Integer.toString(getViewId()) };
                db.getDatabase().delete("maps", "view_id=?", whereArgs);
            } else {
                // Delete all obsolete map results (ones from since-replaced
                // revisions):
                String[] args = { Integer.toString(getViewId()),
                        Long.toString(lastSequence),
                        Long.toString(lastSequence) };
                db.getDatabase().execSQL(
                        "DELETE FROM maps WHERE view_id=? AND sequence IN ("
                                + "SELECT parent FROM revs WHERE sequence>? "
                                + "AND parent>0 AND parent<=?)", args);
            }

            int deleted = 0;
            cursor = db.getDatabase().rawQuery("SELECT changes()", null);
            cursor.moveToFirst();
            deleted = cursor.getInt(0);
            cursor.close();

            // This is the emit() block, which gets called from within the
            // user-defined map() block
            // that's called down below.
            AbstractTouchMapEmitBlock emitBlock = new AbstractTouchMapEmitBlock() {

                @Override
                public void emit(Object key, Object value) {

                    try {
                        String keyJson = CBLServer.getObjectMapper().writeValueAsString(key);
                        String valueJson = CBLServer.getObjectMapper().writeValueAsString(value);
                        Log.v(CBLDatabase.TAG, "    emit(" + keyJson + ", "
                                + valueJson + ")");

                        ContentValues insertValues = new ContentValues();
                        insertValues.put("view_id", getViewId());
                        insertValues.put("sequence", sequence);
                        insertValues.put("key", keyJson);
                        insertValues.put("value", valueJson);
                        db.getDatabase().insert("maps", null, insertValues);
                    } catch (Exception e) {
                        Log.e(CBLDatabase.TAG, "Error emitting", e);
                        // find a better way to propogate this back
                    }
                }
            };

            // Now scan every revision added since the last time the view was
            // indexed:
            String[] selectArgs = { Long.toString(lastSequence) };

            cursor = db.getDatabase().rawQuery(
                    "SELECT revs.doc_id, sequence, docid, revid, json FROM revs, docs "
                            + "WHERE sequence>? AND current!=0 AND deleted=0 "
                            + "AND revs.doc_id = docs.doc_id "
                            + "ORDER BY revs.doc_id, revid DESC", selectArgs);

            cursor.moveToFirst();

            long lastDocID = 0;
            while (!cursor.isAfterLast()) {
                long docID = cursor.getLong(0);
                if (docID != lastDocID) {
                    // Only look at the first-iterated revision of any document,
                    // because this is the
                    // one with the highest revid, hence the "winning" revision
                    // of a conflict.
                    lastDocID = docID;

                    // Reconstitute the document as a dictionary:
                    sequence = cursor.getLong(1);
                    String docId = cursor.getString(2);
                    if(docId.startsWith("_design/")) {  // design docs don't get indexed!
                        cursor.moveToNext();
                        continue;
                    }
                    String revId = cursor.getString(3);
                    byte[] json = cursor.getBlob(4);
                    Map<String, Object> properties = db
                            .documentPropertiesFromJSON(json, docId, revId,
                                    sequence, EnumSet.noneOf(CBLDatabase.TDContentOptions.class));

                    if (properties != null) {
                        // Call the user-defined map() to emit new key/value
                        // pairs from this revision:
                        Log.v(CBLDatabase.TAG,
                                "  call map for sequence="
                                        + Long.toString(sequence));
                        emitBlock.setSequence(sequence);
                        mapBlock.map(properties, emitBlock);
                    }

                }

                cursor.moveToNext();
            }

            // Finally, record the last revision sequence number that was
            // indexed:
            ContentValues updateValues = new ContentValues();
            updateValues.put("lastSequence", dbMaxSequence);
            String[] whereArgs = { Integer.toString(getViewId()) };
            db.getDatabase().update("views", updateValues, "view_id=?",
                    whereArgs);

            // FIXME actually count number added :)
            Log.v(CBLDatabase.TAG, "...Finished re-indexing view " + name
                    + " up to sequence " + Long.toString(dbMaxSequence)
                    + " (deleted " + deleted + " added " + "?" + ")");
            result.setCode(CBLStatus.OK);

        } catch (SQLException e) {
            return result;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (!result.isSuccessful()) {
                Log.w(CBLDatabase.TAG, "Failed to rebuild view " + name + ": "
                        + result.getCode());
            }
            if(db != null) {
                db.endTransaction(result.isSuccessful());
            }
        }

        return result;
    }

    public Cursor resultSetWithOptions(CBLQueryOptions options, CBLStatus status) {
        if (options == null) {
            options = new CBLQueryOptions();
        }

        // OPT: It would be faster to use separate tables for raw-or ascii-collated views so that
        // they could be indexed with the right collation, instead of having to specify it here.
        String collationStr = "";
        if(collation == TDViewCollation.TDViewCollationASCII) {
            collationStr += " COLLATE JSON_ASCII";
        }
        else if(collation == TDViewCollation.TDViewCollationRaw) {
            collationStr += " COLLATE JSON_RAW";
        }

        String sql = "SELECT key, value, docid";
        if (options.isIncludeDocs()) {
            sql = sql + ", revid, json, revs.sequence";
        }
        sql = sql + " FROM maps, revs, docs WHERE maps.view_id=?";

        List<String> argsList = new ArrayList<String>();
        argsList.add(Integer.toString(getViewId()));

        if(options.getKeys() != null) {
            sql += " AND key in (";
            String item = "?";
            for (Object key : options.getKeys()) {
                sql += item;
                item = ", ?";
                argsList.add(toJSONString(key));
            }
            sql += ")";
        }

        Object minKey = options.getStartKey();
        Object maxKey = options.getEndKey();
        boolean inclusiveMin = true;
        boolean inclusiveMax = options.isInclusiveEnd();
        if (options.isDescending()) {
            minKey = maxKey;
            maxKey = options.getStartKey();
            inclusiveMin = inclusiveMax;
            inclusiveMax = true;
        }

        if (minKey != null) {
            assert (minKey instanceof String);
            if (inclusiveMin) {
                sql += " AND key >= ?";
            } else {
                sql += " AND key > ?";
            }
            sql += collationStr;
            argsList.add(toJSONString(minKey));
        }

        if (maxKey != null) {
            assert (maxKey instanceof String);
            if (inclusiveMax) {
                sql += " AND key <= ?";
            } else {
                sql += " AND key < ?";
            }
            sql += collationStr;
            argsList.add(toJSONString(maxKey));
        }

        sql = sql
                + " AND revs.sequence = maps.sequence AND docs.doc_id = revs.doc_id ORDER BY key";
        sql += collationStr;
        if (options.isDescending()) {
            sql = sql + " DESC";
        }
        sql = sql + " LIMIT ? OFFSET ?";
        argsList.add(Integer.toString(options.getLimit()));
        argsList.add(Integer.toString(options.getSkip()));

        Log.v(CBLDatabase.TAG, "Query " + name + ": " + sql);

        Cursor cursor = db.getDatabase().rawQuery(sql,
                argsList.toArray(new String[argsList.size()]));
        return cursor;
    }

    // Are key1 and key2 grouped together at this groupLevel?
    public static boolean groupTogether(Object key1, Object key2, int groupLevel) {
        if(groupLevel == 0 || !(key1 instanceof List) || !(key2 instanceof List)) {
            return key1.equals(key2);
        }
        @SuppressWarnings("unchecked")
        List<Object> key1List = (List<Object>)key1;
        @SuppressWarnings("unchecked")
        List<Object> key2List = (List<Object>)key2;
        int end = Math.min(groupLevel, Math.min(key1List.size(), key2List.size()));
        for(int i = 0; i < end; ++i) {
            if(!key1List.get(i).equals(key2List.get(i))) {
                return false;
            }
        }
        return true;
    }

    // Returns the prefix of the key to use in the result row, at this groupLevel
    @SuppressWarnings("unchecked")
    public static Object groupKey(Object key, int groupLevel) {
        if(groupLevel > 0 && (key instanceof List) && (((List<Object>)key).size() > groupLevel)) {
            return ((List<Object>)key).subList(0, groupLevel);
        }
        else {
            return key;
        }
    }

    /*** Querying ***/
    public List<Map<String, Object>> dump() {
        if (getViewId() < 0) {
            return null;
        }

        String[] selectArgs = { Integer.toString(getViewId()) };
        Cursor cursor = null;
        List<Map<String, Object>> result = null;

        try {
            cursor = db
                    .getDatabase()
                    .rawQuery(
                            "SELECT sequence, key, value FROM maps WHERE view_id=? ORDER BY key",
                            selectArgs);

            cursor.moveToFirst();
            result = new ArrayList<Map<String, Object>>();
            while (!cursor.isAfterLast()) {
                Map<String, Object> row = new HashMap<String, Object>();
                row.put("seq", cursor.getInt(0));
                row.put("key", cursor.getString(1));
                row.put("value", cursor.getString(2));
                result.add(row);
                cursor.moveToNext();
            }
        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error dumping view", e);
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    /**
     * Queries the view. Does NOT first update the index.
     *
     * @param options The options to use.
     * @param status An array of result rows -- each is a dictionary with "key" and "value" keys, and possibly "id" and "doc".
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> queryWithOptions(CBLQueryOptions options, CBLStatus status) {
        if (options == null) {
            options = new CBLQueryOptions();
        }

        Cursor cursor = null;
        List<Map<String, Object>> rows = new ArrayList<Map<String,Object>>();
        try {
            cursor = resultSetWithOptions(options, status);
            int groupLevel = options.getGroupLevel();
            boolean group = options.isGroup() || (groupLevel > 0);
            boolean reduce = options.isReduce() || group;

            if(reduce && (reduceBlock == null) && !group) {
                Log.w(CBLDatabase.TAG, "Cannot use reduce option in view " + name + " which has no reduce block defined");
                status.setCode(CBLStatus.BAD_REQUEST);
                return null;
            }

            List<Object> keysToReduce = null;
            List<Object> valuesToReduce = null;
            Object lastKey = null;
            if(reduce) {
                keysToReduce = new ArrayList<Object>(REDUCE_BATCH_SIZE);
                valuesToReduce = new ArrayList<Object>(REDUCE_BATCH_SIZE);
            }

            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                Object key = fromJSON(cursor.getBlob(0));
                Object value = fromJSON(cursor.getBlob(1));
                assert(key != null);
                if(reduce) {
                    // Reduced or grouped query:
                    if(group && !groupTogether(key, lastKey, groupLevel) && (lastKey != null)) {
                        // This pair starts a new group, so reduce & record the last one:
                        Object reduced = (reduceBlock != null) ? reduceBlock.reduce(keysToReduce, valuesToReduce, false) : null;
                        Map<String,Object> row = new HashMap<String,Object>();
                        row.put("key", groupKey(lastKey, groupLevel));
                        if(reduced != null) {
                            row.put("value", reduced);
                        }
                        rows.add(row);
                        keysToReduce.clear();
                        valuesToReduce.clear();
                    }
                    keysToReduce.add(key);
                    valuesToReduce.add(value);
                    lastKey = key;
                } else {
                	// Regular query:
                	Map<String,Object> row = new HashMap<String,Object>();
                    String docId = cursor.getString(2);
                    Map<String,Object> docContents = null;
                    if(options.isIncludeDocs()) {
                        docContents = db.documentPropertiesFromJSON(cursor.getBlob(4), docId, cursor.getString(3), cursor.getLong(5), options.getContentOptions());


                    }


                    if(docContents != null) {
                        row.put("doc", docContents);
                    }
                    if(value != null) {
                        row.put("value", value);
                    }
                    row.put("id", docId);
                    row.put("key", key);

                    rows.add(row);
                }


                cursor.moveToNext();
            }

            if(reduce) {
                if(keysToReduce.size() > 0) {
                    // Finish the last group (or the entire list, if no grouping):
                    Object key = group ? groupKey(lastKey, groupLevel) : null;
                    Object reduced = (reduceBlock != null) ? reduceBlock.reduce(keysToReduce, valuesToReduce, false) : null;
                    Map<String,Object> row = new HashMap<String,Object>();
                    row.put("key", key);
                    if(reduced != null) {
                        row.put("value", reduced);
                    }
                    rows.add(row);
                }
                keysToReduce.clear();
                valuesToReduce.clear();
            }

            status.setCode(CBLStatus.OK);

        } catch (SQLException e) {
            Log.e(CBLDatabase.TAG, "Error querying view", e);
            return null;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return rows;
    }

    /**
     * Utility function to use in reduce blocks. Totals an array of Numbers.
     */
    public static double totalValues(List<Object>values) {
        double total = 0;
        for (Object object : values) {
            if(object instanceof Number) {
                Number number = (Number)object;
                total += number.doubleValue();
            } else {
                Log.w(CBLDatabase.TAG, "Warning non-numeric value found in totalValues: " + object);
            }
        }
        return total;
    }

    public static CBLViewCompiler getCompiler() {
        return compiler;
    }

    public static void setCompiler(CBLViewCompiler compiler) {
        CBLView.compiler = compiler;
    }

}

abstract class AbstractTouchMapEmitBlock implements CBLViewMapEmitBlock {

    protected long sequence = 0;

    void setSequence(long sequence) {
        this.sequence = sequence;
    }

}
