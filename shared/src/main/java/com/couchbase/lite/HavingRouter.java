package com.couchbase.lite;

/**
 * Note: HavingRouter is an internal interface. This should not be public.
 */
interface HavingRouter {
    Having having(Expression expression);
}
