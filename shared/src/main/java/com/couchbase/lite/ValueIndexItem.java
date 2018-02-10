package com.couchbase.lite;

/**
 * Value Index Item
 */
public final class ValueIndexItem {
    Expression expression;

    private ValueIndexItem(Expression expression) {
        this.expression = expression;
    }

    /**
     * Creates a value index item with the given property.
     *
     * @param property the property name
     * @return The value index item
     */
    public static ValueIndexItem property(String property) {
        return new ValueIndexItem(Expression.property(property));
    }
}
