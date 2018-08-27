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
import com.netflix.spinnaker.halyard.config.model.v1.node.Notifications;
import com.netflix.spinnaker.halyard.config.model.v1.node.Pubsubs;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerArtifact;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.SpinnakerRuntimeSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.SpinnakerService.Type;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class EchoProfileFactory extends SpringProfileFactory {
  @Override
  public SpinnakerArtifact getArtifact() {
    return SpinnakerArtifact.ECHO;
  }

  @Override
  protected void setProfile(Profile profile, DeploymentConfiguration deploymentConfiguration, SpinnakerRuntimeSettings endpoints) {
    super.setProfile(profile, deploymentConfiguration, endpoints);

    List<String> files = new ArrayList<>();

    profile.appendContents("global.spinnaker.timezone: " + deploymentConfiguration.getTimezone());
    profile.appendContents("spinnaker.baseUrl: " + endpoints.getServiceSettings(Type.DECK).getBaseUrl());

    Notifications notifications = deploymentConfiguration.getNotifications();
    if (notifications != null) {
      files.addAll(backupRequiredFiles(notifications, deploymentConfiguration.getName()));
      profile.appendContents(yamlToString(notifications));
    }

    Pubsubs pubsubs = deploymentConfiguration.getPubsub();
    if (pubsubs != null) {
      files.addAll(backupRequiredFiles(pubsubs, deploymentConfiguration.getName()));
      profile.appendContents(yamlToString(new PubsubWrapper(pubsubs)));
    }

    profile.appendContents(profile.getBaseContents())
        .setRequiredFiles(files);
  }

  @Data
  private static class PubsubWrapper {
    private Pubsubs pubsub;

    PubsubWrapper(Pubsubs pubsub) {
      this.pubsub = pubsub;
    }
  }
}
