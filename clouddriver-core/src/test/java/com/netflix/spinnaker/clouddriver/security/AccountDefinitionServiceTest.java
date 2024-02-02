/*
 * Copyright 2024 Salesforce, Inc.
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

package com.netflix.spinnaker.clouddriver.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.kork.secrets.user.UserSecret;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import io.spinnaker.test.security.ValueAccount;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

public class AccountDefinitionServiceTest {
  AccountDefinitionRepository repository;
  AccountDefinitionSecretManager secretManager;
  AccountCredentialsProvider accountCredentialsProvider;
  AccountSecurityPolicy policy;
  AuthorizedRolesExtractor extractor;
  CredentialsDefinition definition =
      ValueAccount.builder().name("name").value("secret://test?k=value").build();
  AccountDefinitionService accountDefinitionService;
  Set<String> authorizedRoles = Set.of("role1", "role2");

  @BeforeEach
  public void setup() {
    repository = mock(AccountDefinitionRepository.class);
    secretManager = mock(AccountDefinitionSecretManager.class);
    accountCredentialsProvider = mock(AccountCredentialsProvider.class);
    policy = mock(AccountSecurityPolicy.class);
    extractor = mock(AuthorizedRolesExtractor.class);
    List<AuthorizedRolesExtractor> extractors = List.of(extractor);
    accountDefinitionService =
        new AccountDefinitionService(
            repository, secretManager, accountCredentialsProvider, policy, extractors);

    doReturn(true).when(extractor).supportsType(definition.getClass());
  }

  @Test
  public void testValidateAccountAuthorizationWithCommonRole() {
    doReturn(authorizedRoles).when(extractor).getAuthorizedRoles(definition);
    Set<String> userRoles = Set.of("role1");
    assertDoesNotThrow(
        () ->
            accountDefinitionService.validateAccountAuthorization(
                userRoles, definition, AccountDefinitionService.AccountAction.UPDATE));
  }

  @Test
  public void testValidateAccountAuthorizationEmptyAuthorizedRoles() {
    doReturn(Set.of()).when(extractor).getAuthorizedRoles(definition);
    Set<String> userRoles = Set.of("role1");
    assertDoesNotThrow(
        () ->
            accountDefinitionService.validateAccountAuthorization(
                userRoles, definition, AccountDefinitionService.AccountAction.UPDATE));
  }

  @Test
  public void testValidateAccountAuthorizationNoCommonRoles() {
    doReturn(authorizedRoles).when(extractor).getAuthorizedRoles(definition);
    Set<String> userRoles = Set.of("oneRole", "anotherRole");
    assertThrows(
        InvalidRequestException.class,
        () ->
            accountDefinitionService.validateAccountAuthorization(
                userRoles, definition, AccountDefinitionService.AccountAction.UPDATE));
  }

  @Test
  public void testValidateUserSecretAuthorizationWithCommonRole() {
    UserSecret userSecret = mock(UserSecret.class);
    doReturn(List.copyOf(authorizedRoles)).when(userSecret).getRoles();
    doReturn(userSecret).when(secretManager).getUserSecret(any());
    Set<String> userRoles = Set.of("role1", "role3");

    assertDoesNotThrow(
        () ->
            accountDefinitionService.validateUserSecretAuthorization(
                userRoles, definition, AccountDefinitionService.AccountAction.UPDATE));
  }

  @Test
  public void testValidateUserSecretAuthorizationEmptyAuthorizedRoles() {
    UserSecret userSecret = mock(UserSecret.class);
    doReturn(List.of()).when(userSecret).getRoles();
    doReturn(userSecret).when(secretManager).getUserSecret(any());
    Set<String> userRoles = Set.of("role1", "role3");

    assertThrows(
        AccessDeniedException.class,
        () ->
            accountDefinitionService.validateUserSecretAuthorization(
                userRoles, definition, AccountDefinitionService.AccountAction.UPDATE));
  }

  @Test
  public void testValidateUserSecretAuthorizationNoCommonRoles() {
    UserSecret userSecret = mock(UserSecret.class);
    doReturn(List.copyOf(authorizedRoles)).when(userSecret).getRoles();
    doReturn(userSecret).when(secretManager).getUserSecret(any());
    Set<String> userRoles = Set.of("role3");

    assertThrows(
        AccessDeniedException.class,
        () ->
            accountDefinitionService.validateUserSecretAuthorization(
                userRoles, definition, AccountDefinitionService.AccountAction.UPDATE));
  }
}
