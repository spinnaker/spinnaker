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

package com.netflix.spinnaker.security.authz.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ApplicationDefaultPermissionsPropertiesTest {

  @Test
  void unconfiguredIsInert() {
    ApplicationDefaultPermissionsProperties props = new ApplicationDefaultPermissionsProperties();

    assertThat(props.toPermissions()).isEqualTo(Permissions.EMPTY);
    assertThat(props.toPermissions().isRestricted()).isFalse();
    assertThat(props.getCreateRoles()).isEmpty();
  }

  @Test
  void toPermissionsSanitizesAndLowercasesRoles() {
    ApplicationDefaultPermissionsProperties props = new ApplicationDefaultPermissionsProperties();
    Map<Authorization, Set<String>> config = new LinkedHashMap<>();
    config.put(Authorization.READ, Set.of("  Spinnaker-SAs  "));
    config.put(Authorization.WRITE, Set.of("Spinnaker-SAs"));
    props.setDefaultPermissions(config);

    Permissions permissions = props.toPermissions();

    assertThat(permissions.isRestricted()).isTrue();
    assertThat(permissions.get(Authorization.READ)).containsExactly("spinnaker-sas");
    assertThat(permissions.get(Authorization.WRITE)).containsExactly("spinnaker-sas");
  }

  @Test
  void getCreateRolesReturnsCreateEntry() {
    ApplicationDefaultPermissionsProperties props = new ApplicationDefaultPermissionsProperties();
    Map<Authorization, Set<String>> config = new LinkedHashMap<>();
    config.put(Authorization.CREATE, Set.of("creators", "ADMINS-GROUP"));
    config.put(Authorization.READ, Set.of("readers"));
    props.setDefaultPermissions(config);

    assertThat(props.getCreateRoles()).containsExactlyInAnyOrder("creators", "admins-group");
  }

  @Test
  void setNullDefaultsToEmpty() {
    ApplicationDefaultPermissionsProperties props = new ApplicationDefaultPermissionsProperties();
    props.setDefaultPermissions(null);

    assertThat(props.getDefaultPermissions()).isEmpty();
    assertThat(props.toPermissions()).isEqualTo(Permissions.EMPTY);
  }
}
