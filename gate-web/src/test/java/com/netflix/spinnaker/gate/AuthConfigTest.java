/*
 * Copyright 2024 Salesforce, Inc.
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
package com.netflix.spinnaker.gate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.config.AuthConfig;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.security.basic.BasicAuthConfig;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import java.io.IOException;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** AuthConfig is in gate-core, but is about matching http requests, so use gate-web to test it. */
@SpringBootTest(
    classes = {Main.class, AuthConfigTest.TestConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "spring.security.user.name=testuser",
      "spring.security.user.password=testpassword",
      "security.basicform.enabled=true"
    })
class AuthConfigTest {

  private static final String TEST_USER = "testuser";

  private static final String TEST_PASSWORD = "testpassword";

  private static final ParameterizedTypeReference<Map<String, String>> mapType =
      new ParameterizedTypeReference<>() {};

  @Autowired TestRestTemplate restTemplate;

  @Autowired ObjectMapper objectMapper;

  /** To prevent periodic calls to service's /health endpoints */
  @MockBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  /** to prevent period application loading */
  @MockBean ApplicationService applicationService;

  /** To prevent attempts to load accounts */
  @MockBean DefaultProviderLookupService defaultProviderLookupService;

  @BeforeEach
  void init(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
  }

  @Test
  void forwardNoCredsRequiresAuth() {
    final ResponseEntity<Map<String, String>> response =
        restTemplate.exchange("/forward", HttpMethod.GET, null, mapType);

    // Without .antMatchers("/error").permitAll() in AuthConfig, we'd expect to
    // get an empty error response since the request is unauthorized.
    // https://github.com/spring-projects/spring-boot/issues/26356 has details.

    // Leave this test here in case someone gets the urge to restrict access to /error.
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().get("timestamp")).isNotNull();
    assertThat(response.getBody().get("status"))
        .isEqualTo(String.valueOf(HttpStatus.UNAUTHORIZED.value()));
    assertThat(response.getBody().get("error"))
        .isEqualTo(HttpStatus.UNAUTHORIZED.getReasonPhrase());
    assertThat(response.getBody().get("message"))
        .isEqualTo(HttpStatus.UNAUTHORIZED.getReasonPhrase());
  }

  @Test
  void forwardWrongCredsRequiresAuth() {
    final ResponseEntity<Map<String, String>> response =
        restTemplate
            .withBasicAuth(TEST_USER, "wrong" + TEST_PASSWORD)
            .exchange("/forward", HttpMethod.GET, null, mapType);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().get("timestamp")).isNotNull();
    assertThat(response.getBody().get("status"))
        .isEqualTo(String.valueOf(HttpStatus.UNAUTHORIZED.value()));
    assertThat(response.getBody().get("error"))
        .isEqualTo(HttpStatus.UNAUTHORIZED.getReasonPhrase());
    assertThat(response.getBody().get("message"))
        .isEqualTo(HttpStatus.UNAUTHORIZED.getReasonPhrase());
  }

  @Test
  void forwardWithCorrectCreds() {
    final ResponseEntity<Object> response =
        restTemplate
            .withBasicAuth(TEST_USER, TEST_PASSWORD)
            .exchange("/forward", HttpMethod.GET, null, Object.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
    assertThat(response.getHeaders().getLocation().getPath()).isEqualTo("/hello");
    assertThat(response.getBody()).isNull();
  }

  static class TestAuthConfig extends BasicAuthConfig {
    public TestAuthConfig(
        AuthConfig authConfig,
        SecurityProperties securityProperties,
        DefaultCookieSerializer defaultCookieSerializer) {
      super(authConfig, securityProperties, defaultCookieSerializer);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
      // This is the same as BasicAuthConfig except for
      //
      // authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
      //
      // Leaving that out makes it easier to test some behavior of AuthConfig.
      defaultCookieSerializer.setSameSite(null);
      http.formLogin().and().httpBasic();
      authConfig.configure(http);
    }
  }

  @Configuration
  static class TestConfiguration {
    @RestController
    public static class TestController {
      @GetMapping("/forward")
      public void forward(HttpServletResponse response) throws IOException {
        response.sendRedirect("/hello");
      }

      @GetMapping("/hello")
      public String hello() {
        return "hello";
      }
    }

    @Bean
    @Primary
    BasicAuthConfig basicAuthConfig(
        AuthConfig autoConfig,
        SecurityProperties securityProperties,
        DefaultCookieSerializer defaultCookieSerializer) {
      return new TestAuthConfig(autoConfig, securityProperties, defaultCookieSerializer);
    }
  }
}
