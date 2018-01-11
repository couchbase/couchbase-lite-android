package com.couchbase.lite;

/**
 * Note: JoinRouter is an internal interface. This should not be public.
 */
interface JoinRouter {
    Joins join(Join... joins);
}
