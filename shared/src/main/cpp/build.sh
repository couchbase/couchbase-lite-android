#!/bin/bash

javah -d ./jni -classpath ./src \
	com.couchbase.litecore.Database \
	com.couchbase.litecore.Document \
	com.couchbase.litecore.DocumentIterator \
	com.couchbase.litecore.LiteCoreException \
	com.couchbase.litecore.Logger \
	com.couchbase.litecore.C4BlobKey \
	com.couchbase.litecore.C4BlobStore \
	com.couchbase.litecore.C4Prediction \
	com.couchbase.litecore.C4Query \
	com.couchbase.litecore.C4QueryEnumerator \
	com.couchbase.litecore.C4BlobReadStream \
	com.couchbase.litecore.C4BlobWriteStream \
	com.couchbase.litecore.fleece.FLArray \
	com.couchbase.litecore.fleece.FLArrayIterator \
	com.couchbase.litecore.fleece.FLDict \
	com.couchbase.litecore.fleece.FLDictIterator \
	com.couchbase.litecore.fleece.FLEncoder \
	com.couchbase.litecore.fleece.FLSliceResult \
	com.couchbase.litecore.fleece.FLValue
	