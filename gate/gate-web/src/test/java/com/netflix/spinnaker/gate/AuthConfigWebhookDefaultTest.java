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
package com.netflix.spinnaker.gate;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.gate.config.AuthConfig;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.security.basic.BasicAuthConfig;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Locks in the default (unauthenticated) posture for {@code POST /webhooks/**}: when {@code
 * security.webhooks.default-auth-enabled} is left at its {@code false} default, {@link AuthConfig}
 * must {@code permitAll()} webhooks so they are reachable without credentials, while other
 * endpoints remain protected. The companion {@code AuthConfigTest} covers the opposite (enabled)
 * posture; this restores the "webhooks are unauthenticated by default" coverage previously in the
 * deleted {@code gate/config/AuthConfigTest.groovy}.
 */
@SpringBootTest(
    classes = {Main.class, AuthConfigWebhookDefaultTest.TestConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "spring.security.user.name=testuser",
      "spring.security.user.password=testpassword",
      "security.basicform.enabled=true"
      // security.webhooks.default-auth-enabled intentionally left unset (defaults to false).
    })
class AuthConfigWebhookDefaultTest {

  @Autowired TestRestTemplate restTemplate;

  @Autowired ObjectMapper objectMapper;

  /** To prevent periodic calls to service's /health endpoints */
  @MockitoBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  /** to prevent period application loading */
  @MockitoBean ApplicationService applicationService;

  /** To prevent attempts to load accounts */
  @MockitoBean DefaultProviderLookupService defaultProviderLookupService;

  @Test
  void webhooksAreUnauthenticatedByDefault() {
    String body = "new message";
    HttpEntity<String> entity = new HttpEntity<>(body);

    ResponseEntity<Object> response =
        restTemplate.exchange("/webhooks/sample", HttpMethod.POST, entity, Object.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isEqualTo(body);
  }

  @Test
  void nonWebhookEndpointsStillRequireAuth() {
    ResponseEntity<Object> response =
        restTemplate.exchange("/protected", HttpMethod.POST, HttpEntity.EMPTY, Object.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  static class TestAuthConfig extends BasicAuthConfig {
    public TestAuthConfig(
        AuthConfig authConfig,
        SecurityProperties securityProperties,
        DefaultCookieSerializer defaultCookieSerializer) {
      super(authConfig, securityProperties, defaultCookieSerializer);
    }

    @Override
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
      defaultCookieSerializer.setSameSite(null);
      http.formLogin(Customizer.withDefaults()).httpBasic(Customizer.withDefaults());
      authConfig.configure(http);
      return http.build();
    }
  }

  @Configuration
  static class TestConfiguration {
    @RestController
    public static class TestController {
      @PostMapping("/webhooks/sample")
      public ResponseEntity<String> webhooks(@RequestBody String message) {
        return new ResponseEntity<>(message, HttpStatus.CREATED);
      }

      @PostMapping("/protected")
      public void protectedEndpoint(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.OK.value());
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
