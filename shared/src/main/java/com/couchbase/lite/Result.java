/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.lite;

import com.couchbase.litecore.C4QueryEnumerator;
import com.couchbase.litecore.fleece.FLArrayIterator;
import com.couchbase.litecore.fleece.FLValue;

import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Result represents a row of result set returned by a Query.
 */
public class Result implements ReadOnlyArrayInterface, ReadOnlyDictionaryInterface, Iterable<String> {

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private Query query;
    private C4QueryEnumerator c4enum;
    private FLArrayIterator columns;
    private String documentID;

    //---------------------------------------------
    // constructors
    //---------------------------------------------

    Result(Query query, C4QueryEnumerator c4enum) {
        this.query = query;
        this.c4enum = c4enum;
        this.documentID = c4enum.getDocID();
        this.columns = c4enum.getColumns();
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Get the ID of the document that produced this row.
     *
     * @return the ID of the document that produced this row.
     */
    public String getDocumentID() {
        return c4enum.getDocID();
    }

    /**
     * Get the sequence number of the document revision that produced this row.
     *
     * @return the sequence number of the document revision that produced this row.
     */
    public long getSequence() {
        return c4enum.getDocSequence();
    }

    /**
     * Get the document that produced this row.
     *
     * @return the document object.
     */
    public Document getDocument() {
        return query.getDatabase().getDocument(documentID);
    }

    @Override
    public String toString() {
        return "Result{" +
                "query=" + query +
                ", c4enum=" + c4enum +
                ", documentID='" + documentID + '\'' +
                '}';
    }

//    public Object getObject(int index) {
//        FLValue value = flValueAtIndex(index);
//        return value.toObject(null, null);
//    }

    //---------------------------------------------
    // implementation of common betwen ReadOnlyArrayInterface and ReadOnlyDictionaryInterface
    //--------------------------------------------

    @Override
    public int count() {
        return 0;
    }

    //---------------------------------------------
    // implementation of ReadOnlyArrayInterface
    //---------------------------------------------

    @Override
    public Object getObject(int index) {
        FLValue value = flValueAtIndex(index);
        return value.toObject(null, null);
    }

    @Override
    public String getString(int index) {
        return null;
    }

    @Override
    public Number getNumber(int index) {
        return null;
    }

    @Override
    public int getInt(int index) {
        return 0;
    }

    @Override
    public long getLong(int index) {
        return 0;
    }

    @Override
    public float getFloat(int index) {
        return 0;
    }

    @Override
    public double getDouble(int index) {
        return 0;
    }

    @Override
    public boolean getBoolean(int index) {
        return false;
    }

    @Override
    public Blob getBlob(int index) {
        return null;
    }

    @Override
    public Date getDate(int index) {
        return null;
    }

    @Override
    public ReadOnlyArrayInterface getArray(int index) {
        return null;
    }

    @Override
    public ReadOnlyDictionaryInterface getDictionary(int index) {
        return null;
    }

    @Override
    public List<Object> toList() {
        return null;
    }

    //---------------------------------------------
    // implementation of ReadOnlyDictionaryInterface
    //---------------------------------------------
    @Override
    public List<String> getKeys() {
        return null;
    }

    @Override
    public Object getObject(String key) {
        return null;
    }

    @Override
    public String getString(String key) {
        return null;
    }

    @Override
    public Number getNumber(String key) {
        return null;
    }

    @Override
    public int getInt(String key) {
        return 0;
    }

    @Override
    public long getLong(String key) {
        return 0;
    }

    @Override
    public float getFloat(String key) {
        return 0;
    }

    @Override
    public double getDouble(String key) {
        return 0;
    }

    @Override
    public boolean getBoolean(String key) {
        return false;
    }

    @Override
    public Blob getBlob(String key) {
        return null;
    }

    @Override
    public Date getDate(String key) {
        return null;
    }

    @Override
    public ReadOnlyArrayInterface getArray(String key) {
        return null;
    }

    @Override
    public ReadOnlyDictionaryInterface getDictionary(String key) {
        return null;
    }

    @Override
    public Map<String, Object> toMap() {
        return null;
    }

    @Override
    public boolean contains(String key) {
        return false;
    }

    //---------------------------------------------
    // implementation of Iterable
    //---------------------------------------------
    @Override
    public Iterator<String> iterator() {
        return null;
    }

    //---------------------------------------------
    // Protected level access
    //---------------------------------------------

    protected Query getQuery() {
        return query;
    }

    protected C4QueryEnumerator getC4enum() {
        return c4enum;
    }

    //---------------------------------------------
    // private level access
    //---------------------------------------------
    FLValue flValueAtIndex(int index) {
        return columns.getValueAt(index);
    }


}
