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
import com.netflix.spinnaker.halyard.config.model.v1.node.BakeryDefaults;
import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import com.netflix.spinnaker.halyard.config.services.v1.BakeryService;
import com.netflix.spinnaker.halyard.core.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/providers/{providerName:.+}/bakery")
public class BakeryController {
  private final BakeryService bakeryService;
  private final HalconfigParser halconfigParser;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/defaults/", method = RequestMethod.GET)
  DaemonTask<Halconfig, BakeryDefaults> getBakeryDefaults(@PathVariable String deploymentName,
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
  DaemonTask<Halconfig, Void> setBakeryDefaults(@PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawBakeryDefaults) {
    BakeryDefaults bakeryDefaults = objectMapper.convertValue(
        rawBakeryDefaults,
        Providers.translateBakeryDefaultsType(providerName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> bakeryDefaults.stageLocalFiles(configPath));
    builder.setUpdate(
        () -> bakeryService.setBakeryDefaults(deploymentName, providerName, bakeryDefaults));
    builder.setSeverity(validationSettings.getSeverity());

    Supplier<ProblemSet> doValidate = ProblemSet::new;

    if (validationSettings.isValidate()) {
      doValidate = () -> bakeryService.validateBakeryDefaults(deploymentName, providerName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler
        .submitTask(builder::build, "Edit " + providerName + " bakery defaults");
  }

  @RequestMapping(value = "/defaults/baseImage/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<BaseImage>> images(@PathVariable String deploymentName,
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
  DaemonTask<Halconfig, BaseImage> baseImage(@PathVariable String deploymentName,
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
  DaemonTask<Halconfig, Void> deleteBaseImage(@PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String baseImageId,
      @ModelAttribute ValidationSettings validationSettings) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder
        .setUpdate(() -> bakeryService.deleteBaseImage(deploymentName, providerName, baseImageId));
    builder.setSeverity(validationSettings.getSeverity());

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validationSettings.isValidate()) {
      doValidate = () -> bakeryService.validateAllBaseImages(deploymentName, providerName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Delete " + baseImageId + " base image");
  }

  @RequestMapping(value = "/defaults/baseImage/{baseImageId:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setBaseImage(@PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String baseImageId,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawBaseImage) {
    BaseImage baseImage = objectMapper.convertValue(
        rawBaseImage,
        Providers.translateBaseImageType(providerName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> baseImage.stageLocalFiles(configPath));
    builder.setUpdate(
        () -> bakeryService.setBaseImage(deploymentName, providerName, baseImageId, baseImage));
    builder.setSeverity(validationSettings.getSeverity());

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validationSettings.isValidate()) {
      doValidate = () -> bakeryService
          .validateBaseImage(deploymentName, providerName, baseImage.getBaseImage().getId());
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Edit " + baseImageId + " base image");
  }

  @RequestMapping(value = "/defaults/baseImage/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addBaseImage(@PathVariable String deploymentName,
      @PathVariable String providerName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawBaseImage) {
    BaseImage baseImage = objectMapper.convertValue(
        rawBaseImage,
        Providers.translateBaseImageType(providerName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> baseImage.stageLocalFiles(configPath));
    builder.setSeverity(validationSettings.getSeverity());

    // TODO(lwander): Would be good to indicate when an added base image id conflicts with an existing base image id.
    builder.setUpdate(() -> bakeryService.addBaseImage(deploymentName, providerName, baseImage));

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validationSettings.isValidate()) {
      doValidate = () -> bakeryService
          .validateBaseImage(deploymentName, providerName, baseImage.getBaseImage().getId());
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler
        .submitTask(builder::build, "Add " + baseImage.getNodeName() + " base image");
  }
}
