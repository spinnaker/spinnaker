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
import com.netflix.spinnaker.halyard.config.model.v1.node.ArtifactProvider;
import com.netflix.spinnaker.halyard.config.model.v1.node.Artifacts;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.services.v1.ArtifactProviderService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericEnableDisableRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/artifactProviders")
public class ArtifactProviderController {
  private final HalconfigParser halconfigParser;
  private final ArtifactProviderService providerService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/{providerName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, ArtifactProvider> get(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<ArtifactProvider>builder()
        .getter(() -> providerService.getArtifactProvider(deploymentName, providerName))
        .validator(() -> providerService.validateArtifactProvider(deploymentName, providerName))
        .description("Get the " + providerName + " provider")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{providerName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setArtifactProvider(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawArtifactProvider) {
    ArtifactProvider provider =
        objectMapper.convertValue(
            rawArtifactProvider, Artifacts.translateArtifactProviderType(providerName));
    return GenericUpdateRequest.<ArtifactProvider>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(p -> providerService.setArtifactProvider(deploymentName, p))
        .validator(() -> providerService.validateArtifactProvider(deploymentName, providerName))
        .description("Edit the " + providerName + " provider")
        .build()
        .execute(validationSettings, provider);
  }

  @RequestMapping(value = "/{providerName:.+}/enabled", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setEnabled(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody boolean enabled) {
    return GenericEnableDisableRequest.builder(halconfigParser)
        .updater(e -> providerService.setEnabled(deploymentName, providerName, e))
        .validator(() -> providerService.validateArtifactProvider(deploymentName, providerName))
        .description("Edit the " + providerName + " provider")
        .build()
        .execute(validationSettings, enabled);
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<ArtifactProvider>> providers(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<List<ArtifactProvider>>builder()
        .getter(() -> providerService.getAllArtifactProviders(deploymentName))
        .validator(() -> providerService.validateAllArtifactProviders(deploymentName))
        .description("Get all providers")
        .build()
        .execute(validationSettings);
  }
}
