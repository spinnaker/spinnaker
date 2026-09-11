/*
 * Copyright 2026 Netflix, Inc.
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
package com.netflix.spinnaker.front50.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.config.Front50CoreConfiguration
import com.netflix.spinnaker.front50.config.controllers.PipelineControllerConfig
import com.netflix.spinnaker.front50.model.pipeline.PipelineDAO
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification
import spock.mock.DetachedMockFactory

import jakarta.servlet.http.HttpServletResponse

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get

/**
 * Verifies PipelineController's @PostFilter expressions through Spring's real method security
 * interceptor, rather than only asserting the annotation's SpEL string (as PipelineControllerSpec
 * does). Kept in its own Spring context, separate from PipelineControllerSpec, because enabling
 * method security there would newly enforce @PreAuthorize on save() for that spec's tests that
 * exercise the autowired, container-managed MockMvc, which don't set up an Authentication or stub
 * fiatPermissionEvaluator for that purpose.
 *
 * https://github.com/spinnaker/spinnaker/issues/7940
 */
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(controllers = [PipelineController])
@ContextConfiguration(classes = [
    TestConfiguration, AuthorizationSupport, PipelineController, PipelineControllerConfig,
    Front50CoreConfiguration, MethodSecurityConfiguration
])
class PipelineControllerSecuritySpec extends Specification {

  @Autowired
  private MockMvc mockMvc

  @Autowired
  PipelineDAO pipelineDAO

  @Autowired
  FiatPermissionEvaluator fiatPermissionEvaluator

  def setup() {
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("test-user", null, []))
  }

  def cleanup() {
    SecurityContextHolder.clearContext()
  }

  def "list only returns pipelines from applications the caller can READ, filtered by application rather than pipeline name"() {
    given: "a visible and a hidden pipeline, with name/application deliberately swapped so filtering on the wrong field would let the hidden one through"
    def visiblePipeline = new Pipeline([id: "1", name: "hidden-app", application: "visible-app"])
    def hiddenPipeline = new Pipeline([id: "2", name: "visible-app", application: "hidden-app"])

    pipelineDAO.all(true) >> [visiblePipeline, hiddenPipeline]

    fiatPermissionEvaluator.storeWholePermission() >> true
    fiatPermissionEvaluator.hasPermission(_, "visible-app", "APPLICATION", "READ") >> true
    fiatPermissionEvaluator.hasPermission(_, "hidden-app", "APPLICATION", "READ") >> false

    when:
    HttpServletResponse response = mockMvc.perform(get("/pipelines")).andReturn().response

    then:
    response.status == 200
    response.contentAsString.contains('"id":"1"')
    !response.contentAsString.contains('"id":"2"')
  }

  def "list returns nothing when the caller has no application read permissions"() {
    given:
    def pipeline = new Pipeline([id: "1", name: "some-name", application: "some-app"])

    pipelineDAO.all(true) >> [pipeline]

    fiatPermissionEvaluator.storeWholePermission() >> true
    fiatPermissionEvaluator.hasPermission(_, "some-app", "APPLICATION", "READ") >> false

    when:
    HttpServletResponse response = mockMvc.perform(get("/pipelines")).andReturn().response

    then:
    response.status == 200
    response.contentAsString == "[]"
  }

  @Configuration
  private static class TestConfiguration {
    DetachedMockFactory detachedMockFactory = new DetachedMockFactory()

    @Bean
    PipelineDAO pipelineDAO() {
      detachedMockFactory.Stub(PipelineDAO)
    }

    @Bean
    FiatPermissionEvaluator fiatPermissionEvaluator() {
      detachedMockFactory.Mock(FiatPermissionEvaluator)
    }

    @Bean
    ObjectMapper objectMapper() {
      new ObjectMapper()
    }
  }

  @Configuration
  @EnableMethodSecurity
  private static class MethodSecurityConfiguration {
    @Bean
    static MethodSecurityExpressionHandler methodSecurityExpressionHandler(PermissionEvaluator permissionEvaluator) {
      def handler = new DefaultMethodSecurityExpressionHandler()
      handler.setPermissionEvaluator(permissionEvaluator)
      return handler
    }
  }
}
