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

package com.netflix.spinnaker.orca.clouddriver

import com.github.tomakehurst.wiremock.WireMockServer
import com.netflix.spinnaker.config.DefaultServiceClientProvider
import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfiguration
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.test.Retrofit2TestConfig
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import spock.lang.Specification
import spock.lang.Subject
import static com.github.tomakehurst.wiremock.client.WireMock.*
import static java.net.HttpURLConnection.HTTP_ACCEPTED
import static java.net.HttpURLConnection.HTTP_OK

@SpringBootTest (
    classes = [Retrofit2TestConfig],
    webEnvironment = SpringBootTest.WebEnvironment.NONE)
class KatoRestServiceSpec extends Specification {

  public static WireMockServer wireMockServer = new WireMockServer(0)

  @Subject
  KatoRestService service

  @Autowired
  DefaultServiceClientProvider serviceClientProvider

  @Subject
  CloudDriverTaskStatusService taskStatusService

  def mapper = OrcaObjectMapper.newInstance()

  private static final taskId = "e1jbn3"

  def setupSpec() {
    wireMockServer.start()
    configureFor(wireMockServer.port())
  }

  def setup() {
    def cfg = new CloudDriverConfiguration()
    def builder = cfg.clouddriverRetrofitBuilder(
        mapper,
        serviceClientProvider,
        new CloudDriverConfigurationProperties(clouddriver: new CloudDriverConfigurationProperties.CloudDriver(baseUrl: wireMockServer.url("/"))))
    service = builder.katoDeployService(builder)
    taskStatusService = builder.cloudDriverTaskStatusService(builder)
  }


  def cleanupSpec() {
    wireMockServer.stop()
  }

  def "can interpret the response from an operation request"() {
    given: "kato accepts an operations request"
    stubFor(
      post(urlPathEqualTo("/ops"))
        .withQueryParam("clientRequestId", equalTo(requestId))
        .willReturn(
        aResponse()
          .withStatus(HTTP_ACCEPTED)
          .withBody(mapper.writeValueAsString([
          id          : taskId,
          resourceLink: "/task/$taskId"
        ])
        )
      )
    )

    and: "we request a deployment"
    def operation = [:]

    expect: "kato should return the details of the task it created"
    with(Retrofit2SyncCall.execute(service.requestOperations(requestId, [operation]))) {
      it.id == taskId
    }

    where:
    requestId = UUID.randomUUID().toString()
  }

  def "can interpret the response from a task lookup"() {
    given:
    stubFor(
      get("/task/$taskId")
        .willReturn(
        aResponse()
          .withStatus(HTTP_OK)
          .withBody(mapper.writeValueAsString([
          id    : taskId,
          status: [
            completed: true,
            failed   : true
          ]
        ]))
      )
    )

    expect:
    with(Retrofit2SyncCall.execute(taskStatusService.lookupTask(taskId))) {
      id == taskId
      status.completed
      status.failed
    }
  }

  def "can interpret the response from a cloudProvider operation request"() {
    given:
    def operation = [:]
    def requestId = UUID.randomUUID().toString()
    stubFor(
        post(urlPathEqualTo("/aws/ops"))
            .withQueryParam("clientRequestId", equalTo(requestId))
            .willReturn(
                aResponse()
                    .withStatus(HTTP_ACCEPTED)
                    .withBody(mapper.writeValueAsString([
                        id          : taskId,
                        resourceLink: "/task/$taskId"
                    ]))
            )
    )

    expect: "kato should return the details of the task it created"
    with(Retrofit2SyncCall.execute(service.requestOperations(requestId, "aws", [operation]))) {
      it.id == taskId
    }
  }
}
