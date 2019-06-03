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
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/persistentStorage")
public class PersistentStorageController {
  private final HalconfigParser halconfigParser;
  private final PersistentStorageService persistentStorageService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, PersistentStorage> getPersistentStorage(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<PersistentStorage>builder()
        .getter(() -> persistentStorageService.getPersistentStorage(deploymentName))
        .validator(() -> persistentStorageService.validatePersistentStorage(deploymentName))
        .description("Get persistent storage settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setPersistentStorage(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody PersistentStorage persistentStorage) {
    return GenericUpdateRequest.<PersistentStorage>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(p -> persistentStorageService.setPersistentStorage(deploymentName, p))
        .validator(() -> persistentStorageService.validatePersistentStorage(deploymentName))
        .description("Edit persistent storage settings")
        .build()
        .execute(validationSettings, persistentStorage);
  }

  @RequestMapping(value = "/{persistentStoreType:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, PersistentStore> getPersistentStore(
      @PathVariable String deploymentName,
      @PathVariable String persistentStoreType,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<PersistentStore>builder()
        .getter(
            () -> persistentStorageService.getPersistentStore(deploymentName, persistentStoreType))
        .validator(
            () ->
                persistentStorageService.validatePersistentStore(
                    deploymentName, persistentStoreType))
        .description("Get persistent store")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{persistentStoreType:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setPersistentStore(
      @PathVariable String deploymentName,
      @PathVariable String persistentStoreType,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawPersistentStore) {
    PersistentStore persistentStore =
        objectMapper.convertValue(
            rawPersistentStore,
            PersistentStorage.translatePersistentStoreType(persistentStoreType));
    return GenericUpdateRequest.<PersistentStore>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(p -> persistentStorageService.setPersistentStore(deploymentName, p))
        .validator(
            () ->
                persistentStorageService.validatePersistentStore(
                    deploymentName, persistentStoreType))
        .description("Edit persistent store")
        .build()
        .execute(validationSettings, persistentStore);
  }
}
