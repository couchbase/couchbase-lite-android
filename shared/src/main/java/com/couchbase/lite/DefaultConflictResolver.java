package com.couchbase.lite;

/*package*/ class DefaultConflictResolver implements ConflictResolver {
    /**
     * Default resolution algorithm is "most active wins", i.e. higher generation number.
     */
    @Override
    public ReadOnlyDocument resolve(Conflict conflict) {
        ReadOnlyDocument mine = conflict.getMine();
        ReadOnlyDocument theirs = conflict.getTheirs();
        if (mine.generation() >= theirs.generation())
            return mine;
        else
            return theirs;
    }
}
