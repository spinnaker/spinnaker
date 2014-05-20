package com.netflix.bluespar.orca.bakery.api

import com.netflix.bluespar.orca.test.HttpServerRule
import org.junit.Rule
import retrofit.RestAdapter
import retrofit.RetrofitError
import spock.lang.Specification
import spock.lang.Subject

import static com.google.common.net.HttpHeaders.LOCATION
import static java.net.HttpURLConnection.*
import static retrofit.RestAdapter.LogLevel.FULL

class BakeryServiceSpec extends Specification {

    @Rule HttpServerRule httpServer = new HttpServerRule()

    @Subject BakeryService bakery

    final bakePath = "/api/v1/us-west-1/bake"
    final statusPath = "/api/v1/us-west-1/status"
    final bakeId = "b-123456789"
    final statusId = "s-123456789"

    String bakeURI
    String statusURI

    def setup() {
        bakeURI = "$httpServer.baseURI$bakePath"
        statusURI = "$httpServer.baseURI$statusPath"

        bakery = new RestAdapter.Builder()
            .setEndpoint(httpServer.baseURI)
            .setLogLevel(FULL)
            .build()
            .create(BakeryService)
    }

    def "can lookup a bake status"() {
        given:
        httpServer.expect("GET", "$statusPath/$statusId", HTTP_OK) {
            state "COMPLETED"
            progress 100
            status "SUCCESS"
            code 0
            resource_uri "$bakeURI/$bakeId"
            uri "$statusURI/$statusId"
            id statusId
            attempts: 0
            ctime 1382310109766
            mtime 1382310294223
            messages(["amination success"])
        }

        expect:
        with(bakery.lookupStatus("us-west-1", statusId).toBlockingObservable().first()) {
            id == statusId
            state == BakeStatus.State.COMPLETED
        }
    }

    def "looking up an unknown status id will throw an exception"() {
        given:
        httpServer.expect("GET", "$statusPath/$statusId", HTTP_NOT_FOUND)

        when:
        bakery.lookupStatus("us-west-1", statusId).toBlockingObservable().first()

        then:
        def ex = thrown(RetrofitError)
        ex.response.status == HTTP_NOT_FOUND
    }

    def "should return status of newly created bake"() {
        given: "the bakery accepts a new bake"
        httpServer.expect("POST", bakePath, HTTP_ACCEPTED) {
            state "PENDING"
            progress 0
            resource_uri "$bakeURI/$bakeId"
            uri "$statusURI/$statusId"
            id statusId
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
        httpServer.expect "POST", bakePath, HTTP_SEE_OTHER, [(LOCATION): "$statusURI/$statusId"]
        httpServer.expect("GET", "$statusPath/$statusId", HTTP_OK) {
            state "RUNNING"
            progress 1
            resource_uri "$bakeURI/$bakeId"
            uri "$statusURI/$statusId"
            id statusId
            attempts 1
            ctime 1382310109766
            mtime 1382310109766
            messages(["on instance i-66f5913d runnning: aminate ..."])
        }

        expect: "createBake should return the status of the bake"
        with(bakery.createBake("us-west-1").toBlockingObservable().first()) {
            id == statusId
            state == BakeStatus.State.RUNNING
        }
    }

}
