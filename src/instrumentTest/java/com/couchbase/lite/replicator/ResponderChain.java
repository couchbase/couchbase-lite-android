package com.couchbase.lite.replicator;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

import java.io.IOException;
import java.util.List;
import java.util.Queue;

/**
 * A ResponderChain lets you stack up responders which will handle sequences of requests.
 */
public class ResponderChain implements CustomizableMockHttpClient.Responder {

    /**
     * A list of responders which are "consumed" as soon as they answer a request
     */
    private Queue<CustomizableMockHttpClient.Responder> responders;

    /**
     * The final responder in the chain, which is "sticky" and won't
     * be removed after handling a request.
     */
    private CustomizableMockHttpClient.Responder sentinal;

    /**
     * Create a responder chain.
     *
     * In this version, you pass it a list of responders which are "consumed"
     * as soon as they answer a request.  If you go off the edge and have more
     * requests than responders, then it will throw a runtime exception.
     *
     * If you have no idea how many requests this responder chain will need to
     * service, then set a sentinal or use the other ctor that takes a sentinal.
     *
     * @param responders a list of responders, which will be "consumed" as soon
     *                   as they respond to a request.
     */
    public ResponderChain(Queue<CustomizableMockHttpClient.Responder> responders) {
        this.responders = responders;
    }

    /**
     * Create a responder chain with a "sentinal".
     *
     * If and when the responders passed into responders are consumed, then the sentinal
     * will handle all remaining requests to the responder chain.
     *
     * This is the version you want to use if you don't know ahead of time how many
     * requests this responderchain will need to handle.
     *
     * @param responders a list of responders, which will be "consumed" as soon
     *                   as they respond to a request.
     * @param sentinal the final responder in the chain, which is "sticky" and won't
     *                 be removed after handling a request.
     */
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
