/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.test.httpserver.internal

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler

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