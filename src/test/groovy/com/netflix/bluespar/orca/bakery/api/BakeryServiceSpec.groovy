package com.netflix.bluespar.orca.bakery.api

import com.netflix.bluespar.orca.test.HttpServerSpecification
import retrofit.RestAdapter
import spock.lang.Subject

import static com.google.common.net.HttpHeaders.LOCATION
import static java.net.HttpURLConnection.*
import static retrofit.RestAdapter.LogLevel.FULL

class BakeryServiceSpec extends HttpServerSpecification {

    RestAdapter restAdapter

    @Subject
    BakeryService bakery

    final bakeURI = "/api/v1/us-west-1/bake"
    final statusURI = "/api/v1/us-west-1/status"
    final bakeId = "b-123456789"
    final statusId = "s-123456789"

    def setup() {
        restAdapter = new RestAdapter.Builder()
            .setEndpoint(baseURI)
            .setLogLevel(FULL)
            .build()
        bakery = restAdapter.create(BakeryService)
    }

    def "should return status of newly created bake"() {
        given: "the bakery accepts a new bake"
        expect("POST", bakeURI, HTTP_ACCEPTED) {
            state "PENDING"
            progress 0
            resource_uri "$baseURI$bakeURI/$bakeId"
            uri "$baseURI$statusURI/$statusId"
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
            resource_uri "$baseURI$bakeURI/$bakeId"
            uri "$baseURI$statusURI/$statusId"
            id "s-123456789"
            attempts 1
            ctime 1382310109766
            mtime 1382310109766
            messages(["on instance i-66f5913d runnning: aminate ..."])
        }
        expect "POST", bakeURI, HTTP_SEE_OTHER, [(LOCATION): "$baseURI$statusURI/$statusId"], responseContent
        expect "GET", "$statusURI/$statusId", HTTP_OK, responseContent

        expect: "createBake should return the status of the bake"
        with(bakery.createBake("us-west-1").toBlockingObservable().first()) {
            id == statusId
            state == BakeStatus.State.RUNNING
        }
    }


}
