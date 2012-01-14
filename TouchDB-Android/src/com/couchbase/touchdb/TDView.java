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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

public class TDView {

    private TDDatabase db;
    private String name;
    private int viewId;
    private TDViewMapBlock mapBlock;

    public TDView(TDDatabase db, String name) {
        this.db = db;
        this.name = name;
        this.viewId = -1;  // means 'unknown'
    }

    public TDDatabase getDb() {
        return db;
    };

    public String getName() {
        return name;
    }

    public TDViewMapBlock getMapBlock() {
        return mapBlock;
    }

    public int getViewId() {
        if(viewId < 0) {
            String sql = "SELECT view_id FROM views WHERE name=?";
            String[] args = {name};
            Cursor cursor = null;
            try {
                cursor = db.getDatabase().rawQuery(sql, args);
                if(cursor.moveToFirst()) {
                    viewId = cursor.getInt(0);
                }
                else {
                    viewId = 0;
                }
            } catch (SQLException e) {
                Log.e(TDDatabase.TAG, "Error getting view id", e);
                viewId = 0;
            } finally {
                if(cursor != null) {
                    cursor.close();
                }
            }
        }
        return viewId;
    }

    public long getLastSequenceIndexed() {
        String sql = "SELECT lastSequence FROM views WHERE name=?";
        String[] args = {name};
        Cursor cursor = null;
        long result = -1;
        try {
            cursor = db.getDatabase().rawQuery(sql, args);
            if(cursor.moveToFirst()) {
                result = cursor.getLong(0);
            }
        } catch (Exception e) {
            Log.e(TDDatabase.TAG, "Error getting last sequence indexed");
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }
        return result;
    }

