package com.couchbase.cblite.support;

import org.apache.http.client.HttpClient;

public interface HttpClientFactory {
	HttpClient getHttpClient();
}
