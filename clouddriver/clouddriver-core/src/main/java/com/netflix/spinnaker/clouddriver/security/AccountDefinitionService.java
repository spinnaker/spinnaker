/*
 * Copyright 2022 Apple Inc.
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

package com.netflix.spinnaker.clouddriver.security;

import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.kork.annotations.Beta;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import com.netflix.spinnaker.kork.annotations.VisibleForTesting;
import com.netflix.spinnaker.kork.secrets.SecretException;
import com.netflix.spinnaker.kork.secrets.user.UserSecretReference;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.security.AuthenticatedRequest;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.ReflectionUtils;

/**
 * Service wrapper for an {@link AccountDefinitionRepository} which enforces permissions and other
 * validations.
 */
@Beta
@NonnullByDefault
@RequiredArgsConstructor
public class AccountDefinitionService {
  private final AccountDefinitionRepository repository;
  private final AccountDefinitionSecretManager secretManager;
  private final AccountCredentialsProvider accountCredentialsProvider;
  private final AccountSecurityPolicy policy;
  private final List<AuthorizedRolesExtractor> extractors;

  /**
   * Lists accounts by type that the current user has {@link Authorization#WRITE} access. Users who
   * only have {@link Authorization#READ} access can only view related items like load balancers,
   * clusters, security groups, etc., that use the account, but they may not directly use or view
   * account definitions and credentials.
   *
   * @see AccountDefinitionRepository#listByType(String, int, String)
   */
  @PreAuthorize("@accountSecurity.isAccountManager(authentication.name)")
  @PostFilter(
      "@accountDefinitionSecretManager.canAccessAccountWithSecrets(authentication.name, filterObject.name)")
  public List<? extends CredentialsDefinition> listAccountDefinitionsByType(
      String accountType, int limit, @Nullable String startingAccountName) {
    return repository.listByType(accountType, limit, startingAccountName);
  }

  @PreAuthorize("@accountSecurity.isAccountManager(authentication.name)")
  public CredentialsDefinition createAccount(CredentialsDefinition definition) {
    String name = definition.getName();
    String username = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous");
    if (accountCredentialsProvider.getCredentials(name) != null) {
      throw new InvalidRequestException(
          String.format("Cannot create an account which already exists (name: %s)", name));
    }
    validateAccountWritePermissions(username, definition, AccountAction.CREATE);
    repository.create(definition);
    return definition;
  }

  @PreAuthorize("@accountSecurity.isAccountManager(authentication.name)")
  public CredentialsDefinition saveAccount(CredentialsDefinition definition) {
    String name = definition.getName();
    String username = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous");
    if (accountCredentialsProvider.getCredentials(name) != null
        && !policy.canModifyAccount(username, name)) {
      throw new AccessDeniedException(
          String.format("Unauthorized to overwrite existing account (name: %s)", name));
    }
    validateAccountWritePermissions(username, definition, AccountAction.SAVE);
    repository.save(definition);
    return definition;
  }

  @PreAuthorize("@accountSecurity.canModifyAccount(authentication.name, #definition.name)")
  public CredentialsDefinition updateAccount(CredentialsDefinition definition) {
    String name = definition.getName();
    if (accountCredentialsProvider.getCredentials(name) == null) {
      throw new InvalidRequestException(
          String.format("Cannot update an account which does not exist (name: %s)", name));
    }
    String username = AuthenticatedRequest.getSpinnakerUser().orElse("anonymous");
    validateAccountWritePermissions(username, definition, AccountAction.UPDATE);
    repository.update(definition);
    return definition;
  }

  @PreAuthorize("@accountSecurity.canModifyAccount(authentication.name, #accountName)")
  public void deleteAccount(String accountName) {
    repository.delete(accountName);
  }

  /**
   * Deletes an account by name if the current user has {@link Authorization#WRITE} access to the
   * given account.
   */
  @PreAuthorize("@accountSecurity.canModifyAccount(authentication.name, #accountName)")
  public List<AccountDefinitionRepository.Revision> getAccountHistory(String accountName) {
    return repository.revisionHistory(accountName);
  }

  private void validateAccountWritePermissions(
      String username, CredentialsDefinition definition, AccountAction action) {
    if (policy.isAdmin(username)) {
      return;
    }
    Set<String> userRoles = policy.getRoles(username);
    validateAccountAuthorization(userRoles, definition, action);
    validateUserSecretAuthorization(userRoles, definition, action);
  }

  @VisibleForTesting
  void validateAccountAuthorization(
      Set<String> userRoles, CredentialsDefinition definition, AccountAction action) {
    String accountName = definition.getName();
    var type = definition.getClass();
    Set<String> authorizedRoles =
        extractors.stream()
            .filter(extractor -> extractor.supportsType(type))
            .flatMap(extractor -> extractor.getAuthorizedRoles(definition).stream())
            .collect(Collectors.toSet());
    // if the account defines authorized roles and the user has no roles in common with these
    // authorized roles, then the user attempted to create an account they'd immediately be
    // locked out from which is a poor user experience
    // (Collections::disjoint returns true if both collections have no elements in common)
    if (!authorizedRoles.isEmpty() && Collections.disjoint(userRoles, authorizedRoles)) {
      throw new InvalidRequestException(
          String.format(
              "Cannot %s account without granting permissions for current user (name: %s)",
              action.name().toLowerCase(Locale.ROOT), accountName));
    }
  }

  @VisibleForTesting
  void validateUserSecretAuthorization(
      Set<String> userRoles, CredentialsDefinition definition, AccountAction action) {
    var type = definition.getClass();
    Set<UserSecretReference> secretReferences = new HashSet<>();
    ReflectionUtils.doWithFields(
        type,
        field -> {
          field.setAccessible(true);
          UserSecretReference.tryParse(field.get(definition)).ifPresent(secretReferences::add);
        },
        field -> field.getType() == String.class);
    // if the account uses any UserSecrets and the user has no roles in common with any of
    // the UserSecrets, then don't allow the user to save this account due to lack of authorization
    for (var ref : secretReferences) {
      try {
        var secret = secretManager.getUserSecret(ref);
        var secretRoles = Set.copyOf(secret.getRoles());
        // (Collections::disjoint returns true if both collections have no elements in common)
        if (Collections.disjoint(userRoles, secretRoles)) {
          throw new AccessDeniedException(
              String.format(
                  "Unauthorized to %s account with user secret %s",
                  action.name().toLowerCase(Locale.ROOT), ref));
        }
      } catch (SecretException e) {
        throw new InvalidRequestException(e);
      }
    }
  }

  @VisibleForTesting
  enum AccountAction {
    CREATE,
    UPDATE,
    SAVE
  }
}
