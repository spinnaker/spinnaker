/*
 * Copyright 2025 OpsMx, Inc.
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
package com.netflix.spinnaker.orca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spinnaker.fiat.shared.AuthenticatedRequestAuthenticationConverter;
import com.netflix.spinnaker.fiat.shared.FiatAuthenticationFilter;
import com.netflix.spinnaker.filters.AuthenticatedRequestFilter;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@ContextConfiguration(
    classes = {
      FiatAuthenticationFilterIT.TestController.class,
      FiatAuthenticationFilterIT.TestSecurityConfig.class
    })
@AutoConfigureMockMvc
class FiatAuthenticationFilterIT {

  private static final String USER = "test-user";

  @Nested
  @SpringBootTest(properties = {"spring.application.name=orcaTest", "services.fiat.enabled=true"})
  class WhenFiatEnabled {

    @Autowired MockMvc mockMvc;

    @Test
    void authenticatedUserShouldBeReturned() throws Exception {
      mockMvc
          .perform(
              get("/test/security-context")
                  .header("X-SPINNAKER-USER", USER)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(content().string(USER));
    }
  }

  @Nested
  @SpringBootTest(properties = {"spring.application.name=orcaTest", "services.fiat.enabled=false"})
  class WhenFiatDisabled {

    @Autowired MockMvc mockMvc;

    @Test
    void authenticatedUserShouldBeReturned() throws Exception {
      mockMvc
          .perform(
              get("/test/security-context")
                  .header("X-SPINNAKER-USER", USER)
                  .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(content().string(USER));
    }
  }

  @Configuration
  static class TestSecurityConfig {

    @Bean
    SecurityFilterChain testSecurityFilterChain(
        HttpSecurity http, AuthenticationConverter authenticationConverter) throws Exception {

      http.csrf(csrf -> csrf.disable())
          .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
          .addFilterBefore(
              new FiatAuthenticationFilter(authenticationConverter),
              AnonymousAuthenticationFilter.class)
          .addFilterBefore(new AuthenticatedRequestFilter(true), FiatAuthenticationFilter.class);

      return http.build();
    }

    @Bean
    AuthenticationConverter authenticationConverter() {
      return new AuthenticatedRequestAuthenticationConverter();
    }
  }

  @RestController
  static class TestController {

    @GetMapping("/test/security-context")
    String securityContext() {
      Authentication auth = SecurityContextHolder.getContext().getAuthentication();
      return auth != null ? auth.getName() : "null";
    }
  }
}
