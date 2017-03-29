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
 * Options for creating a database index.
 */
public class IndexOptions {
    private String language;

    private boolean ignoreDiacritics;

    /**
     * Create an IndexOptions with default values.
     * <p>
     * The default language is {@code null}, which means using the language of the current locale.
     * The default ignoreDiacritics is {@code false}, which means not ignoring accents/diacritical
     * marks.
     * </p>
     */
    public IndexOptions() {
        this(null, false);
    }

    /**
     * Create an IndexOptions with the given language and ignoreDiacritics value.
     * @param language the language code, e.g. "en" or "de". This affects how word breaks and
     *                 word stems are parsed. Use {@code null} for current locale and {@code ""}
     *                 to disable stemming.
     * @param ignoreDiacritics {@code true} to ignore accents/diacritical marks.
     */
    public IndexOptions(String language, boolean ignoreDiacritics) {
        this.language = language;
        this.ignoreDiacritics = ignoreDiacritics;
    }
    /**
     * Get the language option.
     * @return the language option.
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Get the ignoring diacritics option.
     * @return the ignoring diacritics option.
     */
    public boolean isIgnoreDiacritics() {
        return ignoreDiacritics;
    }
}
