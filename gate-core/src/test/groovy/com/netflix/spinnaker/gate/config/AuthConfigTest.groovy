/*
 * Copyright 2021 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.gate.config
import com.netflix.spinnaker.fiat.shared.FiatClientConfigurationProperties
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator
import com.netflix.spinnaker.fiat.shared.FiatStatus
import org.springframework.boot.autoconfigure.security.SecurityProperties
import org.springframework.security.config.annotation.ObjectPostProcessor
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.util.matcher.AnyRequestMatcher
import spock.lang.Specification
import java.util.stream.Collectors

class AuthConfigTest extends Specification {
  @SuppressWarnings("GroovyAccessibility")
  def "test webhooks are unauthenticated by default"() {
    given:
    def requestMatcher = AnyRequestMatcher.INSTANCE
    def mockRequestMatcherProvider = Mock(RequestMatcherProvider) {
      requestMatcher() >> requestMatcher
    }
    def authConfig = new AuthConfig(
      permissionRevokingLogoutSuccessHandler: Mock(AuthConfig.PermissionRevokingLogoutSuccessHandler),
      securityProperties: Mock(SecurityProperties),
      configProps: Mock(FiatClientConfigurationProperties),
      fiatStatus: Mock(FiatStatus),
      permissionEvaluator: Mock(FiatPermissionEvaluator),
      requestMatcherProvider: mockRequestMatcherProvider,
      securityDebug: false,
      fiatSessionFilterEnabled: false,
    )
    def httpSecurity = new HttpSecurity(
      Mock(ObjectPostProcessor),
      Mock(AuthenticationManagerBuilder),
      new HashMap<Class<?, Object>>()
    )

    when:
    authConfig.configure(httpSecurity)

    then:
    def filtered = httpSecurity.authorizeRequests().getUrlMappings()
      .stream()
      .filter({ it -> it.requestMatcher.getPattern() == "/webhooks/**" })
      .filter( { it ->
        it.configAttrs.stream().any( {att -> att.getAttribute() == "authenticated" })
      })
      .collect(Collectors.toList())
    filtered.size() == 0
  }

  @SuppressWarnings("GroovyAccessibility")
  def "test webhooks can be configured to be authenticated"() {
    given:
    def requestMatcher = AnyRequestMatcher.INSTANCE
    def mockRequestMatcherProvider = Mock(RequestMatcherProvider) {
      requestMatcher() >> requestMatcher
    }
    def authConfig = new AuthConfig(
      permissionRevokingLogoutSuccessHandler: Mock(AuthConfig.PermissionRevokingLogoutSuccessHandler),
      securityProperties: Mock(SecurityProperties),
      configProps: Mock(FiatClientConfigurationProperties),
      fiatStatus: Mock(FiatStatus),
      permissionEvaluator: Mock(FiatPermissionEvaluator),
      requestMatcherProvider: mockRequestMatcherProvider,
      securityDebug: false,
      fiatSessionFilterEnabled: false,
      webhookDefaultAuthEnabled: true,
    )
    def httpSecurity = new HttpSecurity(
      Mock(ObjectPostProcessor),
      Mock(AuthenticationManagerBuilder),
      new HashMap<Class<?, Object>>()
    )

    when:
    authConfig.configure(httpSecurity)

    then:
    def filtered = httpSecurity.authorizeRequests().getUrlMappings()
      .stream()
      .filter({ it -> it.requestMatcher.getPattern() == "/webhooks/**" })
      .filter( { it ->
        it.configAttrs.stream().any( {att -> att.getAttribute() == "authenticated"})
      })
      .collect(Collectors.toList())
    filtered.size() == 1
  }
}
