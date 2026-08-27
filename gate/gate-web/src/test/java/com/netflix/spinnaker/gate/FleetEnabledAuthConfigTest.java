/*
 * Copyright 2026 Harness, Inc.
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

import com.netflix.spinnaker.gate.filters.FleetDirectAccessFilter;
import com.netflix.spinnaker.gate.health.DownstreamServicesHealthIndicator;
import com.netflix.spinnaker.gate.services.ApplicationService;
import com.netflix.spinnaker.gate.services.DefaultProviderLookupService;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Verifies that opting in to {@code fleet.enabled} actually installs {@link
 * FleetDirectAccessFilter}, and installs it late enough in the chain to see an authenticated
 * principal. {@code AuthConfigTest#fleetFilterAbsentByDefault} covers the disabled default.
 *
 * <p>Filter behaviour itself is covered by {@code FleetDirectAccessFilterTest} in gate-core; this
 * test is deliberately limited to wiring, which is the part that can silently regress.
 */
@SpringBootTest(
    classes = {Main.class, AuthConfigTest.TestConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(
    properties = {
      "spring.config.location=classpath:gate-test.yml",
      "spring.security.user.name=testuser",
      "spring.security.user.password=testpassword",
      "security.basicform.enabled=true",
      "fleet.enabled=true",
      "fleet.global-base-url=https://spinnaker.example.com",
      "fleet.instance-id=inst-1"
    })
class FleetEnabledAuthConfigTest {

  /** To prevent periodic calls to service's /health endpoints */
  @MockitoBean DownstreamServicesHealthIndicator downstreamServicesHealthIndicator;

  /** to prevent period application loading */
  @MockitoBean ApplicationService applicationService;

  /** To prevent attempts to load accounts */
  @MockitoBean DefaultProviderLookupService defaultProviderLookupService;

  @Autowired List<SecurityFilterChain> securityFilterChains;

  @Test
  void fleetFilterIsRegisteredWhenEnabled() {
    assertThat(fleetFilterChain()).isNotNull();
  }

  /**
   * The guardrail needs the populated SecurityContext to tell an admin from a non-admin, so it must
   * run after {@link AuthorizationFilter}. If it drifted earlier, every session would look
   * unauthenticated and the filter would silently never fire.
   */
  @Test
  void fleetFilterRunsAfterAuthorizationFilter() {
    List<Filter> filters = fleetFilterChain();

    int authorizationFilterIndex = indexOf(filters, AuthorizationFilter.class);
    int fleetFilterIndex = indexOf(filters, FleetDirectAccessFilter.class);

    assertThat(authorizationFilterIndex).isNotNegative();
    assertThat(fleetFilterIndex).isGreaterThan(authorizationFilterIndex);
  }

  private List<Filter> fleetFilterChain() {
    return securityFilterChains.stream()
        .map(SecurityFilterChain::getFilters)
        .filter(filters -> indexOf(filters, FleetDirectAccessFilter.class) >= 0)
        .findFirst()
        .orElse(null);
  }

  private static int indexOf(List<Filter> filters, Class<? extends Filter> type) {
    for (int i = 0; i < filters.size(); i++) {
      if (type.isInstance(filters.get(i))) {
        return i;
      }
    }
    return -1;
  }
}
