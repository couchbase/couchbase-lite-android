package com.couchbase.cblite;

import java.util.EnumSet;

import com.couchbase.cblite.CBLDatabase.TDContentOptions;

/**
 * Options for _changes feed
 */
public class CBLChangesOptions {

    private int limit = Integer.MAX_VALUE;
    private EnumSet<TDContentOptions> contentOptions = EnumSet.noneOf(CBLDatabase.TDContentOptions.class);
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
