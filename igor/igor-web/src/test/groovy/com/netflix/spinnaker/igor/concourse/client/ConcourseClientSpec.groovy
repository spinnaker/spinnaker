package com.netflix.spinnaker.igor.concourse.client

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import retrofit.client.Response;
import spock.lang.Shared
import spock.lang.Specification

class ConcourseClientSpec extends Specification {

    @Shared
    ConcourseClient client

    @Shared
    MockWebServer server

    void setup() {
        server = new MockWebServer()
    }

    void cleanup() {
        server.shutdown()
    }

    def "it uses v1 auth for concourse versions < 6.1.0"() {
        given:
        def expiry = java.time.ZonedDateTime.now().plusSeconds(60).toString()
        setResponse '''{
                "cluster_name": "mycluster",
                "external_url": "https://mycluster.example.com",
                "version": "6.0.0",
                "worker_version": "2.2"
            }''',
            """{
                "access_token": "my_token",
                "expiry": "${expiry}"
            }""",
            '''{
                "name": "jsmith"
            }'''

        when:
        Response resp = client.userInfo()
        RecordedRequest req1 = server.takeRequest()
        RecordedRequest req2 = server.takeRequest()
        RecordedRequest req3 = server.takeRequest()

        then:
        req1.path == '/api/v1/info'

        req2.path == '/sky/token'
        req2.getHeader('Authorization') == "Basic Zmx5OlpteDU="

        req3.path == '/sky/userinfo'
        req3.getHeader('Authorization') == "bearer my_token"

        resp.status == 200
    }

    def "it uses v2 auth for concourse versions < 6.5.0"() {
        given:
        setResponse '''{
                "cluster_name": "mycluster",
                "external_url": "https://mycluster.example.com",
                "version": "6.1.0",
                "worker_version": "2.2"
            }''',
            """{
                "id_token": "my_id_token"
            }""",
            '''{
                "name": "jsmith"
            }'''

        when:
        Response resp = client.userInfo()
        RecordedRequest req1 = server.takeRequest()
        RecordedRequest req2 = server.takeRequest()
        RecordedRequest req3 = server.takeRequest()

        then:
        req1.path == '/api/v1/info'

        req2.path == '/sky/issuer/token'
        req2.getHeader('Authorization') == "Basic Zmx5OlpteDU="

        req3.path == '/api/v1/user'
        req3.getHeader('Authorization') == "bearer my_id_token"

        resp.status == 200
    }

    def "it uses v3 auth for concourse versions >= 6.5.0"() {
        given:
        setResponse '''{
                "cluster_name": "mycluster",
                "external_url": "https://mycluster.example.com",
                "version": "6.5.0",
                "worker_version": "2.2"
            }''',
            """{
                "access_token": "my_access_token",
                "expires_in": 86399,
                "id_token": "my_id_token",
                "token_type": "bearer"
            }""",
            '''{
                "name": "jsmith"
            }'''

        when:
        Response resp = client.userInfo()
        RecordedRequest req1 = server.takeRequest()
        RecordedRequest req2 = server.takeRequest()
        RecordedRequest req3 = server.takeRequest()

        then:
        req1.path == '/api/v1/info'

        req2.path == '/sky/issuer/token'
        req2.getHeader('Authorization') == "Basic Zmx5OlpteDU="

        req3.path == '/api/v1/user'
        req3.getHeader('Authorization') == "bearer my_access_token"

        resp.status == 200
    }

    private void setResponse(String... body) {
        for(int i = 0; i < body.length; i++) {
            server.enqueue(
                new MockResponse()
                    .setBody(body[i])
                    .setHeader('Content-Type', 'application/json;charset=utf-8')
            )
        }
        server.start()
        client = new ConcourseClient(server.url('/').toString(), "test-username", "test-password")
    }
}
