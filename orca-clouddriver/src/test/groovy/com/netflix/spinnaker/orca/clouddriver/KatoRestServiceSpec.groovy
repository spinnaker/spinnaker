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

import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfiguration
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfigurationProperties
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.test.httpserver.HttpServerRule
import org.junit.Rule
import retrofit.RequestInterceptor
import retrofit.client.OkClient
import spock.lang.Specification
import spock.lang.Subject
import static java.net.HttpURLConnection.HTTP_ACCEPTED
import static java.net.HttpURLConnection.HTTP_OK
import static retrofit.RestAdapter.LogLevel.FULL

class KatoRestServiceSpec extends Specification {

  @Rule
  HttpServerRule httpServer = new HttpServerRule()

  @Subject
  KatoRestService service

  @Subject
  CloudDriverTaskStatusService taskStatusService

  RequestInterceptor noopInterceptor = new RequestInterceptor() {
    @Override
    void intercept(RequestInterceptor.RequestFacade request) {
      // do nothing
    }
  }

  final taskId = "e1jbn3"

  def setup() {
    def cfg = new CloudDriverConfiguration()
    def builder = cfg.clouddriverRetrofitBuilder(
        OrcaObjectMapper.newInstance(),
        new OkClient(),
        FULL,
        noopInterceptor,
        new CloudDriverConfigurationProperties(clouddriver: new CloudDriverConfigurationProperties.CloudDriver(baseUrl: httpServer.baseURI)))
    service = cfg.katoDeployService(builder)
    taskStatusService = cfg.cloudDriverTaskStatusService(builder)
  }

  def "can interpret the response from an operation request"() {
    given: "kato accepts an operations request"
    httpServer.expect("POST", "/ops").andRespond().withStatus(HTTP_ACCEPTED).withJsonContent {
      id taskId
      resourceLink "/task/$taskId"
    }

    and: "we request a deployment"
    def operation = [:]

    expect: "kato should return the details of the task it created"
    with(service.requestOperations(UUID.randomUUID().toString(), [operation]).toBlocking().first()) {
      it.id == taskId
    }
  }

  def "can interpret the response from a task lookup"() {
    given:
    httpServer.expect("GET", "/task/$taskId").andRespond().withStatus(HTTP_OK).withJsonContent {
      id taskId
      status {
        completed true
        failed true
      }
    }

    expect:
    with(taskStatusService.lookupTask(taskId).toBlocking().first()) {
      id == taskId
      status.completed
      status.failed
    }
  }

}
