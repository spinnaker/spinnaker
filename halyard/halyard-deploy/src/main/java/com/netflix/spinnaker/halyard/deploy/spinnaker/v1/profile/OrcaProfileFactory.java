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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Features;
import com.netflix.spinnaker.halyard.config.model.v1.node.Webhook;
import com.netflix.spinnaker.halyard.config.model.v1.providers.aws.AwsProvider;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile.integrations.IntegrationsConfigWrapper;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrcaProfileFactory extends SpringProfileFactory {
  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.ORCA;
  }

  @Override
  public String getMinimumSecretDecryptionVersion(String deploymentName) {
    return "2.4.1";
  }

  @Override
  protected void setProfile(
      Profile profile,
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings endpoints) {
    super.setProfile(profile, deploymentConfiguration, endpoints);

    profile.appendContents(profile.getBaseContents());

    AwsProvider awsProvider = deploymentConfiguration.getProviders().getAws();
    if (awsProvider.isEnabled()) {
      profile.appendContents("default.bake.account: " + awsProvider.getPrimaryAccount());
      profile.appendContents("default.securityGroups: ");
      profile.appendContents("default.vpc.securityGroups: ");
    }

    final Features features = deploymentConfiguration.getFeatures();
    IntegrationsConfigWrapper integrationsConfig = new IntegrationsConfigWrapper(features);
    Webhook webhook = deploymentConfiguration.getWebhook();
    List<String> files = backupRequiredFiles(webhook, deploymentConfiguration.getName());
    profile.setRequiredFiles(files);
    profile
        .appendContents(
            yamlToString(deploymentConfiguration.getName(), profile, new WebhookWrapper(webhook)))
        .appendContents(
            yamlToString(deploymentConfiguration.getName(), profile, integrationsConfig));

    String pipelineTemplates =
        Boolean.toString(
            features.getPipelineTemplates() != null ? features.getPipelineTemplates() : false);
    profile.appendContents("pipelineTemplates.enabled: " + pipelineTemplates);
    // For backward compatibility
    profile.appendContents("pipelineTemplate.enabled: " + pipelineTemplates);
  }

  @Override
  protected String concreteReleaseWithPlugins() {
    return "1.19.0";
  }

  @Data
  @RequiredArgsConstructor
  private static class WebhookWrapper {
    private final Webhook webhook;
  }
}
