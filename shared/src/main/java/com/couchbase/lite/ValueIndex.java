package com.couchbase.lite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Value (standard query) index
 */
public final class ValueIndex extends AbstractIndex {
    private List<ValueIndexItem> indexItems;

    ValueIndex(ValueIndexItem... indexItems) {
        this.indexItems = Arrays.asList(indexItems);
    }

    @Override
    IndexType type() {
        return IndexType.Value;
    }

    @Override
    String language() {
        return null;
    }

    @Override
    boolean ignoreAccents() {
        return false;
    }

    @Override
    List<Object> items() {
        List<Object> items = new ArrayList<Object>();
        for (ValueIndexItem item : indexItems)

            items.add(item.expression.asJSON());
        return items;
    }
}
