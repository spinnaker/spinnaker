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

package com.netflix.spinnaker.halyard.controllers.v1.ci;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Ci;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.CIAccount;
import com.netflix.spinnaker.halyard.config.services.v1.ci.CiService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericDeleteRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericEnableDisableRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;

import java.util.List;

public abstract class CiController<T extends CIAccount, U extends Ci<T>> {
  protected final ObjectMapper objectMapper = new ObjectMapper();

  @Component
  @RequiredArgsConstructor
  public static class Members {
    private final HalconfigParser halconfigParser;
    private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  }

  public CiController(Members members) {
    this.halconfigParser = members.halconfigParser;
    this.halconfigDirectoryStructure = members.halconfigDirectoryStructure;
  }

  private final HalconfigParser halconfigParser;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;

  protected abstract CiService<T, U> getCiService();
  protected abstract T convertToAccount(Object object);

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, U> ci(@PathVariable String deploymentName,
                              @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<U>builder()
        .getter(() -> getCiService().getCi(deploymentName))
        .validator(() -> getCiService().validateCi(deploymentName))
        .description("Get " + getCiService().ciName() + " ci")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/enabled", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setEnabled(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody boolean enabled) {
    return GenericEnableDisableRequest.builder(halconfigParser)
        .updater(e -> getCiService().setEnabled(deploymentName, e))
        .validator(() -> getCiService().validateCi(deploymentName))
        .description("Edit " + getCiService().ciName() + " settings")
        .build()
        .execute(validationSettings, enabled);
  }

    @RequestMapping(value = "/masters", method = RequestMethod.GET)
    DaemonTask<Halconfig, List<T>> masters(@PathVariable String deploymentName,
                                                   @PathVariable String ciName,
                                                   @ModelAttribute ValidationSettings validationSettings) {
        return GenericGetRequest.<List<T>>builder()
                .getter(() -> getCiService().getAllMasters(deploymentName))
                .validator(() -> getCiService().validateAllMasters(deploymentName))
                .description("Get all masters for " + ciName)
                .build()
                .execute(validationSettings);
    }

    @RequestMapping(value = "/masters/{masterName:.+}", method = RequestMethod.GET)
    DaemonTask<Halconfig, T> master(@PathVariable String deploymentName,
                                            @PathVariable String masterName,
                                            @ModelAttribute ValidationSettings validationSettings) {
        return GenericGetRequest.<T>builder()
                .getter(() -> getCiService().getCiMaster(deploymentName, masterName))
                .validator(() -> getCiService().validateMaster(deploymentName, masterName))
                .description("Get the " + masterName + " master")
                .build()
                .execute(validationSettings);
    }

    @RequestMapping(value = "/masters/{masterName:.+}", method = RequestMethod.DELETE)
    DaemonTask<Halconfig, Void> deleteMaster(@PathVariable String deploymentName,
                                             @PathVariable String masterName,
                                             @ModelAttribute ValidationSettings validationSettings) {
        return GenericDeleteRequest.builder(halconfigParser)
                .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
                .deleter(() -> getCiService().deleteMaster(deploymentName, masterName))
                .validator(() -> getCiService().validateAllMasters(deploymentName))
                .description("Delete the " + masterName + " master")
                .build()
                .execute(validationSettings);
    }

    @RequestMapping(value = "/masters/{masterName:.+}", method = RequestMethod.PUT)
    DaemonTask<Halconfig, Void> setMaster(@PathVariable String deploymentName,
                                          @PathVariable String masterName,
                                          @ModelAttribute ValidationSettings validationSettings,
                                          @RequestBody Object rawMaster) {
        T account = convertToAccount(rawMaster);
        return GenericUpdateRequest.<T>builder(halconfigParser)
                .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
                .updater(m -> getCiService().setMaster(deploymentName, masterName, m))
                .validator(() -> getCiService().validateMaster(deploymentName, account.getName()))
                .description("Edit the " + masterName + " master")
                .build()
                .execute(validationSettings, account);
    }

    @RequestMapping(value = "/masters", method = RequestMethod.POST)
    DaemonTask<Halconfig, Void> addMaster(@PathVariable String deploymentName,
                                          @ModelAttribute ValidationSettings validationSettings,
                                          @RequestBody Object rawMaster) {
        T account = convertToAccount(rawMaster);
        return GenericUpdateRequest.<T>builder(halconfigParser)
                .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
                .updater(m -> getCiService().addMaster(deploymentName, m))
                .validator(() -> getCiService().validateMaster(deploymentName, account.getName()))
                .description("Add the " + account.getName() + " master")
                .build()
                .execute(validationSettings, account);
    }
}
