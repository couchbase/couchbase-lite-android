//
// FullTextIndex.java
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
import java.util.Locale;

/**
 * Index for Full-Text search
 */
public final class FullTextIndex extends AbstractIndex {

    private List<FullTextIndexItem> indexItems;
    private String language = getDefaultLanguage();
    private boolean ignoreAccents = false;

    FullTextIndex(FullTextIndexItem... indexItems) {
        this.indexItems = Arrays.asList(indexItems);
    }

    /**
     * The language code which is an ISO-639 language such as "en", "fr", etc.
     * Setting the language code affects how word breaks and word stems are parsed.
     * Without setting the value, the current locale's language will be used. Setting
     * a nil or "" value to disable the language features.
     */
    public FullTextIndex setLanguage(String language) {
        this.language = language;
        return this;
    }

    /**
     * Set the true value to ignore accents/diacritical marks. The default value is false.
     */
    public FullTextIndex ignoreAccents(boolean ignoreAccents) {
        this.ignoreAccents = ignoreAccents;
        return this;
    }

    @Override
    IndexType type() {
        return IndexType.FullText;
    }

    @Override
    String language() {
        return language;
    }

    @Override
    boolean ignoreAccents() {
        return ignoreAccents;
    }

    @Override
    List<Object> items() {
        List<Object> items = new ArrayList<>();
        for (FullTextIndexItem item : indexItems)
            items.add(item.expression.asJSON());
        return items;
    }

    private static String getDefaultLanguage() {
        return Locale.getDefault().getLanguage();
    }
}
