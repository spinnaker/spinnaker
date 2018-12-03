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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigDirectoryStructure;
import com.netflix.spinnaker.halyard.config.config.v1.HalconfigParser;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Webhook;
import com.netflix.spinnaker.halyard.config.model.v1.webook.WebhookTrust;
import com.netflix.spinnaker.halyard.config.services.v1.WebhookService;
import com.netflix.spinnaker.halyard.core.DaemonResponse;
import com.netflix.spinnaker.halyard.core.problem.v1.Problem;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTask;
import com.netflix.spinnaker.halyard.core.tasks.v1.DaemonTaskHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;

@RestController
@RequestMapping("/v1/config/deployments/{deploymentName:.+}/webhook")
@RequiredArgsConstructor
public class WebhookController {
    private final WebhookService webhookService;
    private final ObjectMapper objectMapper;
    private final HalconfigDirectoryStructure halconfigDirectoryStructure;
    private final HalconfigParser halconfigParser;

    @RequestMapping(value = "/", method = RequestMethod.GET)
    DaemonTask<Halconfig, Webhook> getWebhook(
            @PathVariable String deploymentName,
            @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
            @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Problem.Severity severity
    ) {
        DaemonResponse.StaticRequestBuilder<Webhook> builder = new DaemonResponse.StaticRequestBuilder<>(
                () -> webhookService.getWebhook(deploymentName));

        builder.setSeverity(severity);
        if (validate) {
            builder.setValidateResponse(() -> webhookService.validateWebhook(deploymentName));
        }

        return DaemonTaskHandler.submitTask(builder::build, "Get webhook settings");
    }

    @RequestMapping(value = "/", method = RequestMethod.PUT)
    DaemonTask<Halconfig, Void> setWebhook(
            @PathVariable String deploymentName,
            @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
            @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Problem.Severity severity,
            @RequestBody Object rawWebhook) {
        Webhook webhook = objectMapper.convertValue(rawWebhook, Webhook.class);

        DaemonResponse.UpdateRequestBuilder builder = new DaemonResponse.UpdateRequestBuilder();

        Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
        builder.setStage(() -> webhook.stageLocalFiles(configPath));
        builder.setSeverity(severity);
        builder.setUpdate(() -> webhookService.setWebhook(deploymentName, webhook));

        builder.setValidate(ProblemSet::new);
        if (validate) {
            builder.setValidate(() -> webhookService.validateWebhook(deploymentName));
        }

        builder.setRevert(halconfigParser::undoChanges);
        builder.setSave(halconfigParser::saveConfig);
        builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

        return DaemonTaskHandler.submitTask(builder::build, "Edit webhook settings");
    }

    @RequestMapping(value = "/trust/", method = RequestMethod.GET)
    DaemonTask<Halconfig, WebhookTrust> getWebhookTrust(
            @PathVariable String deploymentName,
            @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
            @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Problem.Severity severity
    ) {
        DaemonResponse.StaticRequestBuilder<WebhookTrust> builder = new DaemonResponse.StaticRequestBuilder<>(
                () -> webhookService.getWebhookTrust(deploymentName));

        builder.setSeverity(severity);
        if (validate) {
            builder.setValidateResponse(() -> webhookService.validateWebhook(deploymentName));
        }

        return DaemonTaskHandler.submitTask(builder::build, "Get webhook trust settings");
    }

    @RequestMapping(value = "/trust/", method = RequestMethod.PUT)
    DaemonTask<Halconfig, Void> setWebhookTrust(
            @PathVariable String deploymentName,
            @RequestParam(required = false, defaultValue = DefaultControllerValues.validate) boolean validate,
            @RequestParam(required = false, defaultValue = DefaultControllerValues.severity) Problem.Severity severity,
            @RequestBody Object rawWebhookTrust) {
        WebhookTrust webhookTrust = objectMapper.convertValue(rawWebhookTrust, WebhookTrust.class);

        DaemonResponse.UpdateRequestBuilder builder = new DaemonResponse.UpdateRequestBuilder();

        Path configPath = halconfigDirectoryStructure.getConfigPath(deploymentName);
        builder.setStage(() -> webhookTrust.stageLocalFiles(configPath));
        builder.setSeverity(severity);
        builder.setUpdate(() -> webhookService.setWebhookTrust(deploymentName, webhookTrust));

        builder.setValidate(ProblemSet::new);
        if (validate) {
            builder.setValidate(() -> webhookService.validateWebhookTrust(deploymentName));
        }

        builder.setRevert(halconfigParser::undoChanges);
        builder.setSave(halconfigParser::saveConfig);
        builder.setClean(() -> halconfigParser.cleanLocalFiles(configPath));

        return DaemonTaskHandler.submitTask(builder::build, "Edit webhook trust settings");
    }
}
