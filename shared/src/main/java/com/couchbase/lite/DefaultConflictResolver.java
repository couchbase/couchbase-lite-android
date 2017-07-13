package com.couchbase.lite;

class DefaultConflictResolver implements ConflictResolver {
    private static final String TAG = Log.DATABASE;

    /**
     * Default resolution algorithm is "most active wins", i.e. higher generation number.
     * If they are same generation, mine should win.
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
