/*
 * Copyright 2026 Salesforce, Inc.
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

package com.netflix.spinnaker.kork.web.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import com.netflix.spinnaker.config.ErrorConfiguration;
import com.netflix.spinnaker.kork.test.log.MemoryAppender;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Test the HTTP response format when controllers throw {@link ResponseStatusException}. */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {
      ErrorConfiguration.class,
      BaseExceptionHandlersTest.TestControllerConfiguration.class
    })
class BaseExceptionHandlersTest {

  private static final String REASON = "something went wrong";

  private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
      new ParameterizedTypeReference<>() {};

  @Configuration
  @EnableAutoConfiguration
  static class TestControllerConfiguration {
    @EnableWebSecurity
    static class WebSecurityConfig implements WebMvcConfigurer {
      @Bean
      protected SecurityFilterChain configure(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable()).headers(headers -> headers.disable());
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
      }
    }

    @Bean
    TestController testController() {
      return new TestController();
    }
  }

  @RestController
  static class TestController {
    @GetMapping("/responseStatusException/{status}")
    void responseStatusException(
        @PathVariable int status, @RequestParam(required = false) String reason) {
      throw new ResponseStatusException(status, reason, null);
    }
  }

  @Autowired private TestRestTemplate restTemplate;

  private MemoryAppender memoryAppender;

  @BeforeEach
  void setUp(TestInfo testInfo) {
    System.out.println("--------------- Test " + testInfo.getDisplayName());
    memoryAppender = new MemoryAppender(BaseExceptionHandlers.class);
  }

  @ParameterizedTest(name = "testResponseStatusException status = {0}")
  @ValueSource(ints = {400, 403, 500})
  void testResponseStatusException(int status) {
    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange(
            "/responseStatusException/" + status + "?reason=" + REASON,
            HttpMethod.GET,
            null,
            MAP_TYPE);
    assertThat(response.getStatusCode().value()).isEqualTo(status);
    assertThat(response.getBody())
        .containsKey("timestamp")
        .containsEntry("status", status)
        .containsEntry("error", HttpStatus.valueOf(status).getReasonPhrase())
        .containsEntry("exception", "org.springframework.web.server.ResponseStatusException")
        .containsEntry("message", REASON);

    assertThat(memoryAppender.countEventsForLevel(Level.ERROR)).isEqualTo(1);
  }

  @Test
  void testResponseStatusException404() {
    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange(
            "/responseStatusException/404?reason=" + REASON, HttpMethod.GET, null, MAP_TYPE);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody())
        .containsKey("timestamp")
        .containsEntry("status", 404)
        .containsEntry("error", "Not Found")
        .containsEntry("exception", "org.springframework.web.server.ResponseStatusException")
        .containsEntry("message", REASON);

    assertThat(memoryAppender.countEventsForLevel(Level.ERROR)).isZero();
  }

  @Test
  void testResponseStatusExceptionNullReason() {
    ResponseEntity<Map<String, Object>> response =
        restTemplate.exchange("/responseStatusException/500", HttpMethod.GET, null, MAP_TYPE);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody())
        .containsKey("timestamp")
        .containsEntry("status", 500)
        .containsEntry("error", "Internal Server Error")
        .containsEntry("exception", "org.springframework.web.server.ResponseStatusException")
        .containsEntry("message", "500 INTERNAL_SERVER_ERROR");

    assertThat(memoryAppender.countEventsForLevel(Level.ERROR)).isEqualTo(1);
  }
}
