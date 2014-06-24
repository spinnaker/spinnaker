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

package com.netflix.spinnaker.orca.test.httpserver

import groovy.transform.CompileStatic
import com.netflix.spinnaker.orca.test.httpserver.internal.HandlerResponseBuilder
import com.netflix.spinnaker.orca.test.httpserver.internal.HttpHandlerChain
import com.sun.net.httpserver.HttpServer
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A JUnit Rule for tests that need a running HTTP server. The server is automatically started and stopped
 * between each test. Use the {@link HttpServerRule#expect(java.lang.String, java.lang.String, int, java.util.Map, com.google.common.base.Optional)}
 * methods to configure valid endpoints that your code-under-test can connect to.
 */
@CompileStatic
class HttpServerRule implements TestRule {

  public static final int DEFAULT_SERVER_SHUTDOWN_TIMEOUT = 3

  private int serverShutdownTimeout
  private String baseURI
  private HttpServer server

  HttpServerRule() {
    this(DEFAULT_SERVER_SHUTDOWN_TIMEOUT)
  }

  HttpServerRule(int serverShutdownTimeout) {
    this.serverShutdownTimeout = serverShutdownTimeout
  }

  @Override
  Statement apply(Statement base, Description description) {
    { ->
      try {
        startServer()
        base.evaluate()
      } finally {
        stopServer()
      }
    } as Statement
  }

  /**
   * @return the URI of the root of the web server.
   */
  final String getBaseURI() {
    if (!server) {
      throw new IllegalStateException("Cannot get base URI until the server is started")
    }
    baseURI
  }

  /**
   * Sets up an expectation for an HTTP request. If a request to {@code path} is made using the specified
   * {@code method} then the server will respond according to the parameters supplied to this method. If a
   * request is made to {@code path} using a different HTTP method the server will respond with
   * {@value HttpURLConnection#HTTP_BAD_METHOD}.
   *
   * @param method the HTTP method expected
   * @param path the literal path expected relative to the base URI of the server. Note this cannot use wildcards or any other clever things.
   * @return a mechanism for configuring the response to this expectation.
   */
  final ResponseConfiguration expect(String method, String path) {
    def responseBuilder = new HandlerResponseBuilder()
    server.createContext path, HttpHandlerChain.builder().withMethodFilter(method).withFinalHandler(responseBuilder).build()
    return new ResponseConfiguration(responseBuilder)
  }

  private void startServer() {
    def address = new InetSocketAddress(0)
    server = HttpServer.create(address, 0)
    server.executor = null
    server.start()
    baseURI = "http://localhost:$server.address.port"
  }

  private void stopServer() {
    server.stop serverShutdownTimeout
  }

}
