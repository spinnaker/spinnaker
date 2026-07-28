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

package com.netflix.spinnaker.orca.web.config.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.orca.front50.Front50Service;
import com.netflix.spinnaker.security.authz.Authorization;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceType;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import retrofit2.mock.Calls;

class Front50ApplicationAclResolverTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private Front50ApplicationAclResolver resolver(Front50Service front50Service) {
    return new Front50ApplicationAclResolver(front50Service, objectMapper);
  }

  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  @DisplayName("resolves the application's ACL from Front50")
  void resolvesApplicationAclFromFront50() {
    Front50Service front50 = mock(Front50Service.class);
    when(front50.getApplicationPermission("dd-logme", true))
        .thenReturn(
            Calls.response(
                Map.of("name", "dd-logme", "permissions", Map.of("READ", List.of("team-a")))));

    Permissions acl = resolver(front50).resolve(ResourceType.APPLICATION, "dd-logme");

    assertThat(acl).isNotNull();
    assertThat(acl.getAuthorizations(List.of("team-a"))).contains(Authorization.READ);
    assertThat(acl.getAuthorizations(List.of("team-b"))).isEmpty();
  }

  @Test
  @DisplayName("an application with an empty permissions block is unrestricted")
  void applicationWithEmptyPermissionsIsUnrestricted() {
    Front50Service front50 = mock(Front50Service.class);
    when(front50.getApplicationPermission("open-app", true))
        .thenReturn(Calls.response(Map.of("name", "open-app", "permissions", Map.of())));

    Permissions acl = resolver(front50).resolve(ResourceType.APPLICATION, "open-app");

    assertThat(acl).isEqualTo(Permissions.EMPTY);
    assertThat(acl.isRestricted()).isFalse();
  }

  @Test
  @DisplayName("an application with no permission record resolves to null")
  void unknownApplicationResolvesToNull() {
    Front50Service front50 = mock(Front50Service.class);
    SpinnakerHttpException notFound = mock(SpinnakerHttpException.class);
    when(notFound.getResponseCode()).thenReturn(404);
    when(front50.getApplicationPermission("ghost", true)).thenThrow(notFound);

    assertThat(resolver(front50).resolve(ResourceType.APPLICATION, "ghost")).isNull();
  }

  @Test
  @DisplayName("an unreachable Front50 resolves to null rather than propagating the failure")
  void front50FailureResolvesToNull() {
    Front50Service front50 = mock(Front50Service.class);
    when(front50.getApplicationPermission("dd-logme", true))
        .thenThrow(new IllegalStateException("boom"));

    assertThat(resolver(front50).resolve(ResourceType.APPLICATION, "dd-logme")).isNull();
  }

  @Test
  @DisplayName("asks Front50 for the effective ACL so global defaults are applied by their owner")
  void requestsTheEffectiveAcl() {
    Front50Service front50 = mock(Front50Service.class);
    when(front50.getApplicationPermission("dd-logme", true))
        .thenReturn(
            Calls.response(
                Map.of(
                    "name",
                    "dd-logme",
                    "permissions",
                    Map.of("READ", List.of("team-a", "spin-internal-service-accounts")))));

    Permissions acl = resolver(front50).resolve(ResourceType.APPLICATION, "dd-logme");

    verify(front50).getApplicationPermission("dd-logme", true);
    assertThat(acl).isNotNull();
    assertThat(acl.getAuthorizations(List.of("spin-internal-service-accounts")))
        .contains(Authorization.READ);
  }

  @Test
  @DisplayName("resolves to null when Front50 is disabled")
  void returnsNullWhenFront50Disabled() {
    assertThat(resolver(null).resolve(ResourceType.APPLICATION, "anything")).isNull();
  }

  @Test
  @DisplayName("non-application resource types are not resolved here")
  void ignoresNonApplicationResourceTypes() {
    Front50Service front50 = mock(Front50Service.class);
    assertThat(resolver(front50).resolve(ResourceType.ACCOUNT, "prod")).isNull();
  }

  @Test
  @DisplayName("a @PostFilter over many rows of one application hits Front50 once")
  void readsFront50OncePerApplicationPerRequest() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));

    Front50Service front50 = mock(Front50Service.class);
    when(front50.getApplicationPermission("dd-logme", true))
        .thenAnswer(
            invocation ->
                Calls.response(
                    Map.of("name", "dd-logme", "permissions", Map.of("READ", List.of("team-a")))));

    Front50ApplicationAclResolver resolver = resolver(front50);
    for (int i = 0; i < 50; i++) {
      assertThat(resolver.resolve(ResourceType.APPLICATION, "dd-logme")).isNotNull();
    }

    verify(front50, times(1)).getApplicationPermission("dd-logme", true);
  }
}
