/*
 * Copyright 2025 Harness, Inc.
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
package com.netflix.spinnaker.gate.security.saml;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SecuritySamlPropertiesTest {

  @Test
  void defaultUserAttributeMappingValuesAreSet() {
    SecuritySamlProperties properties = new SecuritySamlProperties();
    var mapping = properties.getUserAttributeMapping();
    assertThat(mapping.getFirstName()).isEqualTo("User.FirstName");
    assertThat(mapping.getLastName()).isEqualTo("User.LastName");
    assertThat(mapping.getRoles()).isEqualTo("memberOf");
    assertThat(mapping.getRolesDelimiter()).isEqualTo(";");
  }

  @Test
  void defaultRoleBehaviourFlags() {
    SecuritySamlProperties properties = new SecuritySamlProperties();
    assertThat(properties.isForceLowercaseRoles()).isTrue();
    assertThat(properties.isSortRoles()).isFalse();
  }

  @Test
  void requiredRolesIsNullByDefault() {
    SecuritySamlProperties properties = new SecuritySamlProperties();
    assertThat(properties.getRequiredRoles()).isNull();
  }

  @Test
  void requiredRolesCanBeSet() {
    SecuritySamlProperties properties = new SecuritySamlProperties();
    properties.setRequiredRoles(List.of("admin", "operator"));
    assertThat(properties.getRequiredRoles()).containsExactly("admin", "operator");
  }
}
