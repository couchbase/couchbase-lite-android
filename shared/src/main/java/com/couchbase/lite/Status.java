package com.couchbase.lite;

/**
 * Note: Status is an internal interface. This should not be public.
 */
interface Status {
    int CBLErrorDomain = 1000;

    int Forbidden = 403;
    int NotFound = 404;

    // Non-HTTP error:
    int InvalidQuery = 490;
}
