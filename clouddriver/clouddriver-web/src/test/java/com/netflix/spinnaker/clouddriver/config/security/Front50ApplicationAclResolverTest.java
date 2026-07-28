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

package com.netflix.spinnaker.clouddriver.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import retrofit2.mock.Calls;

class Front50ApplicationAclResolverTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private Front50ApplicationAclResolver resolver(Front50Service front50Service) {
    return new Front50ApplicationAclResolver(front50Service, objectMapper);
  }

  @Test
  void resolvesApplicationAclFromFront50() {
    Front50Service front50 = mock(Front50Service.class);
    Map<String, Object> record =
        Map.of("name", "service-template", "permissions", Map.of("READ", List.of("svc")));
    when(front50.getApplicationPermission("service-template", true))
        .thenReturn(Calls.response(record));

    Permissions acl = resolver(front50).resolve(ResourceType.APPLICATION, "service-template");

    assertThat(acl).isNotNull();
    assertThat(acl.getAuthorizations(List.of("svc"))).contains(Authorization.READ);
    assertThat(acl.getAuthorizations(List.of("other"))).isEmpty();
  }

  @Test
  void applicationWithEmptyPermissionsIsUnrestricted() {
    Front50Service front50 = mock(Front50Service.class);
    when(front50.getApplicationPermission("open-app", true))
        .thenReturn(Calls.response(Map.of("name", "open-app", "permissions", Map.of())));

    Permissions acl = resolver(front50).resolve(ResourceType.APPLICATION, "open-app");

    assertThat(acl).isEqualTo(Permissions.EMPTY);
    assertThat(acl.isRestricted()).isFalse();
  }

  @Test
  void unknownApplicationResolvesToNull() {
    Front50Service front50 = mock(Front50Service.class);
    SpinnakerHttpException notFound = mock(SpinnakerHttpException.class);
    when(notFound.getResponseCode()).thenReturn(404);
    when(front50.getApplicationPermission("ghost", true)).thenThrow(notFound);

    assertThat(resolver(front50).resolve(ResourceType.APPLICATION, "ghost")).isNull();
  }

  @Test
  void requestsTheEffectiveAclSoFront50AppliesGlobalDefaults() {
    // Front50 owns authz.application.default-permissions and folds them in, including for an
    // application with no ACL of its own; Clouddriver keeps no copy of that config to drift from.
    Front50Service front50 = mock(Front50Service.class);
    when(front50.getApplicationPermission("ghost", true))
        .thenReturn(
            Calls.response(
                Map.of("name", "ghost", "permissions", Map.of("READ", List.of("default-role")))));

    Permissions acl = resolver(front50).resolve(ResourceType.APPLICATION, "ghost");

    verify(front50).getApplicationPermission("ghost", true);
    assertThat(acl).isNotNull();
    assertThat(acl.getAuthorizations(List.of("default-role"))).contains(Authorization.READ);
  }

  @Test
  void returnsNullWhenFront50Disabled() {
    Front50ApplicationAclResolver resolver = resolver(null);
    assertThat(resolver.resolve(ResourceType.APPLICATION, "anything")).isNull();
  }

  @Test
  void ignoresNonApplicationResourceTypes() {
    Front50Service front50 = mock(Front50Service.class);
    assertThat(resolver(front50).resolve(ResourceType.ACCOUNT, "prod")).isNull();
  }
}
