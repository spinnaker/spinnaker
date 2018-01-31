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

package com.netflix.spinnaker.gate.security.ldap

import com.netflix.spinnaker.gate.Main
import com.netflix.spinnaker.gate.config.RedisTestConfig
import com.netflix.spinnaker.gate.security.FormLoginRequestBuilder
import com.netflix.spinnaker.gate.security.GateSystemTest
import com.netflix.spinnaker.gate.security.YamlFileApplicationContextInitializer
import com.netflix.spinnaker.gate.services.AccountLookupService
import com.netflix.spinnaker.gate.services.internal.ClouddriverService
import com.netflix.spinnaker.gate.services.internal.ClouddriverService.AccountDetails
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.web.FilterChainProxy
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@Slf4j
@TestPropertySource("/ldap.properties")
@GateSystemTest
@ContextConfiguration(
    classes = [Main, LdapSsoConfig, LdapTestConfig],
    initializers = YamlFileApplicationContextInitializer
)
class LdapAuthSpec extends Specification {

  @Autowired
  WebApplicationContext wac

  MockMvc mockMvc

  @Autowired
  FilterChainProxy springSecurityFilterChain

  def setup() {
    this.mockMvc = MockMvcBuilders.webAppContextSetup(this.wac).addFilter(springSecurityFilterChain).build()
  }

  def cleanup() {
    ApacheDSServer.stopServer()
  }

  def "should do ldap authentication"() {
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

    mockMvc.perform(new FormLoginRequestBuilder().user("batman")
                                                 .password("batman")
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

  static class LdapTestConfig {

    @Bean
    RedisTestConfig redisTestConfig() {
      new RedisTestConfig()
    }

    @Autowired
    LdapSsoConfig ldapSsoConfig(LdapSsoConfig config){
      ApacheDSServer.startServer("classpath:ldap-server.ldif")
      Integer ldapPort = ApacheDSServer.getServerPort()
      log.debug("Setting LDAP server port to $ldapPort")
      config.ldapConfigProps.url = config.ldapConfigProps.url.replaceFirst("5555", ldapPort.toString())
      return config
    }

    @Bean
    @Primary
    AccountLookupService accountLookupService() {
      return new AccountLookupService() {
        @Override
        List<AccountDetails> getAccounts() {
          return [
              new AccountDetails(name: "foo")
          ]
        }
      }
    }
  }
}
