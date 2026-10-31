/*
 * Copyright 2026 spinnaker.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
import com.netflix.spinnaker.gate.config.GateConfig
import com.netflix.spinnaker.gate.config.RedisTestConfig
import com.netflix.spinnaker.gate.security.YamlFileApplicationContextInitializer
import com.netflix.spinnaker.gate.services.ApplicationService
import org.spockframework.spring.SpringBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import spock.lang.Specification

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Runs against an embedded server, which is what Gate runs on in production. MockMvc contexts
 * are treated as war deployments by Spring Boot, where the session cookie properties do not
 * apply, so they cannot show what a browser receives.
 */
@DirtiesContext
@SpringBootTest(
  webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties = ["fiat.enabled=false"])
@ContextConfiguration(
  classes = [Main, GateConfig, BasicAuthConfig, BasicAuthSpec.BasicTestConfig, RedisTestConfig],
  initializers = YamlFileApplicationContextInitializer
)
@TestPropertySource("/basic-auth.properties")
class BasicAuthSessionCookieSpec extends Specification {

  /** Prevents the background applications cache refresh from calling clouddriver and front50. */
  @SpringBean ApplicationService applicationService = Mock()

  @LocalServerPort
  int port

  def "should issue a session cookie that omits SameSite by default"() {
    when:
    def setCookie = sessionSetCookie()

    then:
    setCookie.startsWith("SESSION=")
    setCookie.contains("HttpOnly")
    !setCookie.contains("SameSite")
  }

  private String sessionSetCookie() {
    def response = HttpClient.newHttpClient().send(
      HttpRequest.newBuilder(URI.create("http://localhost:${port}/credentials")).GET().build(),
      HttpResponse.BodyHandlers.discarding())
    return response.headers().allValues("Set-Cookie").find { it.startsWith("SESSION=") }
  }
}
