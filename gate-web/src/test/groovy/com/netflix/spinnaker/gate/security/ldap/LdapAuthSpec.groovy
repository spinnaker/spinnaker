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
import com.netflix.spinnaker.gate.security.ldap.LdapSsoConfig.LdapConfigProps
import com.netflix.spinnaker.gate.services.AccountLookupService
import com.netflix.spinnaker.gate.services.internal.ClouddriverService.AccountDetails
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.ldap.server.UnboundIdContainer
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import spock.lang.Specification

import javax.servlet.http.Cookie

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic

@Slf4j
@GateSystemTest
@SpringBootTest(
    properties = ['ldap.enabled=true', 'spring.application.name=gate'])
@ContextConfiguration(
  classes = [LdapSsoConfig, Main, LdapTestConfig, RedisTestConfig],
  initializers = YamlFileApplicationContextInitializer
)
@AutoConfigureMockMvc
class LdapAuthSpec extends Specification {

  @Autowired
  MockMvc mockMvc

  def "should allow http-basic authentication"() {
    when:
    def result = mockMvc.perform(
      get("/credentials")
        .with(httpBasic("batman", "batman")))
      .andDo(print())
      .andExpect(status().isOk())
      .andReturn()

    then:
    result.response.contentAsString.contains("foo")
  }

  def "should do ldap authentication"() {
    setup:
    Cookie sessionCookie = null
    def extractSession = { MvcResult result ->
      sessionCookie = result.response.getCookie("SESSION")
    }

    when:
    mockMvc.perform(get("/credentials"))
           .andDo(print())
           .andExpect(status().is3xxRedirection())
           .andExpect(header().string("Location", "http://localhost/login"))
           .andDo(extractSession)

    mockMvc.perform(new FormLoginRequestBuilder().user("batman")
                                                 .password("batman")
                                                 .cookie(sessionCookie))
           .andDo(print())
           .andExpect(status().is(302))
           .andExpect(redirectedUrl("http://localhost/credentials"))
           .andDo(extractSession)

    def result = mockMvc.perform(get("/credentials").cookie(sessionCookie))
                        .andDo(print())
                        .andExpect(status().isOk())
                        .andReturn()

    then:
    result.response.contentAsString.contains("foo")
  }

  static class LdapTestConfig {

    static final String DEFAULT_PARTITION_SUFFIX = "dc=unit,dc=test"

    @Bean(destroyMethod = "stop")
    UnboundIdContainer unboundIdContainer(ApplicationContext ctx) {
      def c = new UnboundIdContainer(DEFAULT_PARTITION_SUFFIX, "classpath:ldap-server.ldif")
      c.applicationContext = ctx
      c.port = 0

      return c
    }

    @Bean
    @Primary
    @ConfigurationProperties("ldap")
    LdapConfigProps ldapConfigProps(UnboundIdContainer ldapServer) {
      def cfg = new LdapConfigProps()
      cfg.url = "ldap://127.0.0.1:$ldapServer.port/$DEFAULT_PARTITION_SUFFIX"
      cfg.userDnPattern = 'uid={0},ou=users'
      return cfg
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
