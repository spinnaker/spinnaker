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

import com.netflix.spinnaker.halyard.config.config.v1.ResourceConfig;
import com.netflix.spinnaker.halyard.config.model.v1.ci.gcb.GoogleCloudBuild;
import com.netflix.spinnaker.halyard.config.model.v1.node.Artifacts;
import com.netflix.spinnaker.halyard.config.model.v1.node.Cis;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Notifications;
import com.netflix.spinnaker.halyard.config.model.v1.node.Pubsubs;
import com.netflix.spinnaker.halyard.config.model.v1.node.Stats;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EchoProfileFactory extends SpringProfileFactory {

  @Autowired String spinconfigBucket;

  @Autowired boolean gcsEnabled;

  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.ECHO;
  }

  @Override
  public String getMinimumSecretDecryptionVersion(String deploymentName) {
    return "2.3.2";
  }

  @Override
  protected void setProfile(
      Profile profile,
      DeploymentConfiguration deploymentConfiguration,
      SpinnakerRuntimeSettings endpoints) {
    super.setProfile(profile, deploymentConfiguration, endpoints);

    List<String> files = new ArrayList<>();

    profile.appendContents("global.spinnaker.timezone: " + deploymentConfiguration.getTimezone());
    profile.appendContents(
        "spinnaker.baseUrl: " + endpoints.getServiceSettings(Type.DECK).getBaseUrl());

    Notifications notifications = deploymentConfiguration.getNotifications();
    if (notifications != null) {
      files.addAll(backupRequiredFiles(notifications, deploymentConfiguration.getName()));
      profile.appendContents(
          yamlToString(deploymentConfiguration.getName(), profile, notifications));
    }

    Pubsubs pubsubs = deploymentConfiguration.getPubsub();
    if (pubsubs != null) {
      files.addAll(backupRequiredFiles(pubsubs, deploymentConfiguration.getName()));
      profile.appendContents(
          yamlToString(deploymentConfiguration.getName(), profile, new PubsubWrapper(pubsubs)));
    }

    Artifacts artifacts = deploymentConfiguration.getArtifacts();
    if (artifacts != null) {
      files.addAll(backupRequiredFiles(artifacts, deploymentConfiguration.getName()));
      profile.appendContents(
          yamlToString(deploymentConfiguration.getName(), profile, new ArtifactWrapper(artifacts)));
    }

    Cis cis = deploymentConfiguration.getCi();
    if (cis != null) {
      GoogleCloudBuild gcb = cis.getGcb();
      if (gcb != null) {
        files.addAll(backupRequiredFiles(gcb, deploymentConfiguration.getName()));
        profile.appendContents(
            yamlToString(deploymentConfiguration.getName(), profile, new GCBWrapper(gcb)));
      }
    }
    Stats stats = deploymentConfiguration.getStats();
    if (stats != null) {

      // We don't want to accidentally log any PII that may be stuffed into custom BOM bucket names,
      // so we should only log the version if using our public releases (as indicated by using our
      // public GCS bucket).
      String statsVersion = "custom";
      if (gcsEnabled
          && spinconfigBucket.equalsIgnoreCase(ResourceConfig.DEFAULT_HALCONFIG_BUCKET)) {
        statsVersion = deploymentConfiguration.getVersion();
      }
      stats.setSpinnakerVersion(statsVersion);
      stats.setDeploymentMethod(deploymentMethod());
      profile.appendContents(
          yamlToString(deploymentConfiguration.getName(), profile, new StatsWrapper(stats)));
    }

    profile.appendContents(profile.getBaseContents()).setRequiredFiles(files);
  }

  private Stats.DeploymentMethod deploymentMethod() {
    return new Stats.DeploymentMethod()
        .setType(Stats.DeploymentMethod.HALYARD)
        .setVersion(halyardVersion());
  }

  private String halyardVersion() {
    return Optional.ofNullable(EchoProfileFactory.class.getPackage().getImplementationVersion())
        .orElse("Unknown");
  }

  @Data
  private static class PubsubWrapper {
    private Pubsubs pubsub;

    PubsubWrapper(Pubsubs pubsub) {
      this.pubsub = pubsub;
    }
  }

  @Data
  private static class ArtifactWrapper {
    private Artifacts artifacts;

    ArtifactWrapper(Artifacts artifacts) {
      this.artifacts = artifacts;
    }
  }

  @Data
  private static class GCBWrapper {
    private GoogleCloudBuild gcb;

    GCBWrapper(GoogleCloudBuild gcb) {
      this.gcb = gcb;
    }
  }

  @Data
  private static class StatsWrapper {
    private Stats stats;

    StatsWrapper(Stats stats) {
      this.stats = stats;
    }
  }
}
