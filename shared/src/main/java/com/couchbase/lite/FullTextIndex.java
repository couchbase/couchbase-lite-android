package com.couchbase.lite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Index for Full-Text search
 */
public final class FullTextIndex extends Index {

    private List<FullTextIndexItem> indexItems;
    private String locale = null;
    private boolean ignoreAccents = false;

    FullTextIndex(FullTextIndexItem... indexItems) {
        this.indexItems = Arrays.asList(indexItems);
    }

    /**
     * Set the local code which is an ISO-639 language code plus, optionally, an underscore and an ISO-3166
     * country code: "en", "en_US", "fr_CA", etc. Setting the locale code affects how word breaks and
     * word stems are parsed. Setting null value to use current locale and setting "" to disable stemming.
     * The default value is null.
     */
    public FullTextIndex setLocale(String locale) {
        this.locale = locale;
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
    String locale() {
        if (locale == null) {
            return Locale.getDefault().getLanguage();
        }
        return locale;
    }

    @Override
    boolean ignoreDiacritics() {
        if (locale == null && ignoreAccents == false)
            return Locale.getDefault().getLanguage().equals("en");
        return ignoreAccents;
    }


    @Override
    List<Object> items() {
        List<Object> items = new ArrayList<>();
        for (FullTextIndexItem item : indexItems)
            items.add(item.expression.asJSON());
        return items;
    }
}
