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

import com.netflix.spinnaker.gate.services.WebhookService
import com.squareup.okhttp.mockwebserver.MockWebServer
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import spock.lang.Specification

class WebhooksControllerSpec extends Specification {

  MockMvc mockMvc

  def server = new MockWebServer()
  WebhookService webhookService = Mock(WebhookService)

  void cleanup() {
    server.shutdown()
  }

  void setup() {
    webhookService = Mock(WebhookService)
    server.start()
    mockMvc = MockMvcBuilders.standaloneSetup(new WebhookController(webhookService: webhookService)).build()
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
    1 * webhookService.webhooks('git', 'bitbucket', null, null, 'repo:refs_changed')
  }

  void 'handles Bitbucket Server Ping'() {
    given:

    when:
    def result = mockMvc.perform(post("/webhooks/git/bitbucket")
      .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk()).andReturn()

    then:
    result != null
    1 * webhookService.webhooks('git', 'bitbucket', null)
  }
}
