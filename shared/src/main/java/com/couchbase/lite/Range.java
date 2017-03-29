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

/**
 * Full-text word range.
 */
public class Range {
    /**
     * The constant representing the NOT_FOUND location.
     */
    public static final int NOT_FOUND = -1;

    private int location;
    private int length;

    /* package */ Range(int location, int length) {
        this.location = location;
        this.length = length;
    }

    /**
     * Get the start location that the word is located in the full text.
     * @return the location.
     */
    public int getLocation() {
        return location;
    }

    /**
     * Get the length of the word.
     * @return the length of the word.
     */
    public int getLength() {
        return length;
    }
}
