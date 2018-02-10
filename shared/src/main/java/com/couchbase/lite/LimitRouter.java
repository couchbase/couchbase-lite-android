package com.couchbase.lite;

/**
 * Note: LimitRouter is an internal interface. This should not be public.
 */
interface LimitRouter {
    Limit limit(Expression limit);

    Limit limit(Expression limit, Expression offset);
}
