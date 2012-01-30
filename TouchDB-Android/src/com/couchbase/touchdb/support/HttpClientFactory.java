package com.couchbase.touchdb.support;

import org.apache.http.client.HttpClient;

public interface HttpClientFactory {
	HttpClient getHttpClient();
}
