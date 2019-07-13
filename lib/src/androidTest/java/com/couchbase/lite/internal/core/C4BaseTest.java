//
// C4BaseTest.java
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

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.After;
import org.junit.Before;

import com.couchbase.lite.CouchbaseLite;
import com.couchbase.lite.LiteCoreException;
import com.couchbase.lite.internal.fleece.FLEncoder;
import com.couchbase.lite.internal.fleece.FLSliceResult;
import com.couchbase.lite.internal.fleece.FLValue;
import com.couchbase.lite.internal.utils.StopWatch;
import com.couchbase.lite.utils.Config;
import com.couchbase.lite.utils.FileUtils;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;


public class C4BaseTest {
    protected static final String TAG = "C4Test";

    protected static final String kDocID = "mydoc";
    protected static final String kRevID = "1-abcd";
    protected static final String kRev2ID = "2-c001d00d";
    protected static final String kRev3ID = "3-deadbeef";

    protected Context context;
    protected File dir;
    protected C4Database db;

    protected byte[] kFleeceBody;

    private final int flags = C4Constants.DatabaseFlags.CREATE | C4Constants.DatabaseFlags.SHARED_KEYS;
    private final int versioning = C4Constants.DocumentVersioning.REVISION_TREES;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getTargetContext();
        CouchbaseLite.init(context);

        String tempdir = context.getCacheDir().getAbsolutePath();
        if (tempdir != null) { C4.setenv("TMPDIR", tempdir, 1); }
        String dbFilename = "cbl_core_test.sqlite3";
        deleteDatabaseFile(dbFilename);
        dir = new File(context.getFilesDir(), dbFilename);
        FileUtils.cleanDirectory(dir);
        db = new C4Database(dir.getPath(), getFlags(), null, getVersioning(), encryptionAlgorithm(), encryptionKey());

