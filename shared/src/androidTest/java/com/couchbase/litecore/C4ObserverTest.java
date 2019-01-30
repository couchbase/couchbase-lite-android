//
// C4ObserverTest.java
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
package com.couchbase.litecore;

import android.util.Log;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class C4ObserverTest extends C4BaseTest implements C4Constants {

    C4DatabaseObserver dbObserver;
    C4DocumentObserver docObserver;
    AtomicInteger dbCallbackCalls;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        dbObserver = null;
        docObserver = null;
        dbCallbackCalls = new AtomicInteger(0);
    }

    @Override
    public void tearDown() throws Exception {
        if (dbObserver != null) dbObserver.free();
        if (docObserver != null) docObserver.free();
        super.tearDown();
    }

    void checkChanges(List<String> expectedDocIDs,
                      List<String> expectedRevIDs,
                      boolean expectedExternal) {
        checkChanges(dbObserver, expectedDocIDs, expectedRevIDs, expectedExternal);
    }

    void checkChanges(C4DatabaseObserver observer,
                      List<String> expectedDocIDs,
                      List<String> expectedRevIDs,
                      boolean expectedExternal) {
        C4DatabaseChange[] changes = observer.getChanges(100);
        assertNotNull(changes);
        assertEquals(expectedDocIDs.size(), changes.length);
        for (int i = 0; i < changes.length; i++) {
            assertEquals(expectedDocIDs.get(i), changes[i].getDocID());
            assertEquals(expectedRevIDs.get(i), changes[i].getRevID());
            assertEquals(expectedExternal, changes[i].isExternal());
        }
    }

    // - DB Observer
    @Test
    public void testDBObserver() throws LiteCoreException {
        dbObserver = this.db.createDatabaseObserver(new C4DatabaseObserverListener() {
            @Override
            public void callback(C4DatabaseObserver observer, Object context) {
                Log.e(TAG, "testDocObserver().C4DatabaseObserver.callback()");
                assertEquals(C4ObserverTest.this, context);
                dbCallbackCalls.incrementAndGet();
            }
        }, this);
        assertEquals(0, dbCallbackCalls.get());

        createRev("A", "1-aa", kFleeceBody);
        assertEquals(1, dbCallbackCalls.get());
        createRev("B", "1-bb", kFleeceBody);
        assertEquals(1, dbCallbackCalls.get());

        checkChanges(Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);

        createRev("B", "2-bbbb", kFleeceBody);
        assertEquals(2, dbCallbackCalls.get());
        createRev("C", "1-cc", kFleeceBody);
        assertEquals(2, dbCallbackCalls.get());

        checkChanges(Arrays.asList("B", "C"), Arrays.asList("2-bbbb", "1-cc"), false);

        dbObserver.free();
        dbObserver = null;

        createRev("A", "2-aaaa", kFleeceBody);
        assertEquals(2, dbCallbackCalls.get());
    }

    // - Doc Observer
    @Test
    public void testDocObserver() throws LiteCoreException {
        createRev("A", "1-aa", kFleeceBody);

        docObserver = this.db.createDocumentObserver("A", new C4DocumentObserverListener() {
            @Override
            public void callback(C4DocumentObserver observer, String docID, long sequence, Object context) {
                Log.e(TAG, "testDocObserver().C4DocumentObserver.callback() docID: " + docID + ", sequence: " + sequence);
                assertEquals(C4ObserverTest.this, context);
                assertEquals("A", docID);
                assertTrue(sequence > 0);
                dbCallbackCalls.incrementAndGet();
            }
        }, this);
        assertEquals(0, dbCallbackCalls.get());

        createRev("A", "2-bb", kFleeceBody);
        createRev("B", "1-bb", kFleeceBody);
        assertEquals(1, dbCallbackCalls.get());
    }

    // - Multi-DBs Observer
    @Test
    public void testMultiDBsObserver() throws LiteCoreException {
        dbObserver = this.db.createDatabaseObserver(new C4DatabaseObserverListener() {
            @Override
            public void callback(C4DatabaseObserver observer, Object context) {
                Log.e(TAG, "dbObserver.callback()");
                assertEquals(C4ObserverTest.this, context);
                dbCallbackCalls.incrementAndGet();
            }
        }, this);
        assertEquals(0, dbCallbackCalls.get());

        createRev("A", "1-aa", kFleeceBody);
        assertEquals(1, dbCallbackCalls.get());
        createRev("B", "1-bb", kFleeceBody);
        assertEquals(1, dbCallbackCalls.get());

        checkChanges(Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);

        // Open another database on the same file:
        //C4Database otherdb = new Database(dir.getPath(), C4DatabaseFlags.kC4DB_Create | C4DatabaseFlags.kC4DB_Bundled | C4DatabaseFlags.kC4DB_SharedKeys, encryptionAlgorithm(), encryptionKey());
        C4Database otherdb = new C4Database(dir.getPath(), getFlags(), null, getVersioning(), encryptionAlgorithm(), encryptionKey());
        assertNotNull(otherdb);
        {
            boolean commit = false;
            otherdb.beginTransaction();
            try {
                createRev(otherdb, "c", "1-cc", kFleeceBody);
                createRev(otherdb, "d", "1-dd", kFleeceBody);
                createRev(otherdb, "e", "1-ee", kFleeceBody);
                commit = true;
            } finally {
                otherdb.endTransaction(commit);
            }
        }

        assertEquals(2, dbCallbackCalls.get());
        checkChanges(Arrays.asList("c", "d", "e"), Arrays.asList("1-cc", "1-dd", "1-ee"), true);

        dbObserver.free();
        dbObserver = null;

        createRev("A", "2-aaaa", kFleeceBody);
        assertEquals(2, dbCallbackCalls.get());

        otherdb.close();
        otherdb.free();
    }

    // - Multi-DBObservers
    @Test
    public void testMultiDBObservers() throws LiteCoreException {
        dbObserver = this.db.createDatabaseObserver(new C4DatabaseObserverListener() {
            @Override
            public void callback(C4DatabaseObserver observer, Object context) {
                Log.e(TAG, "dbObserver.callback()");
                assertEquals(C4ObserverTest.this, context);
                dbCallbackCalls.incrementAndGet();
            }
        }, this);
        assertEquals(0, dbCallbackCalls.get());

        final AtomicInteger dbCallbackCalls1 = new AtomicInteger(0);
        C4DatabaseObserver dbObserver1 = this.db.createDatabaseObserver(new C4DatabaseObserverListener() {
            @Override
            public void callback(C4DatabaseObserver observer, Object context) {
                Log.e(TAG, "dbObserver1.callback()");
                assertEquals(C4ObserverTest.this, context);
                dbCallbackCalls1.incrementAndGet();
            }
        }, this);
        assertEquals(0, dbCallbackCalls1.get());


        createRev("A", "1-aa", kFleeceBody);
        assertEquals(1, dbCallbackCalls.get());
        assertEquals(1, dbCallbackCalls1.get());
        createRev("B", "1-bb", kFleeceBody);
        assertEquals(1, dbCallbackCalls.get());
        assertEquals(1, dbCallbackCalls1.get());

        checkChanges(dbObserver, Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);
        checkChanges(dbObserver1, Arrays.asList("A", "B"), Arrays.asList("1-aa", "1-bb"), false);

        createRev("B", "2-bbbb", kFleeceBody);
        assertEquals(2, dbCallbackCalls.get());
        assertEquals(2, dbCallbackCalls1.get());
        createRev("C", "1-cc", kFleeceBody);
        assertEquals(2, dbCallbackCalls.get());
        assertEquals(2, dbCallbackCalls1.get());

        checkChanges(dbObserver, Arrays.asList("B", "C"), Arrays.asList("2-bbbb", "1-cc"), false);
        checkChanges(dbObserver1, Arrays.asList("B", "C"), Arrays.asList("2-bbbb", "1-cc"), false);


        dbObserver.free();
        dbObserver = null;

        dbObserver1.free();
        dbObserver1 = null;

        createRev("A", "2-aaaa", kFleeceBody);
        assertEquals(2, dbCallbackCalls.get());
        assertEquals(2, dbCallbackCalls1.get());
    }
}
