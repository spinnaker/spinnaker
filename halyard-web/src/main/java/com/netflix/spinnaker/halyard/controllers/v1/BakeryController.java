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
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.BakeryDefaults;
import com.netflix.spinnaker.halyard.config.model.v1.node.BaseImage;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Providers;
import com.netflix.spinnaker.halyard.config.services.v1.BakeryService;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.DaemonResponse.StaticRequestBuilder;
import com.netflix.spinnaker.halyard.core.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem.Severity;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.function.Supplier;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/providers/{providerName:.+}/bakery")
public class BakeryController {
  @Autowired
  BakeryService bakeryService;

  @Autowired
  HalconfigParser halconfigParser;

  @Autowired
  ObjectMapper objectMapper;

  @RequestMapping(value = "/defaults/", method = RequestMethod.GET)
  DaemonTask<Halconfig, BakeryDefaults> getBakeryDefaults(@PathVariable String deploymentName, @PathVariable String providerName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    DaemonResponse.StaticRequestBuilder<BakeryDefaults> builder = new DaemonResponse.StaticRequestBuilder<>();

    builder.setBuildResponse(() -> bakeryService.getBakeryDefaults(deploymentName, providerName));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> bakeryService.validateBakeryDefaults(deploymentName, providerName));
    }

    return TaskRepository.submitTask(builder::build);
  }

  @RequestMapping(value = "/defaults/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setBakeryDefaults(@PathVariable String deploymentName, @PathVariable String providerName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawBakeryDefaults) {
    BakeryDefaults bakeryDefaults = objectMapper.convertValue(
        rawBakeryDefaults,
        Providers.translateBakeryDefaultsType(providerName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> bakeryService.setBakeryDefaults(deploymentName, providerName, bakeryDefaults));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;

    if (validate) {
      doValidate = () -> bakeryService.validateBakeryDefaults(deploymentName, providerName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return TaskRepository.submitTask(builder::build);
  }

  @RequestMapping(value = "/defaults/baseImage/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<BaseImage>> images(@PathVariable String deploymentName, @PathVariable String providerName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<List<BaseImage>> builder = new StaticRequestBuilder<>();
    builder.setBuildResponse(() -> bakeryService.getAllBaseImages(deploymentName, providerName));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> bakeryService.validateAllBaseImages(deploymentName, providerName));
    }

    return TaskRepository.submitTask(builder::build);
  }

  @RequestMapping(value = "/defaults/baseImage/{baseImageId:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, BaseImage> baseImage(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String baseImageId,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    StaticRequestBuilder<BaseImage> builder = new StaticRequestBuilder<>();
    builder.setBuildResponse(() -> bakeryService.getProviderBaseImage(deploymentName, providerName, baseImageId));
    builder.setSeverity(severity);

    if (validate) {
      builder.setValidateResponse(() -> bakeryService.validateBaseImage(deploymentName, providerName, baseImageId));
    }

    return TaskRepository.submitTask(builder::build);
  }

  @RequestMapping(value = "/defaults/baseImage/{baseImageId:.+}", method = RequestMethod.DELETE)
  DaemonTask<Halconfig, Void> deleteBaseImage(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String baseImageId,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> bakeryService.deleteBaseImage(deploymentName, providerName, baseImageId));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> bakeryService.validateAllBaseImages(deploymentName, providerName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return TaskRepository.submitTask(builder::build);
  }

  @RequestMapping(value = "/defaults/baseImage/{baseImageId:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setBaseImage(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @PathVariable String baseImageId,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawBaseImage) {
    BaseImage baseImage = objectMapper.convertValue(
        rawBaseImage,
        Providers.translateBaseImageType(providerName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> bakeryService.setBaseImage(deploymentName, providerName, baseImageId, baseImage));
    builder.setSeverity(severity);

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> bakeryService.validateBaseImage(deploymentName, providerName, baseImage.getBaseImage().getId());
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return TaskRepository.submitTask(builder::build);
  }

  @RequestMapping(value = "/defaults/baseImage/", method = RequestMethod.POST)
  DaemonTask<Halconfig, Void> addBaseImage(
      @PathVariable String deploymentName,
      @PathVariable String providerName,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
      @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Severity severity,
      @RequestBody Object rawBaseImage) {
    BaseImage baseImage = objectMapper.convertValue(
        rawBaseImage,
        Providers.translateBaseImageType(providerName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();
    builder.setSeverity(severity);

    // TODO(lwander): Would be good to indicate when an added base image id conflicts with an existing base image id.
    builder.setUpdate(() -> bakeryService.addBaseImage(deploymentName, providerName, baseImage));

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validate) {
      doValidate = () -> bakeryService.validateBaseImage(deploymentName, providerName, baseImage.getBaseImage().getId());
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return TaskRepository.submitTask(builder::build);
  }
}
