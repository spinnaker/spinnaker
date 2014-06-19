package com.netflix.spinnaker.orca.test.httpserver

import com.sun.net.httpserver.HttpExchange
import groovy.transform.CompileStatic

@CompileStatic
class CompletingHttpHandler extends HttpHandlerChain {

    @Override
    void handle(HttpExchange exchange) throws IOException {
        try {
            next exchange
        } finally {
            exchange.close()
        }
    }
}
