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
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.*;
import com.netflix.spinnaker.halyard.config.services.v1.BakeryService;
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
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/providers/{providerName:.+}/bakery")
public class BakeryController {
  private final BakeryService bakeryService;
  private final HalconfigParser halconfigParser;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/defaults/", method = RequestMethod.GET)
  DaemonTask<Halconfig, BakeryDefaults> getBakeryDefaults(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<BakeryDefaults>builder()
        .getter(() -> bakeryService.getBakeryDefaults(deploymentName, providerName))
        .validator(() -> bakeryService.validateBakeryDefaults(deploymentName, providerName))
        .description("Get " + providerName + " bakery defaults")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/defaults/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setBakeryDefaults(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawBakeryDefaults) {
    BakeryDefaults bakeryDefaults =
        objectMapper.convertValue(
            rawBakeryDefaults, Providers.translateBakeryDefaultsType(providerName));
    return GenericUpdateRequest.<BakeryDefaults>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(b -> bakeryService.setBakeryDefaults(deploymentName, providerName, b))
        .validator(() -> bakeryService.validateBakeryDefaults(deploymentName, providerName))
        .description("Edit " + providerName + " bakery defaults")
        .build()
        .execute(validationSettings, bakeryDefaults);
  }

  @RequestMapping(value = "/defaults/baseImage/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<BaseImage>> images(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<List<BaseImage>>builder()
        .getter(() -> bakeryService.getAllBaseImages(deploymentName, providerName))
        .validator(() -> bakeryService.validateAllBaseImages(deploymentName, providerName))
        .description("Get " + providerName + " base images")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/defaults/baseImage/{baseImageId:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, BaseImage> baseImage(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String baseImageId,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<BaseImage>builder()
        .getter(() -> bakeryService.getProviderBaseImage(deploymentName, providerName, baseImageId))
        .validator(() -> bakeryService.validateBaseImage(deploymentName, providerName, baseImageId))
        .description("Get " + baseImageId + " base image")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/defaults/baseImage/{baseImageId:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deleteBaseImage(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String baseImageId,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericDeleteRequest.builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .deleter(() -> bakeryService.deleteBaseImage(deploymentName, providerName, baseImageId))
        .validator(() -> bakeryService.validateAllBaseImages(deploymentName, providerName))
        .description("Delete " + baseImageId + " base image")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/defaults/baseImage/{baseImageId:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setBaseImage(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String baseImageId,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawBaseImage) {
    BaseImage baseImage =
        objectMapper.convertValue(rawBaseImage, Providers.translateBaseImageType(providerName));
    return GenericUpdateRequest.<BaseImage>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(b -> bakeryService.setBaseImage(deploymentName, providerName, baseImageId, b))
        .validator(
            () ->
                bakeryService.validateBaseImage(
                    deploymentName, providerName, baseImage.getBaseImage().getId()))
        .description("Edit " + baseImageId + " base image")
        .build()
        .execute(validationSettings, baseImage);
  }

  @RequestMapping(value = "/defaults/baseImage/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addBaseImage(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawBaseImage) {
    BaseImage baseImage =
        objectMapper.convertValue(rawBaseImage, Providers.translateBaseImageType(providerName));
    // TODO(lwander): Would be good to indicate when an added base image id conflicts with an
    // existing base image id.
    return GenericUpdateRequest.<BaseImage>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(b -> bakeryService.addBaseImage(deploymentName, providerName, b))
        .validator(
            () ->
                bakeryService.validateBaseImage(
                    deploymentName, providerName, baseImage.getBaseImage().getId()))
        .description("Add " + baseImage.getNodeName() + " base image")
        .build()
        .execute(validationSettings, baseImage);
  }
}
