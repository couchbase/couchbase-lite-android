//
// ValueIndex.java
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
package com.couchbase.lite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Value (standard query) index
 */
public final class ValueIndex extends AbstractIndex {
    private final List<ValueIndexItem> indexItems;

    ValueIndex(ValueIndexItem... indexItems) {
        this.indexItems = Arrays.asList(indexItems);
    }

    @Override
    IndexType type() {
        return IndexType.Value;
    }

    @Override
    String language() {
        return null;
    }

    @Override
    boolean ignoreAccents() {
        return false;
    }

    @Override
    List<Object> items() {
        final List<Object> items = new ArrayList<>();
        for (ValueIndexItem item : indexItems) { items.add(item.viExpression.asJSON()); }
        return items;
    }
}
