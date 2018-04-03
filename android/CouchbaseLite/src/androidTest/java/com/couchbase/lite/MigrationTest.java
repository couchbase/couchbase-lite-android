//
// MigrationTest.java
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
package com.couchbase.lite;

import com.couchbase.lite.utils.ZipUtils;

import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MigrationTest extends BaseTest {
    //---------------------------------------------
    //  setUp/tearDown
    //---------------------------------------------

    /**
     * Tool to generate test db
     */
    //NOTE: @Test
    public void testPrepareDB() throws CouchbaseLiteException {
        Database db = new Database("android-sqlite", new DatabaseConfiguration(context));
        try {
            for (int i = 1; i <= 2; i++) {
                MutableDocument doc = new MutableDocument("doc" + i);
                doc.setValue("key", String.valueOf(i));
                byte[] attach = String.format(Locale.ENGLISH, "attach%d", i).getBytes();
                Blob blob = new Blob("text/plain", attach);
                doc.setValue("attach" + i, blob);
                db.save(doc);
            }
        } finally {
            db.close();
        }
    }

    // TODO: 1.x DB's attachment is not automatically ditected as blob
    @Test
    public void testOpenExsitingDBv1x() throws Exception {

        // https://github.com/couchbase/couchbase-lite-android/issues/1237

        // if db exist, delete it
        deleteDB("android-sqlite", context.getFilesDir());

        ZipUtils.unzip(getAsset("replacedb/android140-sqlite.cblite2.zip"), context.getFilesDir());

        Database db = new Database("android-sqlite", new DatabaseConfiguration(context));
        try {
            assertEquals(2, db.getCount());
            for (int i = 1; i <= 2; i++) {
                Document doc = db.getDocument("doc" + i);
                assertNotNull(doc);
                assertEquals(String.valueOf(i), doc.getString("key"));

                Dictionary attachments = doc.getDictionary("_attachments");
                assertNotNull(attachments);
                String key = "attach" + i;

                Blob blob = attachments.getBlob(key);
                assertNotNull(blob);
                byte[] attach = String.format(Locale.ENGLISH, "attach%d", i).getBytes();
                Arrays.equals(attach, blob.getContent());
            }
        } finally {
            // close db
            db.close();
            // if db exist, delete it
            deleteDB("android-sqlite", context.getFilesDir());
        }
    }

    @Test
    public void testOpenExsitingDBv1xNoAttachment() throws Exception {
        // https://github.com/couchbase/couchbase-lite-android/issues/1237

        // if db exist, delete it
        deleteDB("android-sqlite", context.getFilesDir());

        ZipUtils.unzip(getAsset("replacedb/android140-sqlite-noattachment.cblite2.zip"), context.getFilesDir());

        Database db = new Database("android-sqlite", new DatabaseConfiguration(context));
        try {
            assertEquals(2, db.getCount());
            for (int i = 1; i <= 2; i++) {
                Document doc = db.getDocument("doc" + i);
                assertNotNull(doc);
                assertEquals(String.valueOf(i), doc.getString("key"));
            }
        } finally {
            // close db
            db.close();
            // if db exist, delete it
            deleteDB("android-sqlite", context.getFilesDir());
        }
    }

    @Test
    public void testOpenExsitingDB() throws Exception {
        // if db exist, delete it
        deleteDB("android-sqlite", context.getFilesDir());

        ZipUtils.unzip(getAsset("replacedb/android200-sqlite.cblite2.zip"), context.getFilesDir());

        Database db = new Database("android-sqlite", new DatabaseConfiguration(context));
        try {
            assertEquals(2, db.getCount());
            for (int i = 1; i <= 2; i++) {
                Document doc = db.getDocument("doc" + i);
                assertNotNull(doc);
                assertEquals(String.valueOf(i), doc.getString("key"));
                Blob blob = doc.getBlob("attach" + i);
                assertNotNull(blob);
                byte[] attach = String.format(Locale.ENGLISH, "attach%d", i).getBytes();
                Arrays.equals(attach, blob.getContent());
            }
        } finally {
            // close db
            db.close();
            // if db exist, delete it
            deleteDB("android-sqlite", context.getFilesDir());
        }
    }

    // if db exist, delete it
    private void deleteDB(String name, File dir) throws CouchbaseLiteException {
        // database exist, delete it
        if (Database.exists(name, context.getFilesDir())) {
            // sometimes, db is still in used, wait for a while. Maximum 3 sec
            for (int i = 0; i < 10; i++) {
                try {
                    Database.delete(name, dir);
                    break;
                } catch (CouchbaseLiteException ex) {
                    if (ex.getCode() == CBLErrorBusy) {
                        try {
                            Thread.sleep(300);
                        } catch (Exception e) {
                        }
                    } else {
                        throw ex;
                    }
                }
            }
        }
    }
}
