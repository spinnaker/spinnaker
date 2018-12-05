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
 */

package com.netflix.spinnaker.halyard.controllers.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStorage;
import com.netflix.spinnaker.halyard.config.model.v1.node.PersistentStore;
import com.netflix.spinnaker.halyard.config.services.v1.PersistentStorageService;
import com.netflix.spinnaker.halyard.core.DaemonResponse.UpdateRequestBuilder;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.function.Supplier;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/persistentStorage")
public class PersistentStorageController {
  private final HalconfigParser halconfigParser;
  private final PersistentStorageService persistentStorageService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, PersistentStorage> getPersistentStorage(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<PersistentStorage>builder()
        .getter(() -> persistentStorageService.getPersistentStorage(deploymentName))
        .validator(() -> persistentStorageService.validatePersistentStorage(deploymentName))
        .description("Get persistent storage settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setPersistentStorage(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody PersistentStorage persistentStorage) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> persistentStorage.stageLocalFiles(configPath));
    builder.setUpdate(
        () -> persistentStorageService.setPersistentStorage(deploymentName, persistentStorage));
    builder.setSeverity(validationSettings.getSeverity());

    Supplier<ProblemSet> doValidate = ProblemSet::new;

    if (validationSettings.isValidate()) {
      doValidate = () -> persistentStorageService.validatePersistentStorage(deploymentName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Edit persistent storage settings");
  }

  @RequestMapping(value = "/{persistentStoreType:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, PersistentStore> getPersistentStore(@PathVariable String deploymentName,
      @PathVariable String persistentStoreType,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<PersistentStore>builder()
        .getter(() -> persistentStorageService.getPersistentStore(deploymentName, persistentStoreType))
        .validator(() -> persistentStorageService.validatePersistentStore(deploymentName, persistentStoreType))
        .description("Get persistent store")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{persistentStoreType:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setPersistentStore(@PathVariable String deploymentName,
      @PathVariable String persistentStoreType,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawPersistentStore) {
    PersistentStore persistentStore = objectMapper.convertValue(rawPersistentStore,
        PersistentStorage.translatePersistentStoreType(persistentStoreType));

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> persistentStore.stageLocalFiles(configPath));
    builder.setUpdate(
        () -> persistentStorageService.setPersistentStore(deploymentName, persistentStore));
    builder.setSeverity(validationSettings.getSeverity());

    Supplier<ProblemSet> doValidate = ProblemSet::new;

    if (validationSettings.isValidate()) {
      doValidate = () -> persistentStorageService
          .validatePersistentStore(deploymentName, persistentStoreType);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Edit persistent store");
  }
}
