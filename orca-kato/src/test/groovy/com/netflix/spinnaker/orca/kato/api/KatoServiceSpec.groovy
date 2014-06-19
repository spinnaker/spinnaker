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

class KatoServiceSpec extends Specification {

    @Rule HttpServerRule httpServer = new HttpServerRule()

    @Subject KatoService service

    final taskId = "e1jbn3"

    def setup() {
        service = new KatoConfiguration(retrofitClient: new OkClient(), retrofitLogLevel: FULL)
            .katoDeployService(newFixedEndpoint(httpServer.baseURI))
    }

    def "can interpret the response from an operation request"() {
        given: "kato accepts an operations request"
        httpServer.expect("POST", "/ops").andRespond().withStatus(HTTP_ACCEPTED).withJsonContent {
            id taskId
            resourceLink "/task/$taskId"
        }

        and: "we request a deployment"
        def operation = new DeployOperation()

        expect: "kato should return the details of the task it created"
        with(service.requestOperations([operation]).toBlockingObservable().first()) {
            it.id == taskId
        }
    }

}
