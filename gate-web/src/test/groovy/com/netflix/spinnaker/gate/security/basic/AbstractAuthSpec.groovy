/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.gate.security.basic

import com.netflix.spinnaker.gate.Main
import com.netflix.spinnaker.gate.config.RedisTestConfig
import com.netflix.spinnaker.gate.security.FormLoginRequestBuilder
import com.netflix.spinnaker.gate.security.GateSystemTest
import com.netflix.spinnaker.gate.security.YamlFileApplicationContextInitializer
import com.netflix.spinnaker.gate.services.AccountLookupService
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import com.netflix.spinnaker.gate.services.internal.IgorService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.util.Base64Utils
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@Slf4j
@GateSystemTest
@ContextConfiguration(
  classes = [Main, BasicAuthConfig, BasicTestConfig],
  initializers = YamlFileApplicationContextInitializer
)
abstract class AbstractAuthSpec extends Specification {

  @Autowired
  WebApplicationContext wac

  @MockBean
  private IgorService igorService

  MockMvc mockMvc

  @Autowired
  FilterChainProxy springSecurityFilterChain

  def setup() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).addFilter(springSecurityFilterChain).build()
  }

  def "should do basic authentication"() {
    setup:
    // MockMvc doesn't seem to like to generate and return cookies for some reason. In order to make
    // the login workflow operate correctly, we have to yank the session out and set it on each
    // subsequent request.
    MockHttpSession session = null

    when:
    mockMvc.perform(get("/credentials"))
      .andDo(print())
      .andExpect(status().is3xxRedirection())
      .andExpect(header().string("Location", "http://localhost/login"))
      .andDo({ result ->
      session = (MockHttpSession) result.getRequest().getSession()
    })

    mockMvc.perform(new FormLoginRequestBuilder().user("basic-user")
      .password("basic-password")
      .session(session))
      .andDo(print())
      .andExpect(status().is(302))
      .andExpect(redirectedUrl("http://localhost/credentials"))

    def result = mockMvc.perform(get("/credentials").session(session))
      .andDo(print())
      .andExpect(status().isOk())
      .andReturn()

    then:
    result.response.contentAsString.contains("foo")
  }

  def "should return user object on correct credentials"() {
    when:
    def result = mockMvc.perform(get("/auth/user")
      .header(HttpHeaders.AUTHORIZATION, "Basic " + Base64Utils.encodeToString("basic-user:basic-password".getBytes())))
      .andDo(print())
      .andExpect(status().isOk())
      .andReturn()

    then:
    result.response.contentAsString.contains("basic-user")
  }

  def "should return authentication error on bad credentials"() {
    when:
    def result = mockMvc.perform(get("/auth/user")
      .header(HttpHeaders.AUTHORIZATION, "Basic " + Base64Utils.encodeToString("basic-user:badbad".getBytes())))
      .andDo(print())
      .andExpect(status().isUnauthorized())
      .andReturn()

    then:
    result.response.errorMessage.contains("Invalid")
  }

  static class BasicTestConfig {

    @Bean
    RedisTestConfig redisTestConfig() {
      new RedisTestConfig()
    }

    @Bean
    @Primary
    AccountLookupService accountLookupService() {
      return new AccountLookupService() {
        @Override
        List<ClouddriverService.AccountDetails> getAccounts() {
          return [
            new ClouddriverService.AccountDetails(name: "foo")
          ]
        }
      }
    }
  }
}
