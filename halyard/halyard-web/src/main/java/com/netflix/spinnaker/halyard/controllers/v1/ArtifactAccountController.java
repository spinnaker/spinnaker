/*
 * Copyright 2017 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactAccount;
import com.netflix.spinnaker.halyard.config.model.v1.node.Artifacts;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.services.v1.ArtifactAccountService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericDeleteRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(
    "/v1/config/deployments/{deploymentName:.+}/artifactProviders/{providerName:.+}/artifactAccounts")
public class ArtifactAccountController {
  private final ArtifactAccountService accountService;
  private final HalconfigParser halconfigParser;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<ArtifactAccount>> accounts(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<List<ArtifactAccount>>builder()
        .getter(() -> accountService.getAllArtifactAccounts(deploymentName, providerName))
        .validator(() -> accountService.validateAllArtifactAccounts(deploymentName, providerName))
        .description("Get all " + providerName + " artifact accounts")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/account/{accountName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, ArtifactAccount> account(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String accountName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<ArtifactAccount>builder()
        .getter(
            () ->
                accountService.getArtifactProviderArtifactAccount(
                    deploymentName, providerName, accountName))
        .validator(
            () -> accountService.validateArtifactAccount(deploymentName, providerName, accountName))
        .description("Get " + accountName + " artifact account")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/account/{accountName:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deleteArtifactAccount(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String accountName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericDeleteRequest.builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .deleter(
            () -> accountService.deleteArtifactAccount(deploymentName, providerName, accountName))
        .validator(() -> accountService.validateAllArtifactAccounts(deploymentName, providerName))
        .description("Delete the " + accountName + " artifact account")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/account/{accountName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setArtifactAccount(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String accountName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawArtifactAccount) {
    ArtifactAccount account =
        objectMapper.convertValue(
            rawArtifactAccount, Artifacts.translateArtifactAccountType(providerName));
    return GenericUpdateRequest.<ArtifactAccount>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(
            a -> accountService.setArtifactAccount(deploymentName, providerName, accountName, a))
        .validator(
            () ->
                accountService.validateArtifactAccount(
                    deploymentName, providerName, account.getName()))
        .description("Edit the " + accountName + " artifact account")
        .build()
        .execute(validationSettings, account);
  }

  @RequestMapping(value = "/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addArtifactAccount(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawArtifactAccount) {
    ArtifactAccount account =
        objectMapper.convertValue(
            rawArtifactAccount, Artifacts.translateArtifactAccountType(providerName));
    return GenericUpdateRequest.<ArtifactAccount>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(a -> accountService.addArtifactAccount(deploymentName, providerName, a))
        .validator(
            () ->
                accountService.validateArtifactAccount(
                    deploymentName, providerName, account.getName()))
        .description("Add the " + account.getName() + " artifact account")
        .build()
        .execute(validationSettings, account);
  }
}
