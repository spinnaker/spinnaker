package com.netflix.bluespar.orca.bakery.api

import com.sun.net.httpserver.HttpServer
import groovy.json.JsonBuilder
import retrofit.RestAdapter
import spock.lang.Specification
import spock.lang.Subject

import static com.google.common.net.HttpHeaders.CONTENT_TYPE
import static com.google.common.net.HttpHeaders.LOCATION
import static java.net.HttpURLConnection.*
import static java.util.Collections.EMPTY_MAP
import static retrofit.RestAdapter.LogLevel.FULL

class BakeryServiceSpec extends Specification {

    String address
    HttpServer server
    RestAdapter restAdapter

    @Subject
    BakeryService bakery

    final bakeURI = "/api/v1/us-west-1/bake"
    final statusURI = "/api/v1/us-west-1/status"
    final bakeId = "b-123456789"
    final statusId = "s-123456789"

    def setup() {
        address = startServer()
        restAdapter = new RestAdapter.Builder()
            .setEndpoint(address)
            .setLogLevel(FULL)
            .build()
        bakery = restAdapter.create(BakeryService)
    }

    def cleanup() {
        server.stop(3)
    }

    def "should return status of newly created bake"() {
        given: "the bakery accepts a new bake"
        expect("POST", bakeURI, HTTP_ACCEPTED) {
            state "PENDING"
            progress 0
            resource_uri "$address$bakeURI/$bakeId"
            uri "$address$statusURI/$statusId"
            id "s-123456789"
            attempts 0
            ctime 1382310109766
            mtime 1382310109766
            messages([])
        }

        expect: "createBake should return the status of the bake"
        with(bakery.createBake("us-west-1").toBlockingObservable().first()) {
            id == statusId
            state == BakeStatus.State.PENDING
        }
    }

    def "should handle a repeat create bake response"() {
        given: "the POST to /bake redirects to the status of an existing bake"
        def responseContent = {
            state "RUNNING"
            progress 1
            resource_uri "$address$bakeURI/$bakeId"
            uri "$address$statusURI/$statusId"
            id "s-123456789"
            attempts 1
            ctime 1382310109766
            mtime 1382310109766
            messages(["on instance i-66f5913d runnning: aminate ..."])
        }
        expect "POST", bakeURI, HTTP_SEE_OTHER, [(LOCATION): "$address$statusURI/$statusId"], responseContent
        expect "GET", "$statusURI/$statusId", HTTP_OK, responseContent

        expect: "createBake should return the status of the bake"
        with(bakery.createBake("us-west-1").toBlockingObservable().first()) {
            id == statusId
            state == BakeStatus.State.RUNNING
        }
    }

    private void expect(String method, String path, int responseStatus, Closure responseContent) {
        expect method, path, responseStatus, EMPTY_MAP, responseContent
    }

    private void expect(String method, String path, Integer responseStatus, Map<String, ?> headers, Closure responseContent) {
        server.createContext path, { exchange ->
            if (exchange.requestMethod == method) {
                def json = new JsonBuilder()
                json(responseContent)
                def response = json.toString()
                exchange.with {
                    responseHeaders[CONTENT_TYPE] = "application/json"
                    headers.each { key, value ->
                        responseHeaders[key] = value
                    }
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
