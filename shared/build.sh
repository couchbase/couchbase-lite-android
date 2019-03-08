#!/bin/bash

javah -d ./src/main/cpp -classpath ./src/main/java \
    com.couchbase.lite.internal.core.C4 \
    com.couchbase.lite.internal.core.C4Base \
    com.couchbase.lite.internal.core.C4BlobKey \
    com.couchbase.lite.internal.core.C4BlobStore \
    com.couchbase.lite.internal.core.C4BlobReadStream \
    com.couchbase.lite.internal.core.C4BlobWriteStream \
    com.couchbase.lite.internal.core.C4Database \
    com.couchbase.lite.internal.core.C4DatabaseObserver \
    com.couchbase.lite.internal.core.C4DocEnumerator \
    com.couchbase.lite.internal.core.C4Document \
    com.couchbase.lite.internal.core.C4DocumentObserver \
    com.couchbase.lite.internal.core.C4FullTextMatch \
    com.couchbase.lite.internal.core.C4Key \
    com.couchbase.lite.internal.core.C4Listener \
    com.couchbase.lite.internal.core.C4Log \
    com.couchbase.lite.internal.core.C4Prediction \
    com.couchbase.lite.internal.core.C4Query \
    com.couchbase.lite.internal.core.C4QueryEnumerator \
    com.couchbase.lite.internal.core.C4RawDocument \
    com.couchbase.lite.internal.core.C4Replicator \
    com.couchbase.lite.internal.core.C4Socket \
    com.couchbase.lite.internal.fleece.AllocSlice \
    com.couchbase.lite.internal.fleece.Encoder \
    com.couchbase.lite.internal.fleece.FLArray \
    com.couchbase.lite.internal.fleece.FLArrayIterator \
    com.couchbase.lite.internal.fleece.FLDict \
    com.couchbase.lite.internal.fleece.FLDictIterator \
    com.couchbase.lite.internal.fleece.FLEncoder \
    com.couchbase.lite.internal.fleece.FLSliceResult \
    com.couchbase.lite.internal.fleece.FLValue

