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
import com.netflix.spinnaker.clouddriver.security.AccountDefinitionService;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.kork.annotations.Beta;
import com.netflix.spinnaker.kork.exceptions.ConfigurationException;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.CheckForNull;
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
  private final AccountDefinitionService accountDefinitionService;
  private final CredentialsConfiguration credentialsConfiguration;
  private final ObjectMapper objectMapper;
  private final AccountCredentialsProvider accountCredentialsProvider;

  public CredentialsController(
      Optional<AccountDefinitionService> service,
      CredentialsConfiguration credentialsConfiguration,
      ObjectMapper objectMapper,
      AccountCredentialsProvider accountCredentialsProvider) {
    this.accountDefinitionService = service.orElse(null);
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
  @Beta
  public List<? extends CredentialsDefinition> listAccountsByType(
      @PathVariable String accountType,
      @RequestParam(required = false, defaultValue = "100") Integer limit,
      @RequestParam(required = false) String startingAccountName) {
    validateAccountStorageEnabled();
    return accountDefinitionService.listAccountDefinitionsByType(
        accountType, limit, startingAccountName);
  }

  @PostMapping
  @Beta
  public CredentialsDefinition createAccount(@RequestBody CredentialsDefinition definition) {
    validateAccountStorageEnabled();
    return accountDefinitionService.createAccount(definition);
  }

  @PutMapping
  @Beta
  public CredentialsDefinition saveAccount(@RequestBody CredentialsDefinition definition) {
    validateAccountStorageEnabled();
    return accountDefinitionService.saveAccount(definition);
  }

  @PutMapping("/{accountName}")
  @Beta
  public CredentialsDefinition updateAccount(
      @RequestBody CredentialsDefinition definition, @PathVariable String accountName) {
    validateAccountStorageEnabled();
    String name = definition.getName();
    if (!accountName.equals(name)) {
      throw new InvalidRequestException(
          String.format(
              "Mismatched account names. URI value: %s. Request body value: %s.",
              accountName, name));
    }
    return accountDefinitionService.updateAccount(definition);
  }

  @DeleteMapping("/{accountName}")
  @Beta
  public void deleteAccount(@PathVariable String accountName) {
    validateAccountStorageEnabled();
    accountDefinitionService.deleteAccount(accountName);
  }

  @GetMapping("/{accountName}/history")
  @Beta
  public List<AccountDefinitionRepository.Revision> getAccountHistory(
      @PathVariable String accountName) {
    validateAccountStorageEnabled();
    return accountDefinitionService.getAccountHistory(accountName);
  }

  private void validateAccountStorageEnabled() {
    if (accountDefinitionService == null) {
      throw new ConfigurationException(
          "Cannot use AccountDefinitionService endpoints without enabling AccountDefinitionService bean");
    }
  }
}
