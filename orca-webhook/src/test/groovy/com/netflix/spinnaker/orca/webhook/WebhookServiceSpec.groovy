/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.webhook

import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestTemplate
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess

class WebhookServiceSpec extends Specification {

  @Shared
  def restTemplate = new RestTemplate()

  def server = MockRestServiceServer.createServer(restTemplate)

  @Subject
  def webhookService = new WebhookService(restTemplate: restTemplate)

  def "Webhook is being called with correct parameters"() {
    expect:
    server.expect(requestTo("https://my.webhook.com/v1/test"))
      .andExpect(method(HttpMethod.POST))
      .andExpect(jsonPath('$.payload1').value("Hello"))
      .andExpect(jsonPath('$.payload2').value("World!"))
      .andRespond(withSuccess('{"status": "SUCCESS"}', MediaType.APPLICATION_JSON))

    when:
    def responseEntity = webhookService.exchange(
      HttpMethod.POST,
      "https://my.webhook.com/v1/test",
      ["payload1": "Hello", "payload2": "World!"])

    then:
    server.verify()
    responseEntity.statusCode == HttpStatus.OK
    responseEntity.body == ["status": "SUCCESS"]
  }

  def "Status endpoint is being called"() {
    expect:
    server.expect(requestTo("https://my.webhook.com/v1/status/123"))
      .andExpect(method(HttpMethod.GET))
      .andRespond(withSuccess('["element1", 123, false]', MediaType.APPLICATION_JSON))

    when:
    def responseEntity = webhookService.getStatus("https://my.webhook.com/v1/status/123")

    then:
    server.verify()
    responseEntity.statusCode == HttpStatus.OK
    responseEntity.body == ["element1", 123, false]
  }
}
