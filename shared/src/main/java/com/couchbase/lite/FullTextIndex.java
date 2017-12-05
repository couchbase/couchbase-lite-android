package com.couchbase.lite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

class FullTextIndex extends Index {

    private List<FullTextIndexItem> indexItems;
    String locale = null;
    boolean ignoreAccents = false;

    FullTextIndex(FullTextIndexItem... indexItems) {
        this.indexItems = Arrays.asList(indexItems);
    }

    public FullTextIndex setLocale(String locale) {
        this.locale = locale;
        return this;
    }

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
