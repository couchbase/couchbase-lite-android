/**
 * Copyright (c) 2017 Couchbase, Inc. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */

package com.couchbase.lite;

import com.couchbase.litecore.C4QueryEnumerator;
import com.couchbase.litecore.LiteCoreException;

import java.io.UnsupportedEncodingException;

/**
 * A single result from a full-text Query.
 */
public class FullTextResult extends Result {
    //---------------------------------------------
    // static variables
    //---------------------------------------------
    private static final String TAG = Log.QUERY;

    //---------------------------------------------
    // member variables
    //---------------------------------------------
    private int matchCount;
    private FullTextTerm[] matches;

    //---------------------------------------------
    // constructors
    //---------------------------------------------

    FullTextResult(ResultSet rs, C4QueryEnumerator c4enum) {
        super(rs, c4enum);
        this.matchCount = (int) c4enum.getFullTextTermCount();
        if (this.matchCount > 0) {
            matches = new FullTextTerm[this.matchCount];
            for (int i = 0; i < this.matchCount; i++) {
                int termIndex = (int) c4enum.getFullTextTermIndex(i);
                int start = (int) c4enum.getFullTextTermStart(i);
                int length = (int) c4enum.getFullTextTermLength(i);
                matches[i] = new FullTextTerm(termIndex, start, length);
            }
        }
    }

    //---------------------------------------------
    // API - public methods
    //---------------------------------------------

    /**
     * Get the text emitted when the view was indexed which contains the match(es).
     *
     * @return the text containing the match(es).
     */
    public String getFullTextMatched() {
        byte[] data = getFullTextUTF8Data();
        String text = null;
        if (data != null) {
            try {
                text = new String(data, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Log.w(TAG, "Text matched has an invalid encoding.", e);
            }
        }
        return text;
    }

    /**
     * Get the number of query words that were found in the full-text. If a query word appears more
     * than once, only the first instance is counted.
     *
     * @return the number of query words found.
     */
    public long getMatchCount() {
        return matchCount;
    }

    /**
     * Get the index of the search term matched by a particular match. Search terms are the
     * individual words in the full-text search expression, skipping duplicates and noise/stop-words.
     * They're numbered from zero.
     *
     * @param matchNumber the zero based index number of the matched word found in the full-text.
     * @return the index of the search term.
     */
    public int getTermIndexOfMatch(int matchNumber) {
        if (matchNumber >= matchCount)
            throw new AssertionError("matchNumber < matchCount");
        return matches[matchNumber].termIndex;
    }

    /**
     * Get the character range in the full-text of a particular match.
     *
     * @param matchNumber the zero based index number of the matched word found in the full-text.
     * @return the character range in the fullText of the given match index.
     */
    public Range getTextRangeOfMatch(int matchNumber) {
        if (matchNumber >= matchCount)
            throw new AssertionError("matchNumber < matchCount");

        int byteStart = matches[matchNumber].getStart();
        int byteLength = matches[matchNumber].getLength();
        byte[] rawText = getFullTextUTF8Data();
        if (rawText == null)
            return new Range(Range.NOT_FOUND, 0);

        int location = charCountOfUTF8ByteRange(rawText, 0, byteStart);
        int length = charCountOfUTF8ByteRange(rawText, byteStart, byteStart + byteLength);
        return new Range(location, length);
    }

    //---------------------------------------------
    // Private level access
    //---------------------------------------------

    private byte[] getFullTextUTF8Data() {
        try {
            return getRs().getQuery().getC4Query().getFullTextMatched(getDocumentID(), getSequence());
        } catch (LiteCoreException e) {
            Log.w(TAG, "Error when get full text matched", e);
        }
        return null;
    }

    private int charCountOfUTF8ByteRange(byte[] bytes, int byteStart, int byteEnd) {
        try {
            if (byteStart < byteEnd) {
                String str = new String(bytes, byteStart, byteEnd - byteStart, "UTF-8");
                return str.length();
            }
        } catch (UnsupportedEncodingException e) {
            Log.w(TAG, "Text matched has an invalid encoding.", e);
        }
        return 0;
    }

    private class FullTextTerm {
        private int termIndex;
        private int start;
        private int length;

        FullTextTerm(int termIndex, int start, int length) {
            this.termIndex = termIndex;
            this.start = start;
            this.length = length;
        }

        int getTermIndex() {
            return termIndex;
        }

        int getStart() {
            return start;
        }

        int getLength() {
            return length;
        }
    }
}
