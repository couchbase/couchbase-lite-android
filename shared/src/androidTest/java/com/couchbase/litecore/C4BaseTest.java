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
package com.couchbase.litecore;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.util.Log;

import com.couchbase.litecore.fleece.FLEncoder;
import com.couchbase.litecore.fleece.FLSliceResult;
import com.couchbase.litecore.fleece.FLValue;
import com.couchbase.litecore.utils.Config;
import com.couchbase.litecore.utils.FileUtils;
import com.couchbase.litecore.utils.StopWatch;

import org.junit.After;
import org.junit.Before;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.couchbase.lite.utils.Config.EE_TEST_PROPERTIES_FILE;
import static com.couchbase.litecore.C4Constants.C4DocumentVersioning.kC4RevisionTrees;
import static com.couchbase.litecore.C4Constants.C4DocumentVersioning.kC4VersionVectors;
import static com.couchbase.litecore.C4Constants.C4RevisionFlags.kRevHasAttachments;
import static com.couchbase.litecore.fleece.FLConstants.FLValueType.kFLData;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class C4BaseTest implements C4Constants {
    static {
        try {
            System.loadLibrary("LiteCoreJNI");
        } catch (Exception e) {
            fail("ERROR: Failed to load libLiteCoreJNI.so");
        }
    }

    protected static final String TAG = "C4Test";

    protected int encryptionAlgorithm() {
        return C4EncryptionAlgorithm.kC4EncryptionNone;
    }

    protected byte[] encryptionKey() {
        return null;
    }

    protected Config config;
    protected Context context;
    protected File dir;
    protected C4Database db = null;
    protected int flags = C4DatabaseFlags.kC4DB_Create | C4DatabaseFlags.kC4DB_SharedKeys;
    protected int versioning = kC4RevisionTrees;

    protected static final String kDocID = "mydoc";
    protected static final String kRevID = "1-abcd";
    protected static final String kRev2ID = "2-c001d00d";
    protected static final String kRev3ID = "3-deadbeef";
    protected byte[] kFleeceBody;

    protected boolean isRevTrees() {
        return versioning == kC4RevisionTrees;
    }

    protected boolean isVersionVectors() {
        return versioning == kC4VersionVectors;
    }

    public int getFlags() {
        return flags;
    }

    public int getVersioning() {
        return versioning;
    }

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getTargetContext();
        try {
            config = new Config(openTestPropertiesFile());
        } catch (FileNotFoundException e) {
            config = new Config();
        }
        String tempdir = context.getCacheDir().getAbsolutePath();
        if (tempdir != null)
            C4.setenv("TMPDIR", tempdir, 1);
        String dbFilename = "cbl_core_test.sqlite3";
        deleteDatabaseFile(dbFilename);
        dir = new File(context.getFilesDir(), dbFilename);
        FileUtils.cleanDirectory(dir);
        db = new C4Database(dir.getPath(), getFlags(), null, getVersioning(),
                encryptionAlgorithm(), encryptionKey());

        Map<String, Object> body = new HashMap<>();
        body.put("answer", 42);
        kFleeceBody = createFleeceBody(body);
    }

    protected void deleteDatabaseFile(String dbFileName) {
        deleteFile(dbFileName);
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
        enc.beginDict(body != null ? body.size() : 0);
        for (String key : body.keySet()) {
            enc.writeKey(key);
            enc.writeValue(body.get(key));
        }
        enc.endDict();
        return enc.finish();
    }

    protected byte[] json2fleece(String json) throws LiteCoreException {
        boolean commit = false;
        db.beginTransaction();
        try {
            byte[] bytes = db.encodeJSON(json5(json).getBytes()).getBuf();
            commit = true;
            return bytes;
        } finally {
            db.endTransaction(commit);
        }
    }

    public void testEncodeBytes() throws LiteCoreException {
        byte[] input = "Hello World!".getBytes();

        FLEncoder enc = new FLEncoder();
        try {
            enc.writeData(input);
            byte[] optionsFleece = enc.finish();
            assertNotNull(optionsFleece);

            FLValue value = FLValue.fromData(optionsFleece);
            assertNotNull(value);
            assertEquals(kFLData, value.getType());
            byte[] output = value.asData();
            assertNotNull(output);
            assertArrayEquals(input, output);
        } finally {
            enc.free();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (db != null) {
            db.close();
            db.free();
            db = null;
        }
        if (dir != null)
            FileUtils.cleanDirectory(dir);
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
        int flag = getFlags() & ~C4DatabaseFlags.kC4DB_Create | C4DatabaseFlags.kC4DB_ReadOnly;
        db = new C4Database(dir.getPath(), flag, null, getVersioning(),
                encryptionAlgorithm(), encryptionKey());
        assertNotNull(db);
    }

    protected void createRev(String docID, String revID, byte[] body) throws LiteCoreException {
        createRev(docID, revID, body, 0);
    }

    protected void createRev(C4Database db, String docID, String revID, byte[] body)
            throws LiteCoreException {
        createRev(db, docID, revID, body, 0);
    }

    protected void createRev(String docID, String revID, byte[] body, int flags)
            throws LiteCoreException {
        createRev(this.db, docID, revID, body, flags);
    }

    /**
     * @param flags C4RevisionFlags
     */
    protected void createRev(C4Database db, String docID, String revID, byte[] body, int flags)
            throws LiteCoreException {
        boolean commit = false;
        db.beginTransaction();
        try {
            C4Document curDoc = db.get(docID, false);
            assertNotNull(curDoc);
            List<String> revIDs = new ArrayList<String>();
            revIDs.add(revID);
            if (curDoc.getRevID() != null)
                revIDs.add(curDoc.getRevID());
            String[] history = revIDs.toArray(new String[revIDs.size()]);
            C4Document doc = db.put(body, docID, flags,
                    true, false, history, true,
                    0, 0);
            assertNotNull(doc);
            doc.free();
            curDoc.free();
            commit = true;
        } finally {
            db.endTransaction(commit);
        }
    }

    protected InputStream getAsset(String name) {
        return this.getClass().getResourceAsStream("/assets/" + name);
    }

    protected long importJSONLines(String name) throws LiteCoreException, IOException {
        return importJSONLines(getAsset(name));
    }
    protected long importJSONLines(String name, String idPrefix) throws LiteCoreException, IOException {
        return importJSONLines(getAsset(name), idPrefix);
    }
    protected long importJSONLines(InputStream is) throws LiteCoreException, IOException {
        // Android API 16 arm emulator is slow. This is reason timeout is set 60 sec
        return importJSONLines(is, 120, true);
    }
    protected long importJSONLines(InputStream is, String idPrefix) throws LiteCoreException, IOException {
        // Android API 16 arm emulator is slow. This is reason timeout is set 60 sec
        return importJSONLines(is, idPrefix, 120, true);
    }
    // Read a file that contains a JSON document per line. Every line becomes a document.
    protected long importJSONLines(InputStream is, double timeout, boolean verbose) throws IOException, LiteCoreException {
        return importJSONLines(is, "", timeout, verbose);
    }

    // Read a file that contains a JSON document per line. Every line becomes a document.
    protected long importJSONLines(InputStream is, String idPrefix, double timeout, boolean verbose)
            throws LiteCoreException, IOException {
        Log.i(TAG, String.format("Reading data from input stream ..."));
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
                        } finally {
                            doc.free();
                        }
                    } finally {
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
                    if (verbose && numDocs % 100000 == 0)
                        Log.i(TAG, String.valueOf(numDocs));
                }
            } finally {
                br.close();
            }
            commit = true;
        } finally {
            Log.i(TAG, "Committing...");
            db.endTransaction(commit);
        }

        if (verbose) Log.i(TAG, st.toString("Importing", numDocs, "doc"));
        return numDocs;
    }

    protected String json5(String input) {
        String json = null;
        try {
            json = FLValue.json5ToJson(input);
        } catch (LiteCoreException e) {
            Log.e(TAG, String.format("Error in json5() input -> %s", input), e);
            fail(e.getMessage());
        }
        assertNotNull(json);
        return json;
    }

    private InputStream openTestPropertiesFile() throws IOException {
        try {
            return context.getAssets().open(EE_TEST_PROPERTIES_FILE);
        } catch (IOException e) {
            return context.getAssets().open(com.couchbase.lite.utils.Config.TEST_PROPERTIES_FILE);
        }
    }

    List<C4BlobKey> addDocWithAttachments(String docID, List<String> attachments, String contentType) throws LiteCoreException {
        List<C4BlobKey> keys = new ArrayList<>();
        StringBuilder json = new StringBuilder();
        int i = 0;
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
        C4Document doc = db.put(body, docID, kRevHasAttachments, false, false, new String[0], true, 0, 0);
        assertNotNull(doc);
        body.free();
        doc.free();
        return keys;
    }
}
