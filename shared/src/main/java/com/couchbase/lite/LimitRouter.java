package com.couchbase.lite;


interface LimitRouter {
    Limit limit(Object limit);

    Limit limit(Object limit, Object offset);
}
