package com.couchbase.lite;

public class MostActiveWinsConflictResolver implements ConflictResolver {
    @Override
    public ReadOnlyDocument resolve(Conflict conflict) {
        return null;
    }
}
