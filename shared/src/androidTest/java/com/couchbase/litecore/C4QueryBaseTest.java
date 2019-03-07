//
// C4QueryBaseTest.java
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

import com.couchbase.litecore.fleece.AllocSlice;
import com.couchbase.litecore.fleece.Encoder;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;

public class C4QueryBaseTest extends C4BaseTest {

    //-------------------------------------------------------------------------
    // protected variables
    //-------------------------------------------------------------------------
    protected C4Query query;

    //-------------------------------------------------------------------------
    // protected methods
    //-------------------------------------------------------------------------

    protected C4Query compileSelect(String queryStr) throws LiteCoreException {
        Log.i(TAG, "Query -> " + queryStr);

        if (query != null) {
            query.free();
            query = null;
        }
        query = db.createQuery(queryStr);
        assertNotNull(query);

        Log.i(TAG, "query.explain() -> " + query.explain());

        return query;
    }

    protected C4Query compile(String whereExpr) throws LiteCoreException {
        return compile(whereExpr, null);
    }

    protected C4Query compile(String whereExpr, String sortExpr) throws LiteCoreException {
        return compile(whereExpr, sortExpr, false);
    }

    protected C4Query compile(String whereExpr, String sortExpr, boolean addOffsetLimit) throws LiteCoreException {
        Log.i(TAG, "whereExpr -> " + whereExpr + ", sortExpr -> " + sortExpr + ", addOffsetLimit -> " + addOffsetLimit);

        StringBuffer json = new StringBuffer();
        json.append("[\"SELECT\", {\"WHERE\": ");
        json.append(whereExpr);
        if (sortExpr != null && sortExpr.length() > 0) {
            json.append(", \"ORDER_BY\": ");
            json.append(sortExpr);
        }
        if (addOffsetLimit) {
            json.append(", \"OFFSET\": [\"$offset\"], \"LIMIT\":  [\"$limit\"]");
        }
        json.append("}]");

        Log.i(TAG, "Query = " + json.toString());

        if (query != null) {
            query.free();
            query = null;
        }
        query = db.createQuery(json.toString());
        assertNotNull(query);

        Log.i(TAG, "query.explain() -> " + query.explain());

        return query;
    }

    protected List<String> run() throws LiteCoreException {
        return run(null);
    }

    protected List<String> run(Map<String, Object> params) throws LiteCoreException {
        List<String> docIDs = new ArrayList<>();
        C4QueryOptions opts = new C4QueryOptions();
        C4QueryEnumerator e = query.run(opts, encodeParameters(params));
        assertNotNull(e);
        while (e.next()) {
            docIDs.add(e.getColumns().getValueAt(0).asString());
        }
        e.free();
        return docIDs;
    }

    protected List<List<List<Long>>> runFTS() throws LiteCoreException {
        return runFTS(null);
    }

    protected List<List<List<Long>>> runFTS(Map<String, Object> params) throws LiteCoreException {
        List<List<List<Long>>> matches = new ArrayList<>();
        C4QueryOptions opts = new C4QueryOptions();
        C4QueryEnumerator e = query.run(opts, encodeParameters(params));
        assertNotNull(e);
        while (e.next()) {
            List<List<Long>> match = new ArrayList<>();
            for (int i = 0; i < e.getFullTextMatchCount(); i++)
                match.add(e.getFullTextMatchs(i).toList());
            matches.add(match);
        }
        e.free();
        return matches;
    }

    private AllocSlice encodeParameters(Map<String, Object> params) throws LiteCoreException {
        Encoder encoder = new Encoder();
        encoder.write(params);
        return encoder.finish();
    }
}
