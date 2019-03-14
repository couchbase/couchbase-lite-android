//
// AbstractIndexBuilder.java
//
// Copyright (c) 2019 Couchbase, Inc All rights reserved.
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

package com.couchbase.lite;

import android.support.annotation.NonNull;


/**
 * Based IndexBuilder used for building database index objects.
 */
abstract class AbstractIndexBuilder {

    /**
     * Create a value index with the given index items. The index items are a list of
     * the properties or expressions to be indexed.
     *
     * @param items The index items
     * @return The value index
     */
    public static ValueIndex valueIndex(@NonNull ValueIndexItem... items) {
        if (items == null) {
            throw new IllegalArgumentException("items cannot be null.");
        }
        return new ValueIndex(items);
    }

    /**
     * Create a full-text search index with the given index item and options. Typically the index item is
     * the property that is used to perform the match operation against with.
     *
     * @param items The index items.
     * @return The full-text search index.
     */
    public static FullTextIndex fullTextIndex(@NonNull FullTextIndexItem... items) {
        if (items == null) {
            throw new IllegalArgumentException("items cannot be null.");
        }
        return new FullTextIndex(items);
    }
}
