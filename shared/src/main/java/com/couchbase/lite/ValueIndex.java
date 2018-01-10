package com.couchbase.lite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ValueIndex extends Index {
    private List<ValueIndexItem> indexItems;

    ValueIndex(ValueIndexItem... indexItems) {
        this.indexItems = Arrays.asList(indexItems);
    }

    @Override
    IndexType type() {
        return IndexType.Value;
    }

    @Override
    String locale() {
        return null;
    }

    @Override
    boolean ignoreDiacritics() {
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
