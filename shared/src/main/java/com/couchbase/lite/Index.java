package com.couchbase.lite;

import java.util.List;

/**
 * Index represents an index which could be a value index for regular queries or
 * full-text index for full-text queries (using the match operator).
 */
public abstract class Index {
    protected Index() {
    }

    abstract IndexType type();

    abstract String locale();

    abstract boolean ignoreDiacritics();

    abstract List<Object> items();

    /**
     * Create a value index with the given index items. The index items are a list of
     * the properties or expressions to be indexed.
     *
     * @param items The index items
     * @return The value index
     */
    public static ValueIndex valueIndex(ValueIndexItem... items) {
        return new ValueIndex(items);
    }

    /**
     * Create a full-text search index with the given index item and options. Typically the index item is
     * the property that is used to perform the match operation against with.
     *
     * @param items The index items.
     * @return The full-text search index.
     */
    public static FullTextIndex fullTextIndex(FullTextIndexItem... items) {
        return new FullTextIndex(items);
    }
}
