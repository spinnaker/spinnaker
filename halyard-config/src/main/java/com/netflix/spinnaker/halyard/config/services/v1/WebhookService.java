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

package com.netflix.spinnaker.halyard.config.services.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeFilter;
import com.netflix.spinnaker.halyard.config.model.v1.node.Webhook;
import com.netflix.spinnaker.halyard.config.model.v1.webook.WebhookTrust;
import com.netflix.spinnaker.halyard.core.problem.v1.ProblemSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WebhookService {
  private final LookupService lookupService;
  private final DeploymentService deploymentService;
  private final ValidateService validateService;

  public Webhook getWebhook(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setWebhook();

    return lookupService.getSingularNodeOrDefault(
        filter, Webhook.class, Webhook::new, n -> setWebhook(deploymentName, n));
  }

  public void setWebhook(String deploymentName, Webhook newWebhook) {
    DeploymentConfiguration deploymentConfiguration =
        deploymentService.getDeploymentConfiguration(deploymentName);
    deploymentConfiguration.setWebhook(newWebhook);
  }

  public ProblemSet validateWebhook(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setWebhook();
    return validateService.validateMatchingFilter(filter);
  }

  public WebhookTrust getWebhookTrust(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setWebhookTrust();

    return lookupService.getSingularNodeOrDefault(
        filter, WebhookTrust.class, WebhookTrust::new, n -> setWebhookTrust(deploymentName, n));
  }

  public void setWebhookTrust(String deploymentName, WebhookTrust newWebhookTrust) {
    Webhook webhook = getWebhook(deploymentName);
    webhook.setTrust(newWebhookTrust);
  }

  public ProblemSet validateWebhookTrust(String deploymentName) {
    NodeFilter filter = new NodeFilter().setDeployment(deploymentName).setWebhookTrust();
    return validateService.validateMatchingFilter(filter);
  }
}
