/**
 * Copyright (c) 2016 Couchbase, Inc. All rights reserved.
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
package com.couchbase.lite.store;

import com.couchbase.lite.Document;
import com.couchbase.lite.LiteTestCaseWithDB;
import com.couchbase.lite.SavedRevision;
import com.couchbase.lite.UnsavedRevision;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by hideki on 7/18/15.
 */
public class SQLiteStoreTest extends LiteTestCaseWithDB {

    @Override
    public void runBare() throws Throwable {
        // Run Unit Test with SQLiteStore
        useForestDB = false;
        super.runBare();
    }

    public void testWinningRevIDOfDoc() throws Exception {
        if(!isSQLiteDB())
            return;

        SQLiteStore store = (SQLiteStore) database.getStore();

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("testName", "testCreateRevisions");
        properties.put("tag", 1337);

        Map<String, Object> properties2a = new HashMap<String, Object>();
        properties2a.put("testName", "testCreateRevisions");
        properties2a.put("tag", 1338);

        Map<String, Object> properties2b = new HashMap<String, Object>();
        properties2b.put("testName", "testCreateRevisions");
        properties2b.put("tag", 1339);

        AtomicBoolean outIsDeleted = new AtomicBoolean(false);
        AtomicBoolean outIsConflict = new AtomicBoolean(false);

        // Create a conflict on purpose
        Document doc = database.createDocument();
        UnsavedRevision newRev1 = doc.createRevision();
        newRev1.setUserProperties(properties);
        SavedRevision rev1 = newRev1.save();


        long docNumericId = store.getDocNumericID(doc.getId());
        assertTrue(docNumericId != 0);
        assertEquals(rev1.getId(), store.winningRevIDOfDocNumericID(docNumericId,
                outIsDeleted, outIsConflict));
        assertFalse(outIsConflict.get());

        outIsDeleted.set(false);
        outIsConflict.set(false);
        UnsavedRevision newRev2a = rev1.createRevision();
        newRev2a.setUserProperties(properties2a);
        SavedRevision rev2a = newRev2a.save();
        assertEquals(rev2a.getId(), store.winningRevIDOfDocNumericID(docNumericId,
                outIsDeleted, outIsConflict));
        assertFalse(outIsConflict.get());

        outIsDeleted.set(false);
        outIsConflict.set(true);
        UnsavedRevision newRev2b = rev1.createRevision();
        newRev2b.setUserProperties(properties2b);
        SavedRevision rev2b = newRev2b.save(true);
        store.winningRevIDOfDocNumericID(docNumericId, outIsDeleted, outIsConflict);
        assertTrue(outIsConflict.get());
    }
}
