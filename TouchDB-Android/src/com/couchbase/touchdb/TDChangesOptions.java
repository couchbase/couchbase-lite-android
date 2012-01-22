package com.couchbase.touchdb;

import java.util.EnumSet;

import com.couchbase.touchdb.TDDatabase.TDContentOptions;

/**
 * Options for _changes feed
 */
public class TDChangesOptions {

    private int limit = Integer.MAX_VALUE;
    private EnumSet<TDContentOptions> contentOptions = EnumSet.noneOf(TDDatabase.TDContentOptions.class);
    private boolean includeDocs = false;
    private boolean includeConflicts = false;
    private boolean sortBySequence = true;

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public boolean isIncludeConflicts() {
        return includeConflicts;
    }

    public void setIncludeConflicts(boolean includeConflicts) {
        this.includeConflicts = includeConflicts;
    }

    public boolean isIncludeDocs() {
        return includeDocs;
    }

    public void setIncludeDocs(boolean includeDocs) {
        this.includeDocs = includeDocs;
    }

    public boolean isSortBySequence() {
        return sortBySequence;
    }

    public void setSortBySequence(boolean sortBySequence) {
        this.sortBySequence = sortBySequence;
    }

    public EnumSet<TDContentOptions> getContentOptions() {
        return contentOptions;
    }

    public void setContentOptions(EnumSet<TDContentOptions> contentOptions) {
        this.contentOptions = contentOptions;
    }
}
