package com.couchbase.lite;

import java.util.List;

public abstract class Index {
    abstract IndexType type();

    abstract String locale();

    abstract boolean ignoreDiacritics();

    abstract List<Object> items();

    public static ValueIndexOn valueIndex() {
        return new ValueIndexOn();
    }

    public static FTSIndexOn ftsIndex() {
        return new FTSIndexOn();
    }

    public static class ValueIndexOn {
        public ValueIndex on(ValueIndexItem... items) {
            return new ValueIndex(items);
        }
    }

    public static class FTSIndexOn {
        public FTSIndex on(FTSIndexItem item) {
            return new FTSIndex(item);
        }
    }
}
