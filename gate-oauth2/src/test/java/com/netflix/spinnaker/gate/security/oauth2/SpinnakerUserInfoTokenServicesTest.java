/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.gate.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import retrofit2.mock.Calls;

public class SpinnakerUserInfoTokenServicesTest {

  private SpinnakerUserInfoTokenServices tokenServices;
  private OAuth2SsoConfig.UserInfoRequirements userInfoRequirements;

  @BeforeEach
  public void setUp() {
    userInfoRequirements = new OAuth2SsoConfig.UserInfoRequirements();
    tokenServices =
        new SpinnakerUserInfoTokenServices(
            null,
            null,
            null,
            null,
            userInfoRequirements,
            null,
            null,
            Optional.empty(),
            null,
            null,
            null);
  }

  @Test
  public void shouldEvaluateUserInfoRequirementsAgainstAuthenticationDetails() {
    // No domain restriction, everything should match
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of())).isTrue();
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("hd", "foo.com"))).isTrue();
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("bar", "foo.com"))).isTrue();
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("bar", "bar.com"))).isTrue();

    // Domain restricted but not found
    userInfoRequirements.put("hd", "foo.com");
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of())).isFalse();
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("hd", "foo.com"))).isTrue();
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("bar", "foo.com"))).isFalse();
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("bar", "bar.com"))).isFalse();

    // Domain restricted by regex
    userInfoRequirements.put("hd", "/foo\\.com|bar\\.com/");
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of())).isFalse();
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("hd", "foo.com"))).isTrue();
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("hd", "bar.com"))).isTrue();
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("hd", "baz.com"))).isFalse();
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("bar", "foo.com"))).isFalse();

    // Multiple restriction values
    userInfoRequirements.put("bar", "bar.com");
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("hd", "foo.com"))).isFalse();
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("bar", "bar.com"))).isFalse();
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("hd", "foo.com", "bar", "bar.com")))
        .isTrue();

    // Evaluating a list
    userInfoRequirements.clear();
    userInfoRequirements.put("roles", "expected-role");
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("roles", "expected-role"))).isTrue();
    assertThat(
            tokenServices.hasAllUserInfoRequirements(
                Map.of("roles", List.of("expected-role", "unexpected-role"))))
        .isTrue();
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of())).isFalse();
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("roles", "unexpected-role")))
        .isFalse();
    assertThat(
            tokenServices.hasAllUserInfoRequirements(Map.of("roles", List.of("unexpected-role"))))
        .isFalse();

    // Evaluating a regex in a list
    userInfoRequirements.put("roles", "/^.+_ADMIN$/");
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("roles", "foo_ADMIN"))).isTrue();
    assertThat(tokenServices.hasAllUserInfoRequirements(Map.of("roles", List.of("foo_ADMIN"))))
        .isTrue();
    assertThat(
            tokenServices.hasAllUserInfoRequirements(
                Map.of("roles", List.of("_ADMIN", "foo_USER"))))
        .isFalse();
    assertThat(
            tokenServices.hasAllUserInfoRequirements(
                Map.of("roles", List.of("foo_ADMINISTRATOR", "bar_USER"))))
        .isFalse();
  }

  @Test
  public void verifyIsServiceAccount() {
    PermissionService permissionService = mock(PermissionService.class);
    when(permissionService.isEnabled()).thenReturn(true);

    Front50Service front50Service = mock(Front50Service.class);
    ServiceAccount serviceAccount = new ServiceAccount();
    serviceAccount = serviceAccount.setName("ex@foo.com");
    when(front50Service.getServiceAccounts()).thenReturn(Calls.response(List.of(serviceAccount)));

    SpinnakerUserInfoTokenServices tokenServices =
        new SpinnakerUserInfoTokenServices(
            null,
            null,
            null,
            new OAuth2SsoConfig.UserInfoMapping(),
            null,
            permissionService,
            front50Service,
            Optional.empty(),
            null,
            null,
            null);

    Map<String, Object> details = Map.of("client_email", "ex@foo.com");
    boolean isServiceAccount = tokenServices.isServiceAccount(details);

    assertThat(isServiceAccount).isTrue();
    verify(permissionService).isEnabled();
    verify(front50Service).getServiceAccounts();
  }

  @ParameterizedTest
  @MethodSource("provideRoleData")
  public void shouldExtractRolesFromDetails(Object rolesValue, List<String> expectedRoles) {
    OAuth2SsoConfig.UserInfoMapping userInfoMapping = new OAuth2SsoConfig.UserInfoMapping();
    userInfoMapping.setRoles("roles");
    SpinnakerUserInfoTokenServices tokenServices =
        new SpinnakerUserInfoTokenServices(
            null,
            null,
            null,
            userInfoMapping,
            null,
            null,
            null,
            Optional.empty(),
            null,
            null,
            null);

    Map<String, Object> details = new HashMap<>();
    details.put("roles", rolesValue);
    assertThat(tokenServices.getRoles(details)).isEqualTo(expectedRoles);
  }

  private static Stream<Arguments> provideRoleData() {
    return Stream.of(
        Arguments.of(null, List.of()),
        Arguments.of("", List.of()),
        Arguments.of(List.of("foo", "bar"), List.of("foo", "bar")),
        Arguments.of("foo,bar", List.of("foo", "bar")),
        Arguments.of("foo bar", List.of("foo", "bar")),
        Arguments.of("foo", List.of("foo")),
        Arguments.of("foo   bar", List.of("foo", "bar")),
        Arguments.of("foo,,,bar", List.of("foo", "bar")),
        Arguments.of("foo, bar", List.of("foo", "bar")),
        Arguments.of(List.of("[]"), List.of()),
        Arguments.of(List.of("[\"foo\"]"), List.of("foo")),
        Arguments.of(List.of("[\"foo\", \"bar\"]"), List.of("foo", "bar")),
        Arguments.of(1, List.of()),
        Arguments.of(Map.of("blergh", "blarg"), List.of()));
  }
}
