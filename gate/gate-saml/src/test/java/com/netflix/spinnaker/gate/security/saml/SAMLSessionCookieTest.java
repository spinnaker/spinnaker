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

package com.netflix.spinnaker.gate.security.saml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.netflix.spinnaker.gate.config.AuthConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

class SAMLSessionCookieTest {

  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withUserConfiguration(SAMLConfiguration.class, TestDependencies.class)
          .withPropertyValues("saml.enabled=true");

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void preservesLegacyDefaultsWhenCookiePropertiesAreUnspecified(boolean secureRequest) {
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

  @Test
  void honorsExplicitOmittedSameSite() {
    runner
        .withPropertyValues("server.servlet.session.cookie.same-site=omitted")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(cookieHeader(context, false)).doesNotContain("SameSite=");
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

  @Test
  void doesNotCreateSamlCookieSerializerWhenSamlIsDisabled() {
    runner
        .withPropertyValues("saml.enabled=false")
        .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(CookieSerializer.class));
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

  @TestConfiguration(proxyBeanMethods = false)
  static class TestDependencies {
    @Bean
    AuthConfig authConfig() {
      return mock(AuthConfig.class);
    }

    @Bean
    RelyingPartyRegistrationRepository registrations() {
      return mock(RelyingPartyRegistrationRepository.class);
    }
  }
}
