package com.couchbase.lite;

interface Status {
    int CBLErrorDomain = 1000;

    int Forbidden = 403;
    int NotFound = 404;

    // Non-HTTP error:
    int InvalidQuery = 490;
}
