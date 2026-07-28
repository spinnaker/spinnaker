/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.front50.config.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.netflix.spinnaker.security.authz.filter.ApiTokenExchangeProperties;
import com.netflix.spinnaker.security.s2s.ServiceCallerContext;
import com.netflix.spinnaker.security.s2s.config.ServiceToServiceAuthConfiguration;
import com.netflix.spinnaker.security.s2s.filter.ServiceCallerAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationConverter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end verification that {@link ServiceCallerAuthenticationFilter} actually runs inside
 * Front50's real Spring Security {@link FilterChainProxy} — i.e. that {@link ServiceCallerContext}
 * is populated by the time a controller executes — rather than relying on Spring Boot's implicit
 * orphan-filter registration (which does not guarantee the filter is invoked within the security
 * chain).
 *
 * <p>Drives requests through the exact {@link Front50SecurityConfig#front50SecurityFilterChain}
 * wiring so a regression that stops explicitly adding the filter to the chain fails this test.
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(classes = Front50SecurityConfigServiceCallerFilterTest.TestConfig.class)
@TestPropertySource(properties = {"authz.s2s.enabled=true", "authz.s2s.provider=HEADER"})
class Front50SecurityConfigServiceCallerFilterTest {

  @Autowired private WebApplicationContext context;
  @Autowired private FilterChainProxy springSecurityFilterChain;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    // Only the real Spring Security FilterChainProxy is installed — no standalone servlet filters —
    // so a caller only appears in ServiceCallerContext if the filter is wired into the chain.
    mockMvc =
        MockMvcBuilders.webAppContextSetup(context).addFilters(springSecurityFilterChain).build();
  }

  @Test
  void populatesServiceCallerContextThroughTheRealChain() throws Exception {
    mockMvc
        .perform(
            get("/test/service-caller")
                .header("X-Forwarded-Client-Cert", "URI=spiffe://td/ns/spinnaker/sa/orca"))
        .andExpect(status().isOk())
        .andExpect(content().string("ORCA"));
  }

  @Test
  void noServiceCallerWhenTransportIdentityAbsent() throws Exception {
    mockMvc
        .perform(get("/test/service-caller"))
        .andExpect(status().isOk())
        .andExpect(content().string("none"));
  }

  @Configuration
  @EnableWebSecurity
  @Import(ServiceToServiceAuthConfiguration.class)
  @EnableConfigurationProperties(ApiTokenExchangeProperties.class)
  static class TestConfig {

    /**
     * The real production filter-chain wiring under test. Reuses {@link
     * Front50SecurityConfig#front50SecurityFilterChain} verbatim so this test breaks if the {@link
     * ServiceCallerAuthenticationFilter} stops being added to the chain.
     */
    @Bean
    SecurityFilterChain front50SecurityFilterChain(
        HttpSecurity http,
        ServiceCallerAuthenticationFilter serviceCallerAuthenticationFilter,
        ApiTokenExchangeProperties apiTokenExchangeProperties)
        throws Exception {
      // No inbound identity token in this test; the converter yields no authentication.
      AuthenticationConverter noIdentityToken = request -> null;
      return new Front50SecurityConfig()
          .front50SecurityFilterChain(
              http,
              noIdentityToken,
              serviceCallerAuthenticationFilter,
              apiTokenExchangeProperties,
              "");
    }

    @Bean
    ServiceCallerProbeController serviceCallerProbeController() {
      return new ServiceCallerProbeController();
    }
  }

  /** Reports the service caller the filter published for the current request, if any. */
  @RestController
  static class ServiceCallerProbeController {
    @GetMapping("/test/service-caller")
    String serviceCaller() {
      return ServiceCallerContext.current().map(c -> c.service().name()).orElse("none");
    }
  }
}
