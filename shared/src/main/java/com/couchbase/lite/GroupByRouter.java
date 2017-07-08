package com.couchbase.lite;

interface GroupByRouter {
    GroupBy groupBy(Expression... expressions);
}
