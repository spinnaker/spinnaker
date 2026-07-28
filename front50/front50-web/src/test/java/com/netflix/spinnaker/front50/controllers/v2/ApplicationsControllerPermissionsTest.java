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

package com.netflix.spinnaker.front50.controllers.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.front50.model.application.Application;
import com.netflix.spinnaker.front50.model.application.ApplicationDAO;
import com.netflix.spinnaker.front50.model.application.ApplicationPermissionDAO;
import com.netflix.spinnaker.front50.model.application.ApplicationService;
import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.config.ApplicationDefaultPermissionsProperties;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.StaticMessageSource;

/**
 * Verifies that the global default application permissions are additively merged into the {@code
 * permissions} embedded in Front50's list and single-application responses (the data Gate and other
 * consumers authorize against).
 */
@ExtendWith(MockitoExtension.class)
class ApplicationsControllerPermissionsTest {

  @Mock ApplicationDAO applicationDAO;
  @Mock ApplicationPermissionDAO applicationPermissionDAO;
  @Mock ApplicationService applicationService;

  private ApplicationsController controller(Map<Authorization, Set<String>> defaults) {
    ApplicationDefaultPermissionsProperties props = new ApplicationDefaultPermissionsProperties();
    props.setDefaultPermissions(defaults);
    return new ApplicationsController(
        new StaticMessageSource(),
        applicationDAO,
        Optional.of(applicationPermissionDAO),
        applicationService,
        props);
  }

  private static Map<Authorization, Set<String>> rwx(String role) {
    Map<Authorization, Set<String>> config = new LinkedHashMap<>();
    config.put(Authorization.READ, Set.of(role));
    config.put(Authorization.WRITE, Set.of(role));
    config.put(Authorization.EXECUTE, Set.of(role));
    return config;
  }

  private static Application app(String name) {
    Application application = new Application();
    application.setName(name);
    return application;
  }

  private static Application.Permission permission(String name, Permissions permissions) {
    Application.Permission perm = new Application.Permission();
    perm.setName(name);
    perm.setPermissions(permissions);
    return perm;
  }

  @SuppressWarnings("unchecked")
  private static Permissions embedded(Application application) {
    return (Permissions) application.details().get("permissions");
  }

  @Test
  @DisplayName("list embeds the global defaults merged with each app's own ACL")
  void listEmbedsMergedDefaults() {
    Permissions own = new Permissions.Builder().add(Authorization.READ, "team-a").build();
    when(applicationDAO.all()).thenReturn(List.of(app("myapp"), app("openapp")));
    when(applicationPermissionDAO.all()).thenReturn(List.of(permission("myapp", own)));

    List<Application> results = controller(rwx("global")).applications(null, true, new HashMap<>());

    Application myapp =
        results.stream().filter(it -> it.getName().equals("MYAPP")).findFirst().orElseThrow();
    Application openapp =
        results.stream().filter(it -> it.getName().equals("OPENAPP")).findFirst().orElseThrow();

    // app with its own ACL: union of own + defaults
    assertThat(embedded(myapp).get(Authorization.READ))
        .containsExactlyInAnyOrder("team-a", "global");
    assertThat(embedded(myapp).get(Authorization.WRITE)).containsExactly("global");
    assertThat(embedded(myapp).get(Authorization.EXECUTE)).containsExactly("global");

    // app with no own ACL: still gets the defaults (now restricted)
    assertThat(embedded(openapp).isRestricted()).isTrue();
    assertThat(embedded(openapp).get(Authorization.READ)).containsExactly("global");
  }

  @Test
  @DisplayName("with no defaults configured, list embeds only restricted apps' own ACLs")
  void listWithoutDefaultsPreservesLegacyBehavior() {
    Permissions own = new Permissions.Builder().add(Authorization.READ, "team-a").build();
    when(applicationDAO.all()).thenReturn(List.of(app("myapp"), app("openapp")));
    when(applicationPermissionDAO.all()).thenReturn(List.of(permission("myapp", own)));

    List<Application> results =
        controller(new LinkedHashMap<>()).applications(null, true, new HashMap<>());

    Application myapp =
        results.stream().filter(it -> it.getName().equals("MYAPP")).findFirst().orElseThrow();
    Application openapp =
        results.stream().filter(it -> it.getName().equals("OPENAPP")).findFirst().orElseThrow();

    assertThat(embedded(myapp)).isEqualTo(own);
    assertThat(openapp.details().get("permissions")).isNull();
  }

  @Test
  @DisplayName("single-app get embeds the global defaults merged with the app's own ACL")
  void getEmbedsMergedDefaults() {
    Permissions own = new Permissions.Builder().add(Authorization.WRITE, "team-a").build();
    Application application = app("myapp");
    when(applicationDAO.findByName("MYAPP")).thenReturn(application);
    when(applicationPermissionDAO.findById("MYAPP")).thenReturn(permission("MYAPP", own));

    Application result = controller(rwx("global")).get("myapp");

    assertThat(embedded(result).get(Authorization.READ)).containsExactly("global");
    assertThat(embedded(result).get(Authorization.WRITE))
        .containsExactlyInAnyOrder("team-a", "global");
    assertThat(embedded(result).get(Authorization.EXECUTE)).containsExactly("global");
  }

  @Test
  @DisplayName("single-app get reports the defaults separately so an editor can tell them apart")
  void getReportsDefaultsSeparately() {
    Permissions own = new Permissions.Builder().add(Authorization.WRITE, "team-a").build();
    Application application = app("myapp");
    when(applicationDAO.findByName("MYAPP")).thenReturn(application);
    when(applicationPermissionDAO.findById("MYAPP")).thenReturn(permission("MYAPP", own));

    Application result = controller(rwx("global")).get("myapp");

    // Without this, an editor reading `permissions` cannot tell "team-a can write" (its own grant,
    // which it can remove) from "global can write" (inherited, which it cannot).
    Permissions defaults = (Permissions) result.details().get("defaultPermissions");
    assertThat(defaults).isEqualTo(new Permissions.Builder().set(rwx("global")).build());
  }

  @Test
  @DisplayName("no defaults configured means no defaults reported")
  void getOmitsDefaultsWhenNoneConfigured() {
    Permissions own = new Permissions.Builder().add(Authorization.WRITE, "team-a").build();
    Application application = app("myapp");
    when(applicationDAO.findByName("MYAPP")).thenReturn(application);
    when(applicationPermissionDAO.findById("MYAPP")).thenReturn(permission("MYAPP", own));

    Application result = controller(Map.of()).get("myapp");

    assertThat(result.details().get("defaultPermissions")).isNull();
  }
}
