package com.couchbase.lite;

class DefaultConflictResolver implements ConflictResolver {
    /**
     * Default resolution algorithm:
     * 1. DELETE always wins.
     * 2. Most active wins (Higher generation number).
     * 3. Higher RevID wins.
     */
    @Override
    public Document resolve(Conflict conflict) {
        Document mine = conflict.getMine();
        Document theirs = conflict.getTheirs();
        if (theirs.isDeleted())
            return theirs;
        else if (mine.isDeleted())
            return mine;
        else if (mine.generation() > theirs.generation())
            return mine;
        else if (mine.generation() < theirs.generation())
            return theirs;
        else if (mine.getRevID() != null && mine.getRevID().compareTo(theirs.getRevID()) > 0)
            return mine;
        else
            return theirs;
    }
}
