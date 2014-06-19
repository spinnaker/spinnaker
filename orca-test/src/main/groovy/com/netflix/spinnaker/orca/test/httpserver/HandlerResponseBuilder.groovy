package com.netflix.spinnaker.orca.test.httpserver

import com.google.common.base.Optional
import com.netflix.spinnaker.orca.test.ResponseBuilder
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import groovy.json.JsonBuilder
import groovy.transform.CompileStatic
import static com.google.common.net.HttpHeaders.CONTENT_TYPE
import static java.net.HttpURLConnection.HTTP_OK

@CompileStatic
class HandlerResponseBuilder implements ResponseBuilder, HttpHandler {

    protected int status = HTTP_OK
    protected final Map<String, String> headers = [:]
    protected Optional<Closure> content = Optional.absent()

    @Override
    ResponseBuilder withStatus(int status) {
        this.status = status
        return this
    }

    @Override
    ResponseBuilder withHeader(String name, String value) {
        headers[name] = value // TODO: should handle multiple values for same header
        return this
    }

    @Override
    ResponseBuilder withHeaders(Map<String, String> headers) {
        headers.putAll(headers)
        return this
    }

    @Override
    ResponseBuilder withJsonContent(Closure closure) {
        // cast required due to GROOVY-6800
        content = Optional.fromNullable(closure) as Optional<Closure>
        return this
    }

    @Override
    void handle(HttpExchange exchange) {
        def response = ""
        if (content.present) {
            def json = new JsonBuilder()
            json(content.get())
            response = json.toString()
        }
        exchange.with {
            headers.each { key, value ->
                responseHeaders.add(key, value)
            }
            sendResponseHeaders status, response.length()
            if (response.length() > 0) {
                responseHeaders.add(CONTENT_TYPE, "application/json")
                responseBody.write response.bytes
            }
        }
    }
}
