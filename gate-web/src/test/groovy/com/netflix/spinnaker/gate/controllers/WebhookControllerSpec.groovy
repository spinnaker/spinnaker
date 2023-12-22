/*
 * Copyright 2019 Andreas Bergmeier
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

package com.netflix.spinnaker.gate.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.gate.services.WebhookService
import com.netflix.spinnaker.gate.services.internal.EchoService
import com.netflix.spinnaker.gate.services.internal.OrcaServiceSelector
import com.squareup.okhttp.mockwebserver.MockWebServer
import io.cloudevents.spring.mvc.CloudEventHttpMessageConverter
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.util.NestedServletException
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.JacksonConverter
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class WebhooksControllerSpec extends Specification {

  MockMvc mockMvc

  def server = new MockWebServer()
  WebhookService webhookService

  void cleanup() {
    server.shutdown()
  }

  void setup() {
    def sock = new ServerSocket(0)
    def localPort = sock.localPort
    sock.close()

    EchoService echoService = new RestAdapter.Builder()
      .setEndpoint("http://localhost:${localPort}")
      .setClient(new OkClient())
      .setConverter(new JacksonConverter())
      .build()
      .create(EchoService)

    OrcaServiceSelector orcaServiceSelector = Mock(OrcaServiceSelector)
    webhookService = new WebhookService(echoService: echoService, orcaServiceSelector: orcaServiceSelector)

    server.start()
    mockMvc = MockMvcBuilders.standaloneSetup(new WebhookController(webhookService: webhookService))
      .setMessageConverters(new CloudEventHttpMessageConverter())
      .build()
  }

  void 'handles null Maps'() {

    given:
    WebhookController controller = new WebhookController()
    controller.webhookService = webhookService

    when:
    controller.webhooks(
      'git', 'bitbucket', null, null, 'repo:refs_changed'
    )

    then:
    retrofit.RetrofitError ex = thrown()
    ex.message.startsWith("Failed to connect to localhost")

  }

  void 'handles Bitbucket Server Ping'() {
    given:

    when:
    mockMvc.perform(post("/webhooks/git/bitbucket")
      .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk()).andReturn()

    then:
    NestedServletException ex = thrown()
    ex.message.startsWith("Request processing failed; nested exception is retrofit.RetrofitError: Failed to connect to localhost")
  }

  void 'handles CDEvents API with BAD_REQUEST'() {
    given:

    when:
    MockHttpServletResponse response = mockMvc.perform(post("/webhooks/cdevents/artifactPackaged")
      .accept(MediaType.APPLICATION_JSON))
      .andReturn().response

    then:
    response.status == 400
  }

  void 'handles CDEvents API server Ping'() {
    given:
    HttpHeaders headers = new HttpHeaders();
    headers.add("Ce-Id", "1234")
    headers.add("Ce-Specversion", "1.0")
    headers.add("Ce-Type", "dev.cdevents.artifact.packaged")
    headers.add("Ce-Source", "spinnaker.test.io")
    headers.add("Content-Type", "application/cloudevents+json")
    String cdEventData = "{\n" +
      "  \"context\": {\n" +
      "    \"version\": \"0.1.2\",\n" +
      "    \"id\": \"c046b63b-a340-4847-bc39-ee408ad666d9\",\n" +
      "    \"source\": \"http://dev.cdevents\",\n" +
      "    \"type\": \"dev.cdevents.artifact.published.0.1.0\",\n" +
      "    \"timestamp\": \"2023-11-28T15:33:03Z\"\n" +
      "  },\n" +
      "  \"subject\": {\n" +
      "    \"id\": \"pkg:oci/myapp@sha256%3A0b31b1c02ff458ad9b7b\",\n" +
      "    \"source\": \"/dev/artifact/source\",\n" +
      "    \"type\": \"artifact\",\n" +
      "    \"content\": {\n" +
      "    }\n" +
      "  },\n" +
      "  \"customData\": {},\n" +
      "  \"customDataContentType\": \"application/json\"\n" +
      "}"
    Map<String, Object> cdEvent = [
      specversion: "1.0",
      type: "dev.cdevents.artifact.packaged",
      source: "/spinnaker.test.io",
      id: "12345",
      data: cdEventData
    ]

    when:
    mockMvc.perform(post("/webhooks/cdevents/artifactPackaged")
      .headers(headers)
      .content(new ObjectMapper().writeValueAsString(cdEvent)))
      .andExpect(status().isOk()).andReturn()

    then:
    NestedServletException ex = thrown()
    ex.message.startsWith("Request processing failed; nested exception is retrofit.RetrofitError: Failed to connect to localhost")
  }
}
