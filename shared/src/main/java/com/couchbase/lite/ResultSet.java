/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
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

import com.couchbase.litecore.C4QueryEnumerator;
import com.couchbase.litecore.LiteCoreException;

public class ResultSet {
    private static final String LOG_TAG = Log.QUERY;

    private Query query;

    private C4QueryEnumerator c4enum;

    /* package */ ResultSet(Query query, C4QueryEnumerator c4enum) {
        this.query = query;
        this.c4enum = c4enum;
        Log.v(LOG_TAG, "Beginning query enumeration (%p)", c4enum);
    }

    public QueryRow next() {
        try {
            if (c4enum.next()) {
                if (c4enum.getFullTextTermCount() > 0)
                    return new FullTextQueryRow(query, c4enum);
                else
                    return new QueryRow(query, c4enum);
            } else
                return null;
        } catch (LiteCoreException e) {
            Log.w(LOG_TAG, "Query enumeration error: %s", e);
        }
        return null;
    }
}
