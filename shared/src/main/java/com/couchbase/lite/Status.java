package com.couchbase.lite;

/*package*/ interface Status {
    int CBLErrorDomain = 1000;

    int Forbidden = 403;
    int NotFound = 404;


    int DBClosed = 450; // TODO: Temporary value. Should be updated
}
