//
// C4AllDocsPerformanceTest.java
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
package com.couchbase.lite.internal.core;

import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.utils.StopWatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;


/**
 * Ported from c4AllDocsPerformanceTest.cc
 */
public class C4AllDocsPerformanceTest extends C4BaseTest {
    private static final int DOC_SIZE = 1000;
    private static final int DOC_NUM = 1000; // 100000

    @Before
    public void setUp() throws Exception {

        super.setUp();

        char[] chars = new char[DOC_SIZE];
        Arrays.fill(chars, 'a');
        final String content = new String(chars);

        boolean commit = false;
        db.beginTransaction();
        try {
            Random random = new Random();
            for (int i = 0; i < DOC_NUM; i++) {
                String docID = String.format(
                    "doc-%08x-%08x-%08x-%04x",
                    random.nextLong(),
                    random.nextLong(),
                    random.nextLong(),
                    i);
                String json = String.format("{\"content\":\"%s\"}", content);
                List<String> list = new ArrayList<String>();
                if (isRevTrees()) { list.add("1-deadbeefcafebabe80081e50"); }
                else { list.add("1@deadbeefcafebabe80081e50"); }
                String[] history = list.toArray(new String[list.size()]);
                C4Document doc = db.put(json2fleece(json), docID, 0, true, false, history, true, 0, 0);
                assertNotNull(doc);
                doc.free();
            }
            commit = true;
        }
        finally {
            db.endTransaction(commit);
        }

        assertEquals(DOC_NUM, db.getDocumentCount());
    }

    static C4Document nextDocument(C4DocEnumerator e) throws LiteCoreException {
        return e.next() ? e.getDocument() : null;
    }

    // - AllDocsPerformance
    @Test
    public void testAllDocsPerformance() throws LiteCoreException {
        StopWatch st = new StopWatch();
        st.start();

        // No start or end ID:
        int iteratorFlags = C4Constants.EnumeratorFlags.DEFAULT;
        iteratorFlags &= ~C4Constants.EnumeratorFlags.INCLUDE_BODIES;
        C4DocEnumerator e = db.enumerateAllDocs(iteratorFlags);
        C4Document doc;
        int i = 0;
        while ((doc = nextDocument(e)) != null) {
            try {
                i++;
            }
            finally {
                doc.free();
            }
        }
        assertEquals(DOC_NUM, i);

        double elapsed = st.getElapsedTimeMillis();
        Log.i(TAG, String.format("Enumerating %d docs took %.3f ms (%.3f ms/doc)", i, elapsed, elapsed / i));
    }
}
