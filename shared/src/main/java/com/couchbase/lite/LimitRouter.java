package com.couchbase.lite;

/**
 * Note: LimitRouter is an internal interface. This should not be public.
 */
interface LimitRouter {
    Limit limit(Object limit);

    Limit limit(Object limit, Object offset);
}
