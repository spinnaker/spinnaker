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

package com.netflix.spinnaker.gate.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.session.autoconfigure.SessionAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.tomcat.autoconfigure.servlet.TomcatServletWebServerAutoConfiguration;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.server.servlet.context.AnnotationConfigServletWebServerApplicationContext;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

/**
 * Gate relies on Spring Boot to build the session cookie serializer from {@code
 * server.servlet.session.cookie.*}, with defaults from the {@code application.properties} that
 * ships in gate-web. These tests emit a real {@code Set-Cookie} header so a change to that wiring
 * or to the shipped defaults is noticed.
 */
class SessionCookieConfigurationTest {

  // Boot only applies the cookie properties to an embedded web server context; a mock web context
  // is treated as a war deployment, where the servlet container owns the cookie.
  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner(AnnotationConfigServletWebServerApplicationContext::new)
          .withInitializer(new ConfigDataApplicationContextInitializer())
          .withConfiguration(
              AutoConfigurations.of(
                  TomcatServletWebServerAutoConfiguration.class, SessionAutoConfiguration.class))
          .withPropertyValues("server.port=0")
          .withUserConfiguration(BindServerProperties.class)
          .withBean(SessionRepository.class, () -> mock(SessionRepository.class));

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void omitsSameSiteByDefault(boolean secureRequest) {
    runner.run(
        context -> {
          assertThat(context).hasNotFailed().hasSingleBean(DefaultCookieSerializer.class);
          String cookie = cookieHeader(context, secureRequest);
          assertThat(cookie).startsWith("SESSION=").contains("; Path=/", "; HttpOnly");
          assertThat(cookie).doesNotContain("SameSite=", "Max-Age=", "; Partitioned");
          assertThat(cookie.contains("; Secure")).isEqualTo(secureRequest);
        });
  }

  @ParameterizedTest
  @CsvSource({"none,None", "lax,Lax", "strict,Strict"})
  void honorsExplicitSameSite(String configured, String expected) {
    runner
        .withPropertyValues("server.servlet.session.cookie.same-site=" + configured)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(cookieHeader(context, false))
                  .contains("; SameSite=" + expected)
                  .doesNotContain("; Secure");
            });
  }

  @ParameterizedTest
  @CsvSource({"true,false", "false,true", "true,true", "false,false"})
  void honorsSecureIndependentlyOfSameSiteAndRequest(boolean secureCookie, boolean secureRequest) {
    runner
        .withPropertyValues(
            "server.servlet.session.cookie.same-site=none",
            "server.servlet.session.cookie.secure=" + secureCookie)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              String cookie = cookieHeader(context, secureRequest);
              assertThat(cookie).contains("; SameSite=None", "; HttpOnly");
              assertThat(cookie.contains("; Secure")).isEqualTo(secureCookie);
            });
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void honorsExplicitHttpOnly(boolean httpOnly) {
    runner
        .withPropertyValues("server.servlet.session.cookie.http-only=" + httpOnly)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(cookieHeader(context, false).contains("; HttpOnly")).isEqualTo(httpOnly);
            });
  }

  @Test
  void honorsCookieNameDomainPathMaxAgeAndPartitioned() {
    runner
        .withPropertyValues(
            "server.servlet.session.cookie.name=GATE_SESSION",
            "server.servlet.session.cookie.domain=example.org",
            "server.servlet.session.cookie.path=/auth",
            "server.servlet.session.cookie.max-age=30m",
            "server.servlet.session.cookie.partitioned=true",
            "server.servlet.session.cookie.same-site=none",
            "server.servlet.session.cookie.secure=true")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(cookieHeader(context, false))
                  .startsWith("GATE_SESSION=")
                  .contains(
                      "; Domain=example.org",
                      "; Path=/auth",
                      "; Max-Age=1800",
                      "; Partitioned",
                      "; SameSite=None",
                      "; Secure",
                      "; HttpOnly");
            });
  }

  private static String cookieHeader(ApplicationContext context, boolean secureRequest) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setSecure(secureRequest);
    MockHttpServletResponse response = new MockHttpServletResponse();
    context
        .getBean(DefaultCookieSerializer.class)
        .writeCookieValue(new CookieSerializer.CookieValue(request, response, "test-session"));
    assertThat(response.getHeaders("Set-Cookie")).hasSize(1);
    return response.getHeader("Set-Cookie");
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(ServerProperties.class)
  static class BindServerProperties {}
}
