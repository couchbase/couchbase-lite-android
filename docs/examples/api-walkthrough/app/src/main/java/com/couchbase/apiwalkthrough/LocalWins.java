package com.couchbase.apiwalkthrough;

import com.couchbase.lite.Conflict;
import com.couchbase.lite.ConflictResolver;
import com.couchbase.lite.ReadOnlyDocument;

public class LocalWins implements ConflictResolver {

    @Override
    public ReadOnlyDocument resolve(Conflict conflict) {
        ReadOnlyDocument base = conflict.getBase();
        ReadOnlyDocument mine = conflict.getMine();
        ReadOnlyDocument theirs = conflict.getTheirs();

        return theirs;
    }

}
