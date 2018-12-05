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
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Pubsub;
import com.netflix.spinnaker.halyard.config.model.v1.node.Pubsubs;
import com.netflix.spinnaker.halyard.config.services.v1.PubsubService;
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
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/pubsubs")
public class PubsubController {
  private final HalconfigParser halconfigParser;
  private final PubsubService pubsubService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/{pubsubName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, Pubsub> get(@PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Pubsub>builder()
        .getter(() -> pubsubService.getPubsub(deploymentName, pubsubName))
        .validator(() -> pubsubService.validatePubsub(deploymentName, pubsubName))
        .description("Get the " + pubsubName + " pubsub")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{pubsubName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setPubsub(@PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawPubsub) {
    Pubsub pubsub = objectMapper.convertValue(
        rawPubsub,
        Pubsubs.translatePubsubType(pubsubName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> pubsub.stageLocalFiles(configPath));
    builder.setUpdate(() -> pubsubService.setPubsub(deploymentName, pubsub));
    builder.setSeverity(validationSettings.getSeverity());

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validationSettings.isValidate()) {
      doValidate = () -> pubsubService.validatePubsub(deploymentName, pubsubName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler.submitTask(builder::build, "Edit the " + pubsubName + " pubsub");
  }

  @RequestMapping(value = "/{pubsubName:.+}/enabled", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setEnabled(@PathVariable String deploymentName,
      @PathVariable String pubsubName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody boolean enabled) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder.setUpdate(() -> pubsubService.setEnabled(deploymentName, pubsubName, enabled));
    builder.setSeverity(validationSettings.getSeverity());

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validationSettings.isValidate()) {
      doValidate = () -> pubsubService.validatePubsub(deploymentName, pubsubName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Edit the " + pubsubName + " pubsub");
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, List<Pubsub>> pubsubs(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<List<Pubsub>>builder()
        .getter(() -> pubsubService.getAllPubsubs(deploymentName))
        .validator(() -> pubsubService.validateAllPubsubs(deploymentName))
        .description("Get all pubsubs")
        .build()
        .execute(validationSettings);
  }
}
