package com.netflix.bluespar.orca.bakery.api

import com.sun.net.httpserver.HttpServer
import groovy.json.JsonBuilder
import retrofit.RestAdapter
import spock.lang.Specification
import spock.lang.Subject

import static com.google.common.net.HttpHeaders.CONTENT_TYPE
import static java.net.HttpURLConnection.HTTP_ACCEPTED
import static java.net.HttpURLConnection.HTTP_BAD_METHOD

class BakeryServiceSpec extends Specification {

    HttpServer server
    RestAdapter restAdapter

    @Subject
    BakeryService bakery

    def setup() {
        def address = startServer()
        restAdapter = new RestAdapter.Builder()
            .setEndpoint(address)
            .build()
        bakery = restAdapter.create(BakeryService)
    }

    def cleanup() {
        server.stop(3)
    }

    def "should return status of newly created bake"() {
        given:
        expect("POST", "/api/v1/us-west-1/bake", HTTP_ACCEPTED) {
            state "PENDING"
            progress 0
            resource_uri "http://bakery.test.netflix.net:7001/api/v1/us-west-1/bake/b-123456789"
            uri "http://bakery.test.netflix.net:7001/api/v1/us-west-1/status/s-123456789"
            id "s-123456789"
            attempts 0
            ctime 1382310109766
            mtime 1382310109766
            messages([])
        }

        expect:
        with(bakery.createBake("us-west-1").toBlockingObservable().first()) {
            id == "s-123456789"
            state == BakeStatus.State.PENDING
        }
    }

    private void expect(String method, String path, Integer responseStatus, Closure responseContent) {
        server.createContext path, { exchange ->
            if (exchange.requestMethod == method) {
                def json = new JsonBuilder()
                json(responseContent)
                def response = json.toString()
                exchange.with {
                    responseHeaders[CONTENT_TYPE] = "application/json"
                    sendResponseHeaders responseStatus, response.length()
                    responseBody.write response.bytes
                    close()
                }
            } else {
                exchange.with {
                    sendResponseHeaders HTTP_BAD_METHOD, 0
                    close()
                }
            }
        }
    }

    private String startServer() {
        def address = new InetSocketAddress(0)
        server = HttpServer.create(address, 0)
        server.with {
            executor = null
            start()
        }
        return "http://localhost:$server.address.port"
    }

}
