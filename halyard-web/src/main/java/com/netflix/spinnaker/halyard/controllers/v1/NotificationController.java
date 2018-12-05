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
import com.netflix.spinnaker.halyard.config.model.v1.node.Notification;
import com.netflix.spinnaker.halyard.config.model.v1.node.Notifications;
import com.netflix.spinnaker.halyard.config.services.v1.NotificationService;
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
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/notifications")
public class NotificationController {
  private final HalconfigParser halconfigParser;
  private final NotificationService notificationService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final ObjectMapper objectMapper;

  @RequestMapping(value = "/{notificationName:.+}", method = RequestMethod.GET)
  DaemonTask<Halconfig, Notification> notification(@PathVariable String deploymentName,
      @PathVariable String notificationName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Notification>builder()
        .getter(() -> notificationService.getNotification(deploymentName, notificationName))
        .validator(() -> notificationService.validateNotification(deploymentName, notificationName))
        .description("Get " + notificationName + " notification")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{notificationName:.+}/enabled", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setEnabled(@PathVariable String deploymentName,
      @PathVariable String notificationName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody boolean enabled) {
    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    builder
        .setUpdate(() -> notificationService.setEnabled(deploymentName, notificationName, enabled));
    builder.setSeverity(validationSettings.getSeverity());

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validationSettings.isValidate()) {
      doValidate = () -> notificationService.validateNotification(deploymentName, notificationName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());

    return DaemonTaskHandler.submitTask(builder::build, "Edit " + notificationName + " settings");
  }

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, Notifications> notifications(@PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Notifications>builder()
        .getter( () -> notificationService.getNotifications(deploymentName))
        .validator(() -> notificationService.validateAllNotifications(deploymentName))
        .description("Get all notification settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/{notificationName:.+}", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setNotification(@PathVariable String deploymentName,
      @PathVariable String notificationName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Object rawNotification) {
    Notification notification = objectMapper.convertValue(
        rawNotification,
        Notifications.translateNotificationType(notificationName)
    );

    UpdateRequestBuilder builder = new UpdateRequestBuilder();

    Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
    builder.setStage(() -> notification.stageLocalFiles(configPath));
    builder.setUpdate(() -> notificationService.setNotification(deploymentName, notification));
    builder.setSeverity(validationSettings.getSeverity());

    Supplier<ProblemSet> doValidate = ProblemSet::new;
    if (validationSettings.isValidate()) {
      doValidate = () -> notificationService.validateNotification(deploymentName, notificationName);
    }

    builder.setValidate(doValidate);
    builder.setRevert(() -> halconfigParser.undoChanges());
    builder.setSave(() -> halconfigParser.saveConfig());
    builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

    return DaemonTaskHandler
        .submitTask(builder::build, "Edit the " + notificationName + " notification");
  }
}
