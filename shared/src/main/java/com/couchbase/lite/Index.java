package com.couchbase.lite;

import java.util.List;

public abstract class Index {
    abstract IndexType type();

    abstract String locale();

    abstract boolean ignoreDiacritics();

    abstract List<Object> items();

    public static ValueIndex valueIndex(ValueIndexItem... items) {
        return new ValueIndex(items);
    }

    public static FullTextIndex fullTextIndex(FullTextIndexItem... items) {
        return new FullTextIndex(items);
    }

}
