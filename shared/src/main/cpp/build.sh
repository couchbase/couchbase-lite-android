#!/bin/bash

javah -d ./jni -classpath ./src \
	com.couchbase.lite.internal.Database \
	com.couchbase.lite.internal.Document \
	com.couchbase.lite.internal.DocumentIterator \
	com.couchbase.lite.internal.LiteCoreException \
	com.couchbase.lite.internal.Logger \
	com.couchbase.lite.internal.C4BlobKey \
	com.couchbase.lite.internal.C4BlobStore \
	com.couchbase.lite.internal.C4Prediction \
	com.couchbase.lite.internal.C4Query \
	com.couchbase.lite.internal.C4QueryEnumerator \
	com.couchbase.lite.internal.C4BlobReadStream \
	com.couchbase.lite.internal.C4BlobWriteStream \
	com.couchbase.lite.internal.fleece.FLArray \
	com.couchbase.lite.internal.fleece.FLArrayIterator \
	com.couchbase.lite.internal.fleece.FLDict \
	com.couchbase.lite.internal.fleece.FLDictIterator \
	com.couchbase.lite.internal.fleece.FLEncoder \
	com.couchbase.lite.internal.fleece.FLSliceResult \
	com.couchbase.lite.internal.fleece.FLValue
	
