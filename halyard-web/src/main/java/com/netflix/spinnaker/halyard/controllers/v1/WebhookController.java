/*
 * Copyright 2018 Google, Inc.
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

import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Webhook;
import com.netflix.spinnaker.halyard.config.model.v1.webook.WebhookTrust;
import com.netflix.spinnaker.halyard.config.services.v1.WebhookService;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.models.v1.ValidationSettings;
import com.netflix.spinnaker.halyard.util.v1.GenericGetRequest;
import com.netflix.spinnaker.halyard.util.v1.GenericUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/webhook")
@RequiredArgsConstructor
public class WebhookController {
  private final WebhookService webhookService;
  private final HalconfigDirectoryStructure halconfigDirectoryStructure;
  private final HalconfigParser halconfigParser;

  @RequestMapping(value = "/", method = RequestMethod.GET)
  DaemonTask<Halconfig, Webhook> getWebhook(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<Webhook>builder()
        .getter(() -> webhookService.getWebhook(deploymentName))
        .validator(() -> webhookService.validateWebhook(deploymentName))
        .description("Get webhook settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setWebhook(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody Webhook webhook) {
    return GenericUpdateRequest.<Webhook>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(t -> webhookService.setWebhook(deploymentName, t))
        .validator(() -> webhookService.validateWebhook(deploymentName))
        .description("Edit webhook settings")
        .build()
        .execute(validationSettings, webhook);
  }

  @RequestMapping(value = "/trust/", method = RequestMethod.GET)
  DaemonTask<Halconfig, WebhookTrust> getWebhookTrust(
      @PathVariable String deploymentName, @ModelAttribute ValidationSettings validationSettings) {
    return GenericGetRequest.<WebhookTrust>builder()
        .getter(() -> webhookService.getWebhookTrust(deploymentName))
        .validator(() -> webhookService.validateWebhookTrust(deploymentName))
        .description("Get webhook trust settings")
        .build()
        .execute(validationSettings);
  }

  @RequestMapping(value = "/trust/", method = RequestMethod.PUT)
  DaemonTask<Halconfig, Void> setWebhookTrust(
      @PathVariable String deploymentName,
      @ModelAttribute ValidationSettings validationSettings,
      @RequestBody WebhookTrust webhookTrust) {
    return GenericUpdateRequest.<WebhookTrust>builder(halconfigParser)
        .stagePath(halconfigDirectoryStructure.getStagingPath(deploymentName))
        .updater(t -> webhookService.setWebhookTrust(deploymentName, t))
        .validator(() -> webhookService.validateWebhookTrust(deploymentName))
        .description("Edit webhook trust settings")
        .build()
        .execute(validationSettings, webhookTrust);
  }
}
