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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.gate.security.AllowedAccountsSupport;
import com.netflix.spinnaker.gate.services.PermissionService;
import com.netflix.spinnaker.gate.services.internal.Front50Service;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import retrofit2.mock.Calls;

@ExtendWith(MockitoExtension.class)
public class OAuthUserInfoServiceHelperTest {

  @Mock private OAuth2SsoConfig.UserInfoMapping userInfoMapping;

  @Mock private OAuth2SsoConfig.UserInfoRequirements userInfoRequirements;

  @Mock private PermissionService permissionService;

  @Mock private Front50Service front50Service;

  private OAuthUserInfoServiceHelper userInfoService;

  @BeforeEach
  void setUp() {
    // Manually instantiate the class to ensure correct injection
    userInfoService =
        new OAuthUserInfoServiceHelper(
            userInfoMapping,
            userInfoRequirements,
            permissionService,
            front50Service,
            null,
            null,
            null,
            Optional.empty());
  }

  @Test
  void shouldEvaluateUserInfoRequirementsAgainstAuthenticationDetails() {

    // No domain restriction, everything should match
    Map<String, String> requirements = new HashMap<>();
    when(userInfoRequirements.entrySet()).thenReturn(requirements.entrySet());
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of())).isTrue();
    requirements = Map.of("hd", "foo.com");
    when(userInfoRequirements.entrySet()).thenReturn(requirements.entrySet());
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("hd", "foo.com"))).isTrue();
    requirements = Map.of("bar", "foo.com");
    when(userInfoRequirements.entrySet()).thenReturn(requirements.entrySet());
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("bar", "foo.com"))).isTrue();
    requirements = Map.of("bar", "bar.com");
    when(userInfoRequirements.entrySet()).thenReturn(requirements.entrySet());
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("bar", "bar.com"))).isTrue();

    // Domain restricted but not found
    requirements = Map.of("hd", "foo.com");
    when(userInfoRequirements.entrySet()).thenReturn(requirements.entrySet());
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of())).isFalse();
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("hd", "foo.com"))).isTrue();
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("bar", "foo.com"))).isFalse();
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("bar", "bar.com"))).isFalse();

    // Domain restricted by regex
    requirements = Map.of("hd", "/foo\\.com|bar\\.com/");
    when(userInfoRequirements.entrySet()).thenReturn(requirements.entrySet());
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of())).isFalse();
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("hd", "foo.com"))).isTrue();
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("hd", "bar.com"))).isTrue();
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("hd", "baz.com"))).isFalse();
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("bar", "foo.com"))).isFalse();

    // Multiple restriction values
    requirements = Map.of("bar", "bar.com");
    when(userInfoRequirements.entrySet()).thenReturn(requirements.entrySet());
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("hd", "foo.com"))).isFalse();
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("bar", "bar.com"))).isTrue();
    assertThat(
            userInfoService.hasAllUserInfoRequirements(Map.of("hd", "foo.com", "bar", "bar.com")))
        .isTrue();

    // Evaluating a list
    requirements = Map.of("roles", "expected-role");
    when(userInfoRequirements.entrySet()).thenReturn(requirements.entrySet());
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("roles", "expected-role")))
        .isTrue();
    assertThat(
            userInfoService.hasAllUserInfoRequirements(
                Map.of("roles", List.of("expected-role", "unexpected-role"))))
        .isTrue();
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of())).isFalse();
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("roles", "unexpected-role")))
        .isFalse();
    assertThat(
            userInfoService.hasAllUserInfoRequirements(Map.of("roles", List.of("unexpected-role"))))
        .isFalse();

    // Evaluating a regex in a list
    requirements = Map.of("roles", "/^.+_ADMIN$/");
    when(userInfoRequirements.entrySet()).thenReturn(requirements.entrySet());
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("roles", "foo_ADMIN"))).isTrue();
    assertThat(userInfoService.hasAllUserInfoRequirements(Map.of("roles", List.of("foo_ADMIN"))))
        .isTrue();
    assertThat(
            userInfoService.hasAllUserInfoRequirements(
                Map.of("roles", List.of("_ADMIN", "foo_USER"))))
        .isFalse();
    assertThat(
            userInfoService.hasAllUserInfoRequirements(
                Map.of("roles", List.of("foo_ADMINISTRATOR", "bar_USER"))))
        .isFalse();
  }

  @Test
  void testIsServiceAccountValidServiceAccount() {
    Map<String, Object> details = new HashMap<>();
    details.put("email", "service@example.com");

    when(userInfoMapping.getServiceAccountEmail()).thenReturn("email");
    when(permissionService.isEnabled()).thenReturn(true);
    ServiceAccount serviceAccount = new ServiceAccount();
    serviceAccount = serviceAccount.setName("service@example.com");
    when(front50Service.getServiceAccounts()).thenReturn(Calls.response(List.of(serviceAccount)));

    assertThat(userInfoService.isServiceAccount(details)).isTrue();
  }

  @Test
  void testIsServiceAccountNotAServiceAccount() {
    Map<String, Object> details = new HashMap<>();
    details.put("email", "user@example.com");

    when(userInfoMapping.getServiceAccountEmail()).thenReturn("email");
    when(permissionService.isEnabled()).thenReturn(true);
    ServiceAccount serviceAccount = new ServiceAccount();
    serviceAccount = serviceAccount.setName("service@example.com");
    when(front50Service.getServiceAccounts()).thenReturn(Calls.response(List.of(serviceAccount)));

    assertThat(userInfoService.isServiceAccount(details)).isFalse();
  }

  @Test
  void shouldSupportsWhenGetUserInfoReturnsNullValue() {
    Map<String, Object> badAttributes = new HashMap<>();
    badAttributes.put("username", "test-user");
    badAttributes.put("firstName", null); // NPE trigger

    OAuth2User fakeUser = mock(OAuth2User.class);
    when(fakeUser.getAttributes()).thenReturn(badAttributes);

    OAuth2UserRequest request = mock(OAuth2UserRequest.class);
    AllowedAccountsSupport allowedAccountsSupport = mock(AllowedAccountsSupport.class);
    OAuthUserInfoServiceHelper helper =
        spy(
            new OAuthUserInfoServiceHelper(
                userInfoMapping,
                userInfoRequirements,
                permissionService,
                front50Service,
                allowedAccountsSupport,
                null,
                null,
                Optional.empty()));
    String userName = "userName";
    List<String> roles = Collections.singletonList("ROLE_USER");
    OAuthUserInfoServiceHelper.ResolvedUserInfo userInfo =
        new OAuthUserInfoServiceHelper.ResolvedUserInfo(userName, roles);
    doReturn(userInfo).when(helper).getUserInfo(badAttributes, request);

    doReturn(Collections.singletonList("SOME_ACCOUNT"))
        .when(allowedAccountsSupport)
        .filterAllowedAccounts(userName, roles);

    assertThat(helper.getSpinnakerOAuth2User(fakeUser, request).getAttributes().get("firstName"))
        .isNull();
  }

  @ParameterizedTest
  @MethodSource("provideRoleData")
  public void shouldExtractRolesFromDetails(Object rolesValue, List<String> expectedRoles) {
    Map<String, Object> details = new HashMap<>();
    details.put("roles", rolesValue);
    when(userInfoMapping.getRoles()).thenReturn("roles");
    assertThat(userInfoService.getRoles(details)).isEqualTo(expectedRoles);
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
