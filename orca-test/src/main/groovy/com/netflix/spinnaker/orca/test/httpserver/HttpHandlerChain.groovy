package com.netflix.spinnaker.orca.test.httpserver

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import groovy.transform.CompileStatic
import groovy.transform.PackageScope

@CompileStatic
abstract class HttpHandlerChain implements HttpHandler {

    static Builder builder() {
        new Builder()
    }

    private HttpHandler nextHandler

    protected final void next(HttpExchange exchange) {
        nextHandler.handle(exchange)
    }

    @PackageScope
    final void setNextHandler(HttpHandler nextHandler) {
        this.nextHandler = nextHandler
    }

    public static class Builder {

        private HttpHandlerChain head = new CompletingHttpHandler()
        private HttpHandlerChain tail = head

        Builder withNextHandler(HttpHandlerChain nextHandler) {
            checkNotComplete()
            tail.nextHandler = nextHandler
            tail = nextHandler
            return this
        }

        Builder withFinalHandler(HttpHandler finalHandler) {
            checkNotComplete()
            tail.nextHandler = finalHandler
            tail = null
            return this
        }

        HttpHandler build() {
            checkComplete()
            return head
        }

        Builder withMethodFilter(String method) {
            withNextHandler new MethodFilteringHttpHandler(method)
        }

        private void checkNotComplete() {
            if (complete()) {
                throw new IllegalStateException("Handler chain is already complete")
            }
        }

        private void checkComplete() {
            if (!complete()) {
                throw new IllegalStateException("Handler chain is incomplete")
            }
        }

        private boolean complete() {
            !tail
        }
    }

}