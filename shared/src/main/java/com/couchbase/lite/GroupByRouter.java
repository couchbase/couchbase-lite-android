package com.couchbase.lite;

/**
 * Note: GroupByRouter is an internal interface. This should not be public.
 */
interface GroupByRouter {
    GroupBy groupBy(Expression... expressions);
}
