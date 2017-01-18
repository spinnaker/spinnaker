/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.loader

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.TemplateLoaderException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject;

class HttpTemplateSchemeLoaderSpec extends Specification {
  @Subject
  def schemeLoader = new HttpTemplateSchemeLoader(new ObjectMapper())

  @Shared
  def httpServer

  @Shared
  def httpServerPort

  void setupSpec() {
    httpServer = HttpServer.create(new InetSocketAddress(0), 0)
    httpServer.createContext("/", { HttpExchange exchange ->
      try {
        exchange.responseHeaders.set("Content-Type", "application/yml")
        def resource = HttpTemplateSchemeLoaderSpec.class.getResource(exchange.requestURI.path)
        if (resource) {
          exchange.sendResponseHeaders(200, 0)
        } else {
          exchange.sendResponseHeaders(404, 0)
          exchange.responseBody.close()
          return
        }

        new File(resource.toURI()).withInputStream {
          exchange.responseBody << it
        }
        exchange.responseBody.close()

      } catch (ignored) {
        exchange.sendResponseHeaders(500, 0)
        exchange.responseBody.close()
      }
    } as HttpHandler)

    httpServer.start()
    httpServerPort = httpServer.address.port
  }

  void cleanupSpec() {
    httpServer.stop(0);
  }

  void "should fetch http uri"() {
    given:
    def uri = new URI("http://localhost:${httpServerPort}/templates/simple-001.yml")

    when:
    def pipelineTemplate = schemeLoader.load(uri)

    then:
    pipelineTemplate.id == "simpleTemplate"
  }

  void "should raise exception when uri does not exist"() {
    given:
    def uri = new URI("http://localhost:${httpServerPort}/templates/does-not-exist.yml")

    when:
    schemeLoader.load(uri)

    then:
    def e = thrown(TemplateLoaderException)
    e.cause instanceof FileNotFoundException
  }
}