    public boolean setMapBlock(TDViewMapBlock mapBlock, String version) {
        assert(mapBlock != null);
        assert(version != null);

        this.mapBlock = mapBlock;

        // Update the version column in the db. This is a little weird looking because we want to
        // avoid modifying the db if the version didn't change, and because the row might not exist yet.
        SQLiteDatabase database = db.getDatabase();

        // Older Android doesnt have reliable insert or ignore, will to 2 step
        // FIXME review need for change to execSQL, manual call to changes()

        String sql = "SELECT name, version FROM views WHERE name=?";
        String[] args = {name};
        Cursor cursor = null;

        try {
            cursor = db.getDatabase().rawQuery(sql, args);
            if(!cursor.moveToFirst()) {
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
            int rowsAffected = database.update("views", updateValues, "name=? AND version!=?", whereArgs);

            return (rowsAffected > 0);
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error setting map block", e);
            return false;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

    }

    public void removeIndex() {
        if(getViewId() < 0) {
            return;
        }

        boolean success = false;
        try {
            db.beginTransaction();

            String[] whereArgs = { Integer.toString(getViewId()) };
            db.getDatabase().delete("maps", "view_id=?", whereArgs);

            ContentValues updateValues = new ContentValues();
            updateValues.put("lastSequence", 0);
            db.getDatabase().update("views", updateValues, "view_id=?", whereArgs);

            success = true;
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error removing index", e);
        } finally {
            db.endTransaction(success);
        }
    }

    public void deleteView() {
        db.deleteViewNamed(name);
        viewId = 0;
    }

    /*** Indexing ***/

    public static String toJSONString(Object object) {
        if(object == null) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        String result = null;
        try {
            result = mapper.writeValueAsString(object);
        } catch(Exception e) {
            //ignore
        }
        return result;
    }

    public static Object fromJSON(byte[] json) {
        if(json == null) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        Object result = null;
        try {
            result = mapper.readValue(json, Object.class);
        } catch (Exception e) {
            //ignore
        }
        return result;
    }

    //FIXME review this method may need better exception handling within transaction
    @SuppressWarnings("unchecked")
    public TDStatus updateIndex() {
        Log.v(TDDatabase.TAG, "Re-indexing view " + name + " ...");
        assert(mapBlock != null);

        if(getViewId() < 0) {
            return new TDStatus(TDStatus.NOT_FOUND);
        }

        db.beginTransaction();
        TDStatus result = new TDStatus(TDStatus.INTERNAL_SERVER_ERROR);
        Cursor cursor = null;

        try {

            long lastSequence = getLastSequenceIndexed();
            long sequence = lastSequence;
            if(lastSequence < 0) {
                return result;
            }

            if(lastSequence == 0) {
                // If the lastSequence has been reset to 0, make sure to remove any leftover rows:
                String[] whereArgs = { Integer.toString(getViewId()) };
                db.getDatabase().delete("maps", "view_id=?", whereArgs);
            }
            else {
                // Delete all obsolete map results (ones from since-replaced revisions):
                String[] args = { Integer.toString(getViewId()), Long.toString(lastSequence), Long.toString(lastSequence) };
                db.getDatabase().execSQL("DELETE FROM maps WHERE view_id=? AND sequence IN ("
                                        + "SELECT parent FROM revs WHERE sequence>? "
                                        + "AND parent>0 AND parent<=?)", args);
            }

            int deleted = 0;
            cursor = db.getDatabase().rawQuery("SELECT changes()", null);
            cursor.moveToFirst();
            deleted = cursor.getInt(0);
            cursor.close();

            // This is the emit() block, which gets called from within the user-defined map() block
            // that's called down below.
            AbstractTouchMapEmitBlock emitBlock = new AbstractTouchMapEmitBlock() {

                @Override
                public void emit(Object key, Object value) {
                    if(key == null) {
                        return;
                    }
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        String keyJson = mapper.writeValueAsString(key);
                        String valueJson = mapper.writeValueAsString(value);
                        Log.v(TDDatabase.TAG, "    emit(" + keyJson + ", " + valueJson + ")");

                        ContentValues insertValues = new ContentValues();
                        insertValues.put("view_id", getViewId());
                        insertValues.put("sequence", sequence);
                        insertValues.put("key", keyJson);
                        insertValues.put("value", valueJson);
                        db.getDatabase().insert("maps", null, insertValues);
                    } catch (Exception e) {
                        Log.e(TDDatabase.TAG, "Error emitting", e);
                        //find a better way to propogate this back
                    }
                }
            };

            // Now scan every revision added since the last time the view was indexed:
            String[] selectArgs = { Long.toString(lastSequence) };

            cursor = db.getDatabase().rawQuery("SELECT revs.doc_id, sequence, docid, revid, json FROM revs, docs "
                    + "WHERE sequence>? AND current!=0 AND deleted=0 "
                    + "AND revs.doc_id = docs.doc_id "
                    + "ORDER BY revs.doc_id, revid DESC", selectArgs);

            cursor.moveToFirst();

            long lastDocID = 0;
            while(!cursor.isAfterLast()) {
                long docID = cursor.getLong(0);
                if(docID != lastDocID) {
                    // Only look at the first-iterated revision of any document, because this is the
                    // one with the highest revid, hence the "winning" revision of a conflict.
                    lastDocID = docID;

                    // Reconstitute the document as a dictionary:
                    sequence = cursor.getLong(1);
                    String docId = cursor.getString(2);
                    String revId = cursor.getString(3);
                    byte[] json = cursor.getBlob(4);
                    Map<String, Object> properties = db.documentPropertiesFromJSON(json, docId, revId, sequence);

                    if(properties != null) {
                        // Call the user-defined map() to emit new key/value pairs from this revision:
                        Log.v(TDDatabase.TAG, "  call map for sequence=" + Long.toString(sequence));
                        emitBlock.setSequence(sequence);
                        mapBlock.map(properties, emitBlock);
                    }

                }

                cursor.moveToNext();
            }

            // Finally, record the last revision sequence number that was indexed:
            long dbMaxSequence = db.getLastSequence();
            ContentValues updateValues = new ContentValues();
            updateValues.put("lastSequence", dbMaxSequence);
            String[] whereArgs = { Integer.toString(getViewId()) };
            db.getDatabase().update("views", updateValues, "view_id=?", whereArgs);


            //FIXME actually count number added :)
            Log.v(TDDatabase.TAG, "...Finished re-indexing view " + name + " up to sequence " + Long.toString(dbMaxSequence) + " (deleted " + deleted + " added " + "?" + ")");
            result.setCode(TDStatus.OK);


        } catch(SQLException e) {
            return result;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
            if(!result.isSuccessful()) {
                Log.w(TDDatabase.TAG, "Failed to rebuild view " + name + ": " + result.getCode());
            }
            db.endTransaction(result.isSuccessful());
        }

        return result;
    }

    /*** Querying ***/
    public List<Map<String,Object>> dump() {
        if(getViewId() < 0) {
            return null;
        }

        String[] selectArgs = { Integer.toString(getViewId()) };
        Cursor cursor = null;
        List<Map<String, Object>> result = null;

        try {
            cursor = db.getDatabase().rawQuery("SELECT sequence, key, value FROM maps WHERE view_id=? ORDER BY key", selectArgs);

            cursor.moveToFirst();
            result = new ArrayList<Map<String,Object>>();
            while(!cursor.isAfterLast()) {
                Map<String,Object> row = new HashMap<String,Object>();
                row.put("seq", cursor.getInt(0));
                row.put("key", cursor.getString(1));
                row.put("value", cursor.getString(2));
                result.add(row);
                cursor.moveToNext();
            }
        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error dumping view", e);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> queryWithOptions(TDQueryOptions options) {
        if(options == null) {
            options = new TDQueryOptions();
        }

        TDStatus updateStatus = updateIndex();
        if(!updateStatus.isSuccessful()) {
            return null;
        }

        long update_seq = 0;
        if(options.isUpdateSeq()) {
            update_seq = getLastSequenceIndexed();
        }

        String sql = "SELECT key, value, docid";
        if(options.isIncludeDocs()) {
            sql = sql + ", revid, json, revs.sequence";
        }
        sql = sql + " FROM maps, revs, docs WHERE maps.view_id=?";

        List<String> argsList = new ArrayList<String>();
        argsList.add(Integer.toString(getViewId()));

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
                sql += " AND key >= ?";
            } else {
                sql += " AND key > ?";
            }
            argsList.add(toJSONString(minKey));
        }

        if(maxKey != null) {
            assert(maxKey instanceof String);
            if(inclusiveMax) {
                sql += " AND key <= ?";
            }
            else {
                sql += " AND key < ?";
            }
            argsList.add(toJSONString(maxKey));
        }

        sql = sql + " AND revs.sequence = maps.sequence AND docs.doc_id = revs.doc_id ORDER BY key";
        if(options.isDescending()) {
            sql = sql + " DESC";
        }
        sql = sql + " LIMIT ? OFFSET ?";
        argsList.add(Integer.toString(options.getLimit()));
        argsList.add(Integer.toString(options.getSkip()));

        Cursor cursor = null;

        List<Map<String, Object>> rows;
        try {
            cursor = db.getDatabase().rawQuery(sql, argsList.toArray(new String[argsList.size()]));

            cursor.moveToFirst();
            rows = new ArrayList<Map<String,Object>>();
            while(!cursor.isAfterLast()) {
                Map<String,Object> row = new HashMap<String,Object>();
                Object key = fromJSON(cursor.getBlob(0));
                Object value = fromJSON(cursor.getBlob(1));
                String docId = cursor.getString(2);
                Map<String,Object> docContents = null;
                if(options.isIncludeDocs()) {
                    String revId = cursor.getString(3);
                    byte[] docBytes = cursor.getBlob(4);
                    long sequence = cursor.getLong(5);
                    docContents = db.documentPropertiesFromJSON(docBytes, docId, revId, sequence);
                }
                row.put("id", docId);
                row.put("key", key);
                if(value != null) {
                    row.put("value", value);
                }
                if(docContents != null) {
                    row.put("doc", docContents);
                }
                rows.add(row);
                cursor.moveToNext();
            }

        } catch (SQLException e) {
            Log.e(TDDatabase.TAG, "Error querying view", e);
            return null;
        } finally {
            if(cursor != null) {
                cursor.close();
            }
        }

        int totalRows = rows.size();  //??? Is this true, or does it ignore limit/offset?
        Map<String,Object> result = new HashMap<String,Object>();
        result.put("rows", rows);
        result.put("total_rows", totalRows);
        result.put("offset", options.getSkip());
        if(update_seq != 0) {
            result.put("update_seq", update_seq);
        }

        return result;
    }

}

abstract class AbstractTouchMapEmitBlock implements TDViewMapEmitBlock {

    protected long sequence = 0;

    void setSequence(long sequence) {
        this.sequence = sequence;
    }

}
