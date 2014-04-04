package com.couchbase.lite.replicator;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

import java.io.IOException;
import java.util.List;
import java.util.Queue;

public class ResponderChain implements CustomizableMockHttpClient.Responder {

    private Queue<CustomizableMockHttpClient.Responder> responders;
    private CustomizableMockHttpClient.Responder sentinal;

    public ResponderChain(Queue<CustomizableMockHttpClient.Responder> responders) {
        this.responders = responders;
    }

    public ResponderChain(Queue<CustomizableMockHttpClient.Responder> responders, CustomizableMockHttpClient.Responder sentinal) {
        this.responders = responders;
        this.sentinal = sentinal;
    }

    @Override
    public HttpResponse execute(HttpUriRequest httpUriRequest) throws IOException {

        CustomizableMockHttpClient.Responder responder;

        CustomizableMockHttpClient.Responder nextResponder = responders.peek();
        if (nextResponder != null) {
            responder = responders.remove();
        } else {
            if (sentinal != null) {
                responder = sentinal;
            } else {
                throw new RuntimeException("No more responders in queue");
            }
        }

        return responder.execute(httpUriRequest);
    }
}
