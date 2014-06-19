package com.netflix.spinnaker.orca.test.httpserver

import com.sun.net.httpserver.HttpExchange
import groovy.transform.CompileStatic
import static java.net.HttpURLConnection.HTTP_BAD_METHOD

@CompileStatic
class MethodFilteringHttpHandler extends HttpHandlerChain {

    private final String httpMethod

    MethodFilteringHttpHandler(String httpMethod) {
        this.httpMethod = httpMethod
    }

    @Override
    void handle(HttpExchange exchange) {
        if (exchange.requestMethod == httpMethod) {
            next exchange
        } else {
            exchange.sendResponseHeaders HTTP_BAD_METHOD, 0
        }
    }
}
