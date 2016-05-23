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
package com.couchbase.lite;

import com.couchbase.lite.internal.Body;
import com.couchbase.lite.internal.RevisionInternal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ChangesTest extends LiteTestCaseWithDB {

    private int changeNotifications = 0;

    public void testChangeNotification() throws Exception {
        changeNotifications = 0;

        Database.ChangeListener changeListener = new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                changeNotifications++;
            }
        };

        // add listener to database
        database.addChangeListener(changeListener);

        // create a document
        Map<String, Object> documentProperties = new HashMap<String, Object>();
        documentProperties.put("foo", 1);
        documentProperties.put("bar", false);
        documentProperties.put("baz", "touch");

        Body body = new Body(documentProperties);
        RevisionInternal rev1 = new RevisionInternal(body);

        Status status = new Status();
        rev1 = database.putRevision(rev1, null, false, status);

        assertEquals(1, changeNotifications);
    }

    public void testLocalChangesAreNotExternal() throws Exception {
        changeNotifications = 0;
        Database.ChangeListener changeListener = new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                changeNotifications++;
                assertFalse(event.isExternal());
            }
        };
        database.addChangeListener(changeListener);

        // Insert a document locally.
        Document document = database.createDocument();
        document.createRevision().save();

        // Make sure that the assertion in changeListener was called.
        assertEquals(1, changeNotifications);
    }

    public void testPulledChangesAreExternal() throws Exception {
        changeNotifications = 0;
        Database.ChangeListener changeListener = new Database.ChangeListener() {
            @Override
            public void changed(Database.ChangeEvent event) {
                changeNotifications++;
                assertTrue(event.isExternal());
            }
        };
        database.addChangeListener(changeListener);

        // Insert a document as if it came from a remote source.
        RevisionInternal rev = new RevisionInternal("docId", "1-1111", false);
        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put("_id", rev.getDocID());
        properties.put("_rev", rev.getRevID());
        rev.setProperties(properties);
        database.forceInsert(rev, Arrays.asList(rev.getRevID()), getReplicationURL());

        // Make sure that the assertion in changeListener was called.
        assertEquals(1, changeNotifications);
    }
}
