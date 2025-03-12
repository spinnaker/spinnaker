/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import com.netflix.spinnaker.halyard.config.services.v1.AccountService;
import com.netflix.spinnaker.halyard.core.DaemonOptions;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericDeleteRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/providers/{providerName:.+}/accounts")
public class AccountController {
  private final AccountService accountService;
  private final HalconfigParser halconfigParser;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<Account>> accounts(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<List<Account>>builder()
        .getter(() -> accountService.getAllAccounts(deploymentName, providerName))
        .validator(() -> accountService.validateAllAccounts(deploymentName, providerName))
        .description("Get all " + providerName + " accounts")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/account/{accountName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, Account> account(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String accountName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Account>builder()
        .getter(() -> accountService.getProviderAccount(deploymentName, providerName, accountName))
        .validator(() -> accountService.validateAccount(deploymentName, providerName, accountName))
        .description("Get " + accountName + " account")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/options", method = RequestMethod.POST)
  DaemonTask<Halconfig, List<String>> newAccountOptions(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody DaemonOptions rawAccountOptions) {
    String fieldName = rawAccountOptions.getField();
    Account account =
        objectMapper.convertValue(
            rawAccountOptions.getResource(), Providers.translateAccountType(providerName));
    DaemonResponse.UpdateOptionsRequestBuilder builder =
        new DaemonResponse.UpdateOptionsRequestBuilder();
    String accountName = account.getName();

    builder.setUpdate(() -> accountService.addAccount(deploymentName, providerName, account));
    builder.setFieldOptionsResponse(
        () ->
            accountService.getAccountOptions(deploymentName, providerName, accountName, fieldName));
    builder.setSeverity(validationSettings.getSeverity());

    return DaemonTaskHandler.submitTask(builder::build, "Get " + fieldName + " options");
  }

  @RequestMapping(value = "/account/{accountName:.+}/options", method = RequestMethod.PUT)
  DaemonTask<Halconfig, List<String>> existingAccountOptions(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String accountName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody DaemonOptions rawAccountOptions) {
    String fieldName = rawAccountOptions.getField();
    DaemonResponse.StaticOptionsRequestBuilder builder =
        new DaemonResponse.StaticOptionsRequestBuilder();

    builder.setFieldOptionsResponse(
        () ->
            accountService.getAccountOptions(deploymentName, providerName, accountName, fieldName));
    builder.setSeverity(validationSettings.getSeverity());

    return DaemonTaskHandler.submitTask(builder::build, "Get " + fieldName + " options");
  }

  @RequestMapping(value = "/account/{accountName:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deleteAccount(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String accountName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericDeleteRequest.builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .deleter(() -> accountService.deleteAccount(deploymentName, providerName, accountName))
        .validator(() -> accountService.validateAllAccounts(deploymentName, providerName))
        .description("Delete the " + accountName + " account")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/account/{accountName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setAccount(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String accountName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawAccount) {
    Account account =
        objectMapper.convertValue(rawAccount, Providers.translateAccountType(providerName));
    return GenericUpdateRequest.<Account>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(a -> accountService.setAccount(deploymentName, providerName, accountName, a))
        .validator(
            () -> accountService.validateAccount(deploymentName, providerName, account.getName()))
        .description("Edit the " + accountName + " account")
        .build()
        .execute(validationSettings, account);
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addAccount(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawAccount) {
    Account account =
        objectMapper.convertValue(rawAccount, Providers.translateAccountType(providerName));
    return GenericUpdateRequest.<Account>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(a -> accountService.addAccount(deploymentName, providerName, a))
        .validator(
            () -> accountService.validateAccount(deploymentName, providerName, account.getName()))
        .description("Add the " + account.getName() + " account")
        .build()
        .execute(validationSettings, account);
  }
}
