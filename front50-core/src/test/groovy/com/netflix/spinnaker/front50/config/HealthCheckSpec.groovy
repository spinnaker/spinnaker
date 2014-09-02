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


package com.netflix.spinnaker.front50.config

import com.netflix.spinnaker.amos.AccountCredentials
import com.netflix.spinnaker.amos.AccountCredentialsProvider
import com.netflix.spinnaker.front50.model.application.ApplicationDAO
import com.netflix.spinnaker.front50.model.application.ApplicationDAOProvider
import org.springframework.boot.actuate.endpoint.HealthEndpoint
import org.springframework.boot.actuate.endpoint.mvc.EndpointMvcAdapter
import org.springframework.boot.actuate.health.OrderedHealthAggregator
import org.springframework.http.HttpStatus
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Shared
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup

/**
 * Created by aglover on 5/10/14.
 */
class HealthCheckSpec extends Specification {

  @Shared
  MockMvc mockMvc

  @Shared
  HealthCheck healthCheck

  @Shared
  ApplicationDAO dao

  void setup() {
    def accountCredentialsProvider = Stub(AccountCredentialsProvider)
    accountCredentialsProvider.all >> [Stub(AccountCredentials)]
    dao = Mock(ApplicationDAO)
    healthCheck = new HealthCheck(accountCredentialsProvider: accountCredentialsProvider, applicationDAOProviders: [new TestApplicationDAOProvider(dao: dao)])
    this.mockMvc = standaloneSetup(new EndpointMvcAdapter(
      new HealthEndpoint(new OrderedHealthAggregator(), [health: this.healthCheck]))).setMessageConverters new MappingJackson2HttpMessageConverter() build()
  }

  void 'health check should return 5xx error if dao is not working'() {
    setup:


    when:
    def result = mockMvc.perform(get("/health")).andReturn()

    then:
    1 * dao.isHealthly() >> false
    result.response.status == HttpStatus.SERVICE_UNAVAILABLE.value()
  }

  void 'health check should return Ok'() {
    when:
    def response = mockMvc.perform(get("/health"))

    then:
    1 * dao.isHealthly() >> true
    response.andExpect status().isOk()
  }

  static class TestApplicationDAOProvider implements ApplicationDAOProvider<AccountCredentials> {

    ApplicationDAO dao

    @Override
    boolean supports(Class credentialsClass) {
      true
    }

    @Override
    ApplicationDAO getForAccount(AccountCredentials credentials) {
      dao
    }
  }
}
