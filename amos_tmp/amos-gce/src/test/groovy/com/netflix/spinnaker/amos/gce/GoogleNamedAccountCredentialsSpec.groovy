/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.amos.gce

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.compute.model.Region
import com.google.api.services.compute.model.RegionList
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import spock.lang.Specification
import spock.lang.Unroll

class GoogleNamedAccountCredentialsSpec extends Specification {

    static HttpServer createTestServerForAccountAndKey(final String account, final Optional<String> key) {
        final byte[] json = (key == null ? '{}' : (key.isPresent() ? '{"jsonKey": "' + key.get() + '"}' : '{"jsonKey": null}')).bytes
        HttpServer server = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0);
        server.createContext("/credentials/$account", new HttpHandler() {
            @Override
            void handle(HttpExchange httpExchange) throws IOException {
                if (httpExchange.getRequestMethod() == 'GET') {
                    httpExchange.getResponseHeaders().add("Content-Type", "application/json")
                    httpExchange.sendResponseHeaders(200, json.length)
                    httpExchange.getResponseBody().write(json)
                    httpExchange.getResponseBody().flush()
                    httpExchange.close()
                }
            }
        })
        return server;
    }

    @Unroll
    def 'should fetch JSON key #key using google client http transport and json parser'() {
        setup:
        def server = createTestServerForAccountAndKey(account, key)
        server.start()

        def transport = GoogleNetHttpTransport.newTrustedTransport()
        def jsonFactory = JacksonFactory.defaultInstance
        def addr = server.getAddress()

        def kmsServer = "http://${addr.hostString}:${addr.port}"

        when:
        String jsonKey = GoogleNamedAccountCredentials.getJsonKey(kmsServer, account, transport, jsonFactory)

        then:
        jsonKey == expected

        cleanup:
        server?.stop(0)

        where:
        account | key | expected
        'foo' | Optional.of('abcdefg') | 'abcdefg'
        'bar' | Optional.empty() | null
        'baz' | null | null

    }

    def 'regionlist should convert to map'() {
        setup:
        Region r1 = new Region()
        r1.setName("region1")
        r1.setZones(['region1/z1', 'region1/z2', 'region1/z3'])

        Region r2 = new Region()
        r2.setName("region2")
        r2.setZones(['region2/z4', 'region2/z5', 'region2/z6'])

        RegionList rl = new RegionList()
        rl.setItems([r1, r2])

        when:
        def map = GoogleNamedAccountCredentials.convertToMap(rl)

        then:
        map.size() == 2
        map.region1 == ['z1', 'z2', 'z3']
        map.region2 == ['z4', 'z5', 'z6']
    }
}
