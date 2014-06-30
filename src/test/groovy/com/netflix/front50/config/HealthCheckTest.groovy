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

package com.netflix.front50.config

import com.netflix.front50.config.HealthCheck
import com.netflix.front50.model.application.DefaultApplicationDAO
import org.springframework.boot.actuate.endpoint.HealthEndpoint
import org.springframework.boot.actuate.endpoint.mvc.EndpointMvcAdapter
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

/**
 * Created by aglover on 5/10/14.
 */
class HealthCheckTest extends Specification {

  MockMvc mockMvc
  HealthCheck healthCheck

  void setup() {
    this.healthCheck = new HealthCheck()
    this.mockMvc = standaloneSetup(new EndpointMvcAdapter(
      new HealthEndpoint(this.healthCheck))).setMessageConverters new MappingJackson2HttpMessageConverter() build()
  }

  void 'health check should return 5xx error if dao is not working'() {
    def application = Mock(DefaultApplicationDAO)
    this.healthCheck.dao = application

    when:
    def response = mockMvc.perform(get("/health"))

    then:
    response.andExpect status().is5xxServerError()
  }

  void 'health check should return Ok'() {
    def application = Mock(DefaultApplicationDAO)
    application.isHealthly() >> true
    this.healthCheck.dao = application

    when:
    def response = mockMvc.perform(get("/health"))

    then:
    response.andExpect status().isOk()
  }
}
