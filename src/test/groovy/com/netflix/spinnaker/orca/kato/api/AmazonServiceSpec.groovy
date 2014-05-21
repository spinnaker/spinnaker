package com.netflix.spinnaker.orca.kato.api

import com.netflix.spinnaker.orca.kato.config.KatoConfiguration
import com.netflix.spinnaker.orca.test.HttpServerRule
import org.junit.Rule
import retrofit.client.OkClient
import spock.lang.Specification
import spock.lang.Subject

import static java.net.HttpURLConnection.HTTP_ACCEPTED
import static retrofit.Endpoints.newFixedEndpoint
import static retrofit.RestAdapter.LogLevel.FULL

class AmazonServiceSpec extends Specification {

    @Rule HttpServerRule httpServer = new HttpServerRule()

    @Subject AmazonService amazonService

    final taskId = "e1jbn3"

    def setup() {
        amazonService = new KatoConfiguration(retrofitClient: new OkClient(), retrofitLogLevel: FULL)
            .amazonService(newFixedEndpoint(httpServer.baseURI))
    }

    def "can interpret the response from an operation request"() {
        given: "kato accepts an operations request"
        httpServer.expect("POST", "/ops", HTTP_ACCEPTED) {
            id taskId
            resourceLink "/task/$taskId"
        }

        and: "we request a deployment"
        def operation = new DeployDescription()

        expect: "kato should return the details of the task it created"
        with(amazonService.requestOperations([operation]).toBlockingObservable().first()) {
            it.id == taskId
        }
    }

}
