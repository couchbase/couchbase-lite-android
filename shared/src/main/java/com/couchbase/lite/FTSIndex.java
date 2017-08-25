package com.couchbase.lite;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

class FTSIndex extends Index {

    FTSIndexItem indexItem;
    String locale = null;
    boolean ignoreAccents = false;

    FTSIndex(FTSIndexItem indexItem) {
        this.indexItem = indexItem;
    }

    public FTSIndex setLocale(String locale) {
        this.locale = locale;
        return this;
    }

    public FTSIndex ignoreAccents(boolean ignoreAccents) {
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
        List<Object> indexItems = new ArrayList<>();
        if (indexItem != null)
            indexItems.add(indexItem.getExpression().asJSON());
        return indexItems;
    }
}
