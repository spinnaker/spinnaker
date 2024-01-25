/*
 * Copyright 2023 Salesforce, Inc.
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

package com.netflix.spinnaker.clouddriver.deploy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionSecretManager;
import com.netflix.spinnaker.clouddriver.security.config.SecurityConfig;
import com.netflix.spinnaker.clouddriver.security.resources.AccountNameable;
import com.netflix.spinnaker.clouddriver.security.resources.ApplicationNameable;
import com.netflix.spinnaker.clouddriver.security.resources.ResourcesNameable;
import com.netflix.spinnaker.fiat.model.resources.ResourceType;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.Getter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class DescriptionAuthorizerServiceTest {

  private final DefaultRegistry registry = new DefaultRegistry();
  private final FiatPermissionEvaluator evaluator = mock(FiatPermissionEvaluator.class);
  private final AccountDefinitionSecretManager secretManager =
      mock(AccountDefinitionSecretManager.class);
  private SecurityConfig.OperationsSecurityConfigurationProperties opsSecurityConfigProps;
  private DescriptionAuthorizerService service;
  private final String username = "testUser";

  @BeforeEach
  public void setup() {
    opsSecurityConfigProps = new SecurityConfig.OperationsSecurityConfigurationProperties();
    service =
        new DescriptionAuthorizerService(
            registry, Optional.of(evaluator), opsSecurityConfigProps, secretManager);
    TestingAuthenticationToken auth = new TestingAuthenticationToken(username, null);
    SecurityContextHolder.getContext().setAuthentication(auth);
  }

  @AfterEach
  public void resetRegistry() {
    registry.reset();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  public void shouldAuthorizePassedDescription(boolean hasPermission) {
    TestDescription description =
        new TestDescription(
            "testAccount",
            Arrays.asList("testApplication", null),
            Arrays.asList("testResource1", "testResource2", null));

    DescriptionValidationErrors errors = new DescriptionValidationErrors(description);

    when(secretManager.canAccessAccountWithSecrets(username, "testAccount"))
        .thenReturn(hasPermission);
    when(evaluator.hasPermission(any(Authentication.class), anyString(), anyString(), anyString()))
        .thenReturn(hasPermission);

    service.authorize(description, errors);

    assertEquals(hasPermission ? 0 : 4, errors.getAllErrors().size());
    verify(secretManager).canAccessAccountWithSecrets(username, "testAccount");
    verify(evaluator, times(3))
        .hasPermission(any(Authentication.class), anyString(), anyString(), anyString());
    verify(evaluator, times(1)).storeWholePermission();

    verifySuccessMetric(hasPermission, "TestDescription");
  }

  private static Stream<Arguments> provideSkipAuthenticationForImageTaggingArgs() {
    return Stream.of(
        Arguments.of(List.of("testAccount"), 0),
        Arguments.of(List.of("anotherAccount"), 1),
        Arguments.of(List.of(), 1));
  }

  @ParameterizedTest
  @MethodSource("provideSkipAuthenticationForImageTaggingArgs")
  public void shouldSkipAuthenticationForImageTaggingDescription(
      List<String> allowUnauthenticatedImageTaggingInAccounts, int expectedNumberOfErrors) {
    TestImageTaggingDescription description = new TestImageTaggingDescription("testAccount");
    DescriptionValidationErrors errors = new DescriptionValidationErrors(description);

    opsSecurityConfigProps.setAllowUnauthenticatedImageTaggingInAccounts(
        allowUnauthenticatedImageTaggingInAccounts);

    service.authorize(description, errors);

    assertEquals(errors.getAllErrors().size(), expectedNumberOfErrors);
    if (!allowUnauthenticatedImageTaggingInAccounts.isEmpty()
        && allowUnauthenticatedImageTaggingInAccounts.get(0).equals("testAccount")) {
      verify(secretManager, never()).canAccessAccountWithSecrets(username, "testAccount");
    } else {
      verify(secretManager).canAccessAccountWithSecrets(username, "testAccount");
    }
    verify(evaluator, never())
        .hasPermission(any(Authentication.class), anyString(), anyString(), anyString());
    verify(evaluator, never()).storeWholePermission();

    verifySuccessMetric(expectedNumberOfErrors == 0, "TestImageTaggingDescription");

    assertEquals(
        expectedNumberOfErrors > 0 ? 0 : 1,
        registry
            .counter("authorization.skipped", "descriptionClass", "TestImageTaggingDescription")
            .count());
  }

  @ParameterizedTest
  @CsvSource({"APPLICATION", "ACCOUNT"})
  public void shouldOnlyAuthzSpecifiedResourceType(ResourceType resourceType) {
    TestDescription description =
        new TestDescription(
            "testAccount",
            Arrays.asList("testApplication", null),
            Arrays.asList("testResource1", "testResource2", null));

    DescriptionValidationErrors errors = new DescriptionValidationErrors(description);

    service.authorize(description, errors, List.of(resourceType));

    if (resourceType.equals(ResourceType.APPLICATION)) {
      verify(evaluator, times(3)).hasPermission(any(Authentication.class), any(), any(), any());
      assertEquals(3, errors.getAllErrors().size());
    } else {
      verify(secretManager).canAccessAccountWithSecrets(username, "testAccount");
      assertEquals(1, errors.getAllErrors().size());
    }

    verifySuccessMetric(false, "TestDescription");
  }

  @Test
  public void shouldAddMetricWithApplicationRestrictionAndNoAccount() {
    TestDescription description = new TestDescription("testAccount", List.of(), List.of());
    DescriptionValidationErrors errors = new DescriptionValidationErrors(description);

    service.authorize(description, errors);

    assertEquals(errors.getAllErrors().size(), 1);
    assertEquals(
        1,
        registry
            .counter(
                "authorization.missingApplication",
                "descriptionClass",
                "TestDescription",
                "hasValidationErrors",
                "true")
            .count());
    verifySuccessMetric(false, "TestDescription");
  }

  private void verifySuccessMetric(boolean success, String descriptionClass) {
    assertEquals(
        1,
        registry
            .counter(
                "authorization",
                "descriptionClass",
                descriptionClass,
                "success",
                String.valueOf(success))
            .count());
  }

  @Getter
  public static class TestDescription
      implements AccountNameable, ApplicationNameable, ResourcesNameable {
    String account;
    Collection<String> applications;
    List<String> names;

    public TestDescription(String account, Collection<String> applications, List<String> names) {
      this.account = account;
      this.applications = applications;
      this.names = names;
    }
  }

  @Getter
  public static class TestImageTaggingDescription implements AccountNameable {
    String account;

    public TestImageTaggingDescription(String account) {
      this.account = account;
    }

    @Override
    public boolean requiresApplicationRestriction() {
      return false;
    }

    @Override
    public boolean requiresAuthorization(
        SecurityConfig.OperationsSecurityConfigurationProperties opsSecurityConfigProps) {
      return !opsSecurityConfigProps
          .getAllowUnauthenticatedImageTaggingInAccounts()
          .contains(account);
    }
  }
}
