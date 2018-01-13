package com.couchbase.lite;

/**
 * Full-text Index Item.
 */
public final class FullTextIndexItem {
    Expression expression;

    private FullTextIndexItem(Expression expression) {
        this.expression = expression;
    }

    /**
     * Creates a full-text search index item with the given property.
     *
     * @param property A property used to perform the match operation against with.
     * @return The full-text search index item.
     */
    public static FullTextIndexItem property(String property) {
        return new FullTextIndexItem(Expression.property(property));
    }
}