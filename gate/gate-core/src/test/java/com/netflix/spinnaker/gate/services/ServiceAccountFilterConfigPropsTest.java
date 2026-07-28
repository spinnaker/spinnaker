/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.gate.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.netflix.spinnaker.security.authz.Authorization;
import java.util.EnumSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/** Verifies the dual-prefix binding (canonical {@code authz.*} + deprecated {@code fiat.*}). */
class ServiceAccountFilterConfigPropsTest {

  private static StandardEnvironment environmentWith(Map<String, Object> properties) {
    StandardEnvironment environment = new StandardEnvironment();
    environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
    return environment;
  }

  @Test
  void usesDefaultsWhenNeitherKeyIsSet() {
    ServiceAccountFilterConfigProps props =
        ServiceAccountFilterConfigProps.bind(new StandardEnvironment());

    assertTrue(props.isEnabled());
    assertThat(props.getMatchAuthorizations())
        .containsExactlyInAnyOrder(Authorization.WRITE, Authorization.EXECUTE);
  }

  @Test
  void bindsCanonicalAuthzKey() {
    ServiceAccountFilterConfigProps props =
        ServiceAccountFilterConfigProps.bind(
            environmentWith(
                Map.of(
                    "authz.service-accounts.filter.enabled", "false",
                    "authz.service-accounts.filter.match-authorizations", "READ,WRITE")));

    assertFalse(props.isEnabled());
    assertThat(props.getMatchAuthorizations())
        .containsExactlyInAnyOrder(Authorization.READ, Authorization.WRITE);
  }

  @Test
  void bindsLegacyFiatKeyForBackCompat() {
    ServiceAccountFilterConfigProps props =
        ServiceAccountFilterConfigProps.bind(
            environmentWith(
                Map.of(
                    "fiat.service-accounts.filter.enabled", "false",
                    "fiat.service-accounts.filter.match-authorizations", "CREATE")));

    assertFalse(props.isEnabled());
    assertThat(props.getMatchAuthorizations()).containsExactly(Authorization.CREATE);
  }

  @Test
  void canonicalAuthzKeyTakesPrecedenceOverLegacyFiatKey() {
    ServiceAccountFilterConfigProps props =
        ServiceAccountFilterConfigProps.bind(
            environmentWith(
                Map.of(
                    "authz.service-accounts.filter.enabled", "true",
                    "authz.service-accounts.filter.match-authorizations", "READ",
                    "fiat.service-accounts.filter.enabled", "false",
                    "fiat.service-accounts.filter.match-authorizations", "CREATE")));

    assertTrue(props.isEnabled());
    assertEquals(EnumSet.of(Authorization.READ), EnumSet.copyOf(props.getMatchAuthorizations()));
  }
}
