/*
 * Copyright 2021 Apple Inc.
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

package com.netflix.spinnaker.clouddriver.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.configuration.CredentialsConfiguration;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionRepository;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.kork.annotations.Alpha;
import com.netflix.spinnaker.kork.exceptions.ConfigurationException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/credentials")
public class CredentialsController {
  private final AccountDefinitionRepository repository;
  private final CredentialsConfiguration credentialsConfiguration;
  private final ObjectMapper objectMapper;
  private final AccountCredentialsProvider accountCredentialsProvider;

  public CredentialsController(
      Optional<AccountDefinitionRepository> repository,
      CredentialsConfiguration credentialsConfiguration,
      ObjectMapper objectMapper,
      AccountCredentialsProvider accountCredentialsProvider) {
    this.repository = repository.orElse(null);
    this.credentialsConfiguration = credentialsConfiguration;
    this.objectMapper = objectMapper;
    this.accountCredentialsProvider = accountCredentialsProvider;
  }

  @GetMapping
  public List<Map<String, Object>> listAccountCredentials(@RequestParam Optional<Boolean> expand) {
    boolean shouldExpand = expand.orElse(false);
    return accountCredentialsProvider.getAll().stream()
        .map(accountCredentials -> renderAccountCredentials(accountCredentials, shouldExpand))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @GetMapping("/{accountName}")
  public Map<String, Object> getAccountCredentialsDetails(@PathVariable String accountName) {
    var accountDetail =
        renderAccountCredentials(accountCredentialsProvider.getCredentials(accountName), true);
    if (accountDetail == null) {
      throw new NotFoundException(String.format("Account does not exist (name: %s)", accountName));
    }
    return accountDetail;
  }

  @CheckForNull
  private Map<String, Object> renderAccountCredentials(
      AccountCredentials<?> credentials, boolean expand) {
    if (credentials == null) {
      return null;
    }
    var cred = objectMapper.convertValue(credentials, new TypeReference<Map<String, Object>>() {});
    if (!expand) {
      cred.keySet()
          .retainAll(
              List.of(
                  "name",
                  "environment",
                  "accountType",
                  "cloudProvider",
                  "requiredGroupMembership",
                  "permissions",
                  "accountId"));
    }
    cred.put("type", credentials.getCloudProvider());
    cred.put(
        "challengeDestructiveActions",
        credentialsConfiguration
            .getChallengeDestructiveActionsEnvironments()
            .contains(credentials.getEnvironment()));
    cred.put(
        "primaryAccount",
        credentialsConfiguration.getPrimaryAccountTypes().contains(credentials.getAccountType()));
    return cred;
  }

  @GetMapping("/type/{accountType}")
  // ACCOUNT/WRITE permissions are required to view details of an account;
  // ACCOUNT/READ permissions are only sufficient to view resources that use that account
  // such as load balancers, security groups, clusters, etc.
  @PostFilter("hasPermission(filterObject.name, 'ACCOUNT', 'WRITE')")
  @Alpha
  public List<? extends CredentialsDefinition> listAccountsByType(
      @PathVariable String accountType,
      @RequestParam OptionalInt limit,
      @RequestParam Optional<String> startingAccountName) {
    validateAccountStorageEnabled();
    return repository.listByType(accountType, limit.orElse(100), startingAccountName.orElse(null));
  }

  @PostMapping
  @PreAuthorize("isAuthenticated()")
  @Alpha
  public CredentialsDefinition createAccount(@RequestBody CredentialsDefinition definition) {
    validateAccountStorageEnabled();
    repository.create(definition);
    return definition;
  }

  @PutMapping
  @PreAuthorize("hasPermission(#definition.name, 'ACCOUNT', 'WRITE')")
  @Alpha
  public CredentialsDefinition updateAccount(@RequestBody CredentialsDefinition definition) {
    validateAccountStorageEnabled();
    repository.update(definition);
    return definition;
  }

  @DeleteMapping("/{accountName}")
  @PreAuthorize("hasPermission(#accountName, 'ACCOUNT', 'WRITE')")
  @Alpha
  public void deleteAccount(@PathVariable String accountName) {
    validateAccountStorageEnabled();
    repository.delete(accountName);
  }

  @GetMapping("/{accountName}/history")
  // as with listing accounts, details of the history of an account are restricted to users
  // with ACCOUNT/WRITE permissions; ACCOUNT/READ permissions are related to viewing resources
  // that use that account such as clusters, server groups, load balancers, and server groups
  @PreAuthorize("hasPermission(#accountName, 'ACCOUNT', 'WRITE')")
  @Alpha
  public List<AccountDefinitionRepository.Revision> getAccountHistory(
      @PathVariable String accountName) {
    validateAccountStorageEnabled();
    return repository.revisionHistory(accountName);
  }

  private void validateAccountStorageEnabled() {
    if (repository == null) {
      throw new ConfigurationException(
          "Cannot use AccountDefinitionRepository endpoints without enabling an AccountDefinitionRepository bean");
    }
  }
}
