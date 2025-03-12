/*
 * Copyright 2024 OpsMx, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.fiat.shared.FiatStatus;
import com.netflix.spinnaker.gate.services.internal.ExtendedFiatService;
import com.netflix.spinnaker.kork.exceptions.SpinnakerException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerConversionException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.security.User;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import retrofit2.mock.Calls;

public class PermissionServiceTest {

  @Test
  public void lookupServiceAccountReturnsEmptyOn404() {
    ExtendedFiatService extendedFiatService = mock(ExtendedFiatService.class);
    String user = "foo@bar.com";

    when(extendedFiatService.getUserServiceAccounts(user)).thenThrow(httpError(404));

    PermissionService subject = new PermissionService(null, extendedFiatService, null, null, null);
    var result = subject.lookupServiceAccounts(user);

    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  private static Stream<TestCase> testCasesForRetryable() {
    return Stream.of(
        new TestCase(httpError(400), false),
        new TestCase(conversionError(), false),
        new TestCase(networkError(), null),
        new TestCase(httpError(500), true),
        new TestCase(unexpectedError(), null));
  }

  @ParameterizedTest
  @MethodSource("testCasesForRetryable")
  public void lookupServiceAccountFailuresAreMarkedRetryable(TestCase testCase) {
    ExtendedFiatService extendedFiatService = mock(ExtendedFiatService.class);
    String user = "foo@bar.com";

    when(extendedFiatService.getUserServiceAccounts(user)).thenThrow(testCase.theFailure);

    PermissionService subject = new PermissionService(null, extendedFiatService, null, null, null);

    SpinnakerException exception =
        assertThrows(SpinnakerException.class, () -> subject.lookupServiceAccounts(user));

    assertEquals(testCase.expectedRetryable, exception.getRetryable());
  }

  private static Stream<TestParams> testCases() {
    return Stream.of(
        new TestParams(
            true,
            "foo@bar.com",
            "myapp",
            true,
            true,
            true,
            false,
            false,
            List.of(Authorization.WRITE),
            List.of("foo-service-account"),
            "successfully performs filtered lookup"),
        new TestParams(
            false,
            "foo@bar.com",
            "myapp",
            true,
            true,
            false,
            true,
            true,
            List.of(Authorization.WRITE),
            List.of("foo-service-account"),
            "filtering disabled"),
        new TestParams(
            true,
            "foo@bar.com",
            "myapp",
            true,
            true,
            false,
            true,
            true,
            List.of(),
            List.of("foo-service-account"),
            "no authorizations to filter on"),
        new TestParams(
            true,
            null,
            "myapp",
            true,
            true,
            false,
            false,
            false,
            List.of(Authorization.WRITE),
            List.of("foo-service-account"),
            "no username supplied"),
        new TestParams(
            true,
            "foo@bar.com",
            null,
            true,
            true,
            false,
            true,
            true,
            List.of(Authorization.WRITE),
            List.of("foo-service-account"),
            "no application supplied"),
        new TestParams(
            true,
            "foo@bar.com",
            "myapp",
            false,
            true,
            false,
            false,
            false,
            List.of(Authorization.WRITE),
            List.of("foo-service-account"),
            "fiat disabled"),
        new TestParams(
            true,
            "foo@bar.com",
            "myapp",
            true,
            false,
            true,
            true,
            true,
            List.of(Authorization.WRITE),
            Collections.emptyList(),
            "no service accounts match"));
  }

  @ParameterizedTest
  @MethodSource("testCases")
  public void testGetServiceAccountsForApplication(TestParams params) {

    FiatStatus fiatStatus = mock(FiatStatus.class);
    FiatPermissionEvaluator permissionEvaluator = mock(FiatPermissionEvaluator.class);
    ExtendedFiatService extendedFiatService = mock(ExtendedFiatService.class);

    ServiceAccountFilterConfigProps cfgProps =
        new ServiceAccountFilterConfigProps(params.enabled, params.auths);
    User user = params.username == null ? null : mock(User.class);
    if (user != null) {
      when(user.getUsername()).thenReturn(params.username);
    }
    when(fiatStatus.isEnabled()).thenReturn(params.fiatEnabled);

    when(extendedFiatService.getUserServiceAccounts(params.username))
        .thenReturn(
            Calls.response(
                params.lookupResultEmpty
                    ? List.of()
                    : List.of(sa("foo-service-account", params.application, params.auths))));

    PermissionService permissionService =
        new PermissionService(null, extendedFiatService, cfgProps, permissionEvaluator, fiatStatus);

    // When: Call the method under test
    permissionService.getServiceAccountsForApplication(user, params.application);

    // Then: Verify the expected interactions
    if (params.expectLookup) {
      verify(extendedFiatService, times(1)).getUserServiceAccounts(params.username);
    } else {
      verify(extendedFiatService, times(0)).getUserServiceAccounts(params.username);
    }

    if (params.expectFallback) {
      verify(permissionEvaluator, times(1)).getPermission(params.username);
    } else {
      verify(permissionEvaluator, times(0)).getPermission(params.username);
    }

    verifyNoMoreInteractions(extendedFiatService, permissionEvaluator);
  }

  private UserPermission.View sa(String name, String application, Collection<Authorization> auths) {
    UserPermission userPermission = new UserPermission();
    userPermission.setId(name);

    Role role = new Role(name).setSource(Role.Source.EXTERNAL);
    userPermission.setRoles(Collections.singleton(role));

    Application app = new Application();
    app.setName(application);

    Permissions.Builder pb = new Permissions.Builder();
    for (Authorization auth : auths) {
      pb.add(auth, name);
    }

    app.setPermissions(pb.build());
    userPermission.setApplications(Collections.singleton(app));

    return userPermission.getView();
  }

  private static Throwable conversionError() {
    Request request = new Request.Builder().url("http://some-url").build();
    return new SpinnakerConversionException("you are bad", new Throwable(), request);
  }

  private static SpinnakerNetworkException networkError() {
    return new SpinnakerNetworkException(
        new IOException("network error"), new Request.Builder().url("http://some-url").build());
  }

  private static Throwable unexpectedError() {
    return new SpinnakerServerException(
        new Throwable(), new Request.Builder().url("http://some-url").build());
  }

  private static Throwable httpError(int code) {
    String url = "https://some-url";
    Response<Object> retrofit2Response =
        Response.error(
            code,
            ResponseBody.create(
                MediaType.parse("application/json"), "{ \"message\": \"arbitrary message\" }"));

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build();

    return new SpinnakerHttpException(retrofit2Response, retrofit);
  }

  private static class TestCase {
    final Throwable theFailure;
    final Boolean expectedRetryable;

    TestCase(Throwable theFailure, Boolean expectedRetryable) {
      this.theFailure = theFailure;
      this.expectedRetryable = expectedRetryable;
    }
  }

  private static class TestParams {
    final boolean enabled;
    final String username;
    final String application;
    final boolean fiatEnabled;
    final boolean hasResult;
    final boolean expectLookup;
    final boolean expectFallback;
    final boolean lookupResultEmpty;
    final List<Authorization> auths;
    final List<String> lookupResult;
    final String desc;

    TestParams(
        boolean enabled,
        String username,
        String application,
        boolean fiatEnabled,
        boolean hasResult,
        boolean expectLookup,
        boolean expectFallback,
        boolean lookupResultEmpty,
        List<Authorization> auths,
        List<String> lookupResult,
        String desc) {
      this.enabled = enabled;
      this.username = username;
      this.application = application;
      this.fiatEnabled = fiatEnabled;
      this.hasResult = hasResult;
      this.expectLookup = expectLookup;
      this.expectFallback = expectFallback;
      this.lookupResultEmpty = lookupResultEmpty;
      this.auths = auths;
      this.lookupResult = lookupResult;
      this.desc = desc;
    }
  }
}