        Map<String, Object> body = new HashMap<>();
        body.put("ans*wer", 42);
        kFleeceBody = createFleeceBody(body);
    }

    @After
    public void tearDown() throws Exception {
        if (db != null) {
            db.close();
            db.free();
            db = null;
        }
        if (dir != null) { FileUtils.cleanDirectory(dir); }
    }

    protected void createRev(String docID, String revID, byte[] body) throws LiteCoreException {
        createRev(docID, revID, body, 0);
    }

    protected int getFlags() { return flags; }

    protected int getVersioning() { return versioning; }

    protected int encryptionAlgorithm() { return C4Constants.EncryptionAlgorithm.NONE; }

    protected byte[] encryptionKey() { return null; }

    protected boolean isRevTrees() { return versioning == C4Constants.DocumentVersioning.REVISION_TREES; }

    protected boolean isVersionVectors() { return versioning == C4Constants.DocumentVersioning.VERSION_VECTORS; }

    private void deleteDatabaseFile(String dbFileName) { deleteFile(dbFileName); }

    protected long importJSONLines(String name) throws LiteCoreException, IOException {
        return importJSONLines(getAsset(name));
    }

    protected long importJSONLines(String name, String idPrefix) throws LiteCoreException, IOException {
        return importJSONLines(getAsset(name), idPrefix);
    }

    protected void createRev(C4Database db, String docID, String revID, byte[] body)
        throws LiteCoreException {
        createRev(db, docID, revID, body, 0);
    }

    protected void createRev(String docID, String revID, byte[] body, int flags)
        throws LiteCoreException {
        createRev(this.db, docID, revID, body, flags);
    }

    protected void reopenDB() throws LiteCoreException {
        if (db != null) {
            db.close();
            db.free();
            db = null;
        }
        db = new C4Database(dir.getPath(), getFlags(), null, getVersioning(),
            encryptionAlgorithm(), encryptionKey());
        assertNotNull(db);
    }

    protected void reopenDBReadOnly() throws LiteCoreException {
        if (db != null) {
            db.close();
            db.free();
            db = null;
        }
        int flag = getFlags() & ~C4Constants.DatabaseFlags.CREATE | C4Constants.DatabaseFlags.READ_ONLY;
        db = new C4Database(dir.getPath(), flag, null, getVersioning(),
            encryptionAlgorithm(), encryptionKey());
        assertNotNull(db);
    }

    protected byte[] json2fleece(String json) throws LiteCoreException {
        boolean commit = false;
        db.beginTransaction();
        FLSliceResult body = null;
        try {
            body = db.encodeJSON(json5(json).getBytes());
            byte[] bytes = body.getBuf();
            commit = true;
            return bytes;
        }
        finally {
            if (body != null) { body.free(); }
            db.endTransaction(commit);
        }
    }

    protected String json5(String input) {
        String json = null;
        try {
            json = FLValue.json5ToJson(input);
        }
        catch (LiteCoreException e) {
            Log.e(TAG, String.format("Error in json5() input -> %s", input), e);
            fail(e.getMessage());
        }
        assertNotNull(json);
        return json;
    }

    protected List<C4BlobKey> addDocWithAttachments(String docID, List<String> attachments, String contentType)
        throws LiteCoreException {
        List<C4BlobKey> keys = new ArrayList<>();
        StringBuilder json = new StringBuilder();
        json.append("{attached: [");
        for (String attachment : attachments) {
            C4BlobStore store = db.getBlobStore();
            C4BlobKey key = store.create(attachment.getBytes());
            keys.add(key);
            String keyStr = key.toString();
            json.append("{'");
            json.append("@type");
            json.append("': '");
            json.append("blob");
            json.append("', ");
            json.append("digest: '");
            json.append(keyStr);
            json.append("', length: ");
            json.append(attachment.length());
            json.append(", content_type: '");
            json.append(contentType);
            json.append("'},");
        }
        json.append("]}");
        String jsonStr = json5(json.toString());
        FLSliceResult body = db.encodeJSON(jsonStr.getBytes());

        // Save document:
        C4Document doc
            = db.put(body, docID, C4Constants.RevisionFlags.HAS_ATTACHMENTS, false, false, new String[0], true, 0, 0);
        assertNotNull(doc);
        body.free();
        doc.free();
        return keys;
    }

    /**
     * @param flags C4RevisionFlags
     */
    private void createRev(C4Database db, String docID, String revID, byte[] body, int flags)
        throws LiteCoreException {
        boolean commit = false;
        db.beginTransaction();
        try {
            C4Document curDoc = db.get(docID, false);
            assertNotNull(curDoc);
            List<String> revIDs = new ArrayList<>();
            revIDs.add(revID);
            if (curDoc.getRevID() != null) { revIDs.add(curDoc.getRevID()); }
            String[] history = revIDs.toArray(new String[0]);
            C4Document doc = db.put(body, docID, flags,
                true, false, history, true,
                0, 0);
            assertNotNull(doc);
            doc.free();
            curDoc.free();
            commit = true;
        }
        finally {
            db.endTransaction(commit);
        }
    }

    private InputStream getAsset(String name) {
        return this.getClass().getResourceAsStream("/assets/" + name);
    }

    private long importJSONLines(InputStream is) throws LiteCoreException, IOException {
        // Android API 16 arm emulator is slow. This is reason timeout is set 60 sec
        return importJSONLines(is, 120, true);
    }

    private long importJSONLines(InputStream is, String idPrefix) throws LiteCoreException, IOException {
        // Android API 16 arm emulator is slow. This is reason timeout is set 60 sec
        return importJSONLines(is, idPrefix, 120, true);
    }

    // Read a file that contains a JSON document per line. Every line becomes a document.
    private long importJSONLines(InputStream is, double timeout, boolean verbose)
        throws IOException, LiteCoreException {
        return importJSONLines(is, "", timeout, verbose);
    }

    // Read a file that contains a JSON document per line. Every line becomes a document.
    private long importJSONLines(InputStream is, String idPrefix, double timeout, boolean verbose)
        throws LiteCoreException, IOException {
        Log.i(TAG, "Reading data from input stream ...");
        StopWatch st = new StopWatch();
        long numDocs = 0;
        boolean commit = false;
        db.beginTransaction();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            try {
                String line;
                while ((line = br.readLine()) != null) {
                    FLSliceResult body = db.encodeJSON(line.getBytes());
                    try {
                        String docID = String.format(Locale.ENGLISH, "%s%07d", idPrefix, numDocs + 1);

                        C4Document doc = db.put(body, docID, 0,
                            false, false,
                            new String[0], true, 0, 0);
                        try {
                            assertNotNull(doc);
                        }
                        finally {
                            doc.free();
                        }
                    }
                    finally {
                        body.free();
                    }

                    numDocs++;
                    if (numDocs % 1000 == 0 && st.getElapsedTimeSecs() >= timeout) {
                        String msg = String.format(
                            Locale.ENGLISH, "Stopping JSON import after %.3f sec",
                            st.getElapsedTimeSecs());
                        Log.w(TAG, msg);
                        throw new IOException(msg);
                    }
                    if (verbose && numDocs % 100000 == 0) { Log.i(TAG, String.valueOf(numDocs)); }
                }
            }
            finally {
                br.close();
            }
            commit = true;
        }
        finally {
            Log.i(TAG, "Committing...");
            db.endTransaction(commit);
        }

        if (verbose) { Log.i(TAG, st.toString("Importing", numDocs, "doc")); }
        return numDocs;
    }

    private void deleteFile(String filename) {
        File file = new File(context.getFilesDir(), filename);
        if (file.exists()) {
            if (!file.delete()) {
                Log.e(TAG, "ERROR failed to delete: dbFile=" + file);
            }
        }
    }

    private byte[] createFleeceBody(Map<String, Object> body) throws LiteCoreException {
        FLEncoder enc = new FLEncoder();
        try {
            enc.beginDict(body != null ? body.size() : 0);
            for (String key : body.keySet()) {
                enc.writeKey(key);
                enc.writeValue(body.get(key));
            }
            enc.endDict();
            return enc.finish();
        }
        finally { enc.free(); }
    }
}
