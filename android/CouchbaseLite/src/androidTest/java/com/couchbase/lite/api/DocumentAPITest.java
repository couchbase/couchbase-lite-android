//
// DocumentAPITest.java
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
package com.couchbase.lite.api;

import com.couchbase.lite.BaseTest;
import com.couchbase.lite.Blob;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.Database;
import com.couchbase.lite.Document;
import com.couchbase.lite.MutableDocument;
import com.couchbase.lite.internal.support.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

// https://github.com/couchbaselabs/couchbase-mobile-portal/edit/master/md-docs/_20/guides/couchbase-lite/native-api/document/java.md
public class DocumentAPITest extends BaseTest {
    static final String TAG = DocumentAPITest.class.getSimpleName();
    static final String DATABASE_NAME = "travel-sample";

    Database database;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        database = open(DATABASE_NAME);
    }

    @After
    public void tearDown() throws Exception {
        if (database != null) {
            database.close();
            database = null;
        }

        // database exist, delete it
        deleteDatabase(DATABASE_NAME);

        super.tearDown();
    }

    // ### Initializers
    @Test
    public void testInitializers() {
        // # tag::initializer[]
        MutableDocument newTask = new MutableDocument();
        newTask.setString("type", "task");
        newTask.setString("owner", "todo");
        newTask.setDate("createdAt", new Date());
        try {
            database.save(newTask);
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, e.toString());
        }
        // # end::initializer[]
    }

    // ### Mutability
    @Test
    public void testMutability() {
        try {
            database.save(new MutableDocument("xyz"));
        } catch (CouchbaseLiteException e) {
        }

        // # tag::update-document[]
        Document document = database.getDocument("xyz");
        MutableDocument mutableDocument = document.toMutable();
        mutableDocument.setString("name", "apples");
        try {
            database.save(mutableDocument);
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, e.toString());
        }
        // # end::update-document[]
    }

    // ### Typed Accessors
    @Test
    public void testTypedAccessors() {
        MutableDocument newTask = new MutableDocument();

        // # tag::date-getter[]
        newTask.setValue("createdAt", new Date());
        Date date = newTask.getDate("createdAt");
        // # end::date-getter[]
    }

    // ### Batch operations
    @Test
    public void testBatchOperations() {
        // # tag::batch[]
        try {
            database.inBatch(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < 10; i++) {
                        MutableDocument doc = new MutableDocument();
                        doc.setValue("type", "user");
                        doc.setValue("name", String.format("user %d", i));
                        doc.setBoolean("admin", false);
                        try {
                            database.save(doc);
                        } catch (CouchbaseLiteException e) {
                            Log.e(TAG, e.toString());
                        }
                        Log.i(TAG, String.format("saved user document %s", doc.getString("name")));
                    }
                }
            });
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, e.toString());
        }
        // # end::batch[]
    }

    // ### Blobs
    @Test
    public void testBlobs() {
        MutableDocument newTask = new MutableDocument();

        // # tag::blob[]
        InputStream is = getAsset("avatar.jpg");
        try {
            Blob blob = new Blob("image/jpeg", is);
            newTask.setBlob("avatar", blob);
            database.save(newTask);

            Blob taskBlob = newTask.getBlob("avatar");
            byte[] bytes = taskBlob.getContent();
        } catch (CouchbaseLiteException e) {
            Log.e(TAG, e.toString());
        } finally {
            try {
                is.close();
            } catch (IOException e) {

            }
        }
        // # end::blob[]
    }
}
