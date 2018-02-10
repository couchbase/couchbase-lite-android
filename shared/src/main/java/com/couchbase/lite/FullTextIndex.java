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
