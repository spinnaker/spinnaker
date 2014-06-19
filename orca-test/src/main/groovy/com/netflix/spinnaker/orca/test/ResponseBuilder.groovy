package com.netflix.spinnaker.orca.test

import groovy.json.JsonBuilder

interface ResponseBuilder {

    /**
     * @param status the HTTP status of the response.
     * @return this object to facilitate chaining method calls.
     */
    ResponseBuilder withStatus(int status)

    /**
     * @param name the name of an HTTP response header that should be sent with the response.
     * @param value the value of the HTTP response header.
     * @return this object to facilitate chaining method calls.
     */
    ResponseBuilder withHeader(String name, String value)

    /**
     * @param responseHeaders any HTTP response headers that should be sent with the response.
     * @return this object to facilitate chaining method calls.
     */
    ResponseBuilder withHeaders(Map<String, String> headers)

    /**
     * @param content a <em>Closure</em> used to construct a response using {@link groovy.json.JsonBuilder}.
     * @return this object to facilitate chaining method calls.
     */
    ResponseBuilder withJsonContent(@DelegatesTo(JsonBuilder) Closure closure)
}
