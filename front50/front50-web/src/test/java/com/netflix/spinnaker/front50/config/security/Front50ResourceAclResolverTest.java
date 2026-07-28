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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.front50.model.application.Application;
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.serviceaccount.ServiceAccountDAO;
import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceType;
import com.netflix.spinnaker.security.authz.config.ApplicationDefaultPermissionsProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Front50ResourceAclResolverTest {

  @Mock ApplicationPermissionDAO applicationPermissionDAO;
  @Mock ServiceAccountDAO serviceAccountDAO;

  private ApplicationDefaultPermissionsProperties defaults(Map<Authorization, Set<String>> config) {
    ApplicationDefaultPermissionsProperties props = new ApplicationDefaultPermissionsProperties();
    props.setDefaultPermissions(config);
    return props;
  }

  private static Map<Authorization, Set<String>> rwx(String role) {
    Map<Authorization, Set<String>> config = new LinkedHashMap<>();
    config.put(Authorization.READ, Set.of(role));
    config.put(Authorization.WRITE, Set.of(role));
    config.put(Authorization.EXECUTE, Set.of(role));
    return config;
  }

  private Front50ResourceAclResolver resolver(ApplicationDefaultPermissionsProperties props) {
    return new Front50ResourceAclResolver(
        Optional.of(applicationPermissionDAO), Optional.of(serviceAccountDAO), props);
  }

  private static Application.Permission permission(String name, Permissions permissions) {
    Application.Permission perm = new Application.Permission();
    perm.setName(name);
    perm.setPermissions(permissions);
    return perm;
  }

  @Test
  @DisplayName("with no defaults, resolves the application's own ACL unchanged")
  void noDefaultsReturnsOwnAcl() {
    Permissions own = new Permissions.Builder().add(Authorization.READ, "team-a").build();
    when(applicationPermissionDAO.findById("myapp")).thenReturn(permission("myapp", own));

    Front50ResourceAclResolver resolver = resolver(defaults(new LinkedHashMap<>()));

    assertThat(resolver.resolve(ResourceType.APPLICATION, "myapp")).isEqualTo(own);
  }

  @Test
  @DisplayName("with no defaults and no record, returns null (unknown application)")
  void noDefaultsUnknownApplicationReturnsNull() {
    when(applicationPermissionDAO.findById("ghost")).thenReturn(null);

    Front50ResourceAclResolver resolver = resolver(defaults(new LinkedHashMap<>()));

    assertThat(resolver.resolve(ResourceType.APPLICATION, "ghost")).isNull();
  }

  @Test
  @DisplayName("global defaults are additively merged onto the application's own ACL")
  void defaultsMergedWithOwnAcl() {
    Permissions own = new Permissions.Builder().add(Authorization.READ, "team-a").build();
    when(applicationPermissionDAO.findById("myapp")).thenReturn(permission("myapp", own));

    Front50ResourceAclResolver resolver = resolver(defaults(rwx("global")));

    Permissions resolved = resolver.resolve(ResourceType.APPLICATION, "myapp");
    assertThat(resolved).isNotNull();
    assertThat(resolved.get(Authorization.READ)).containsExactlyInAnyOrder("team-a", "global");
    assertThat(resolved.get(Authorization.WRITE)).containsExactly("global");
    assertThat(resolved.get(Authorization.EXECUTE)).containsExactly("global");
  }

  @Test
  @DisplayName("an application with no record still resolves to the global defaults")
  void unknownApplicationResolvesToDefaults() {
    when(applicationPermissionDAO.findById("ghost")).thenReturn(null);

    Front50ResourceAclResolver resolver = resolver(defaults(rwx("global")));

    Permissions resolved = resolver.resolve(ResourceType.APPLICATION, "ghost");
    assertThat(resolved).isNotNull();
    assertThat(resolved.isRestricted()).isTrue();
    assertThat(resolved.get(Authorization.READ)).containsExactly("global");
    assertThat(resolved.get(Authorization.WRITE)).containsExactly("global");
    assertThat(resolved.get(Authorization.EXECUTE)).containsExactly("global");
  }
}
