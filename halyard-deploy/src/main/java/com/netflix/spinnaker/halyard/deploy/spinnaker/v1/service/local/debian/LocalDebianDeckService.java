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
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.debian;

import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.deploy.deployment.v1.DeploymentDetails;
import com.netflix.spinnaker.halyard.deploy.services.v1.ArtifactService;
import com.netflix.spinnaker.halyard.deploy.services.v1.GenerateService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.DeckService;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.HasServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.LogCollector;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.ServiceSettings;
import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.local.LocalLogCollectorFactory;
import io.fabric8.utils.Strings;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Delegate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@EqualsAndHashCode(callSuper = true)
@Data
@Component
public class LocalDebianDeckService extends DeckService implements LocalDebianService<DeckService.Deck> {
  final String upstartServiceName = "apache2";

  @Autowired
  ArtifactService artifactService;

  @Autowired
  LocalLogCollectorFactory localLogCollectorFactory;


  @Delegate(excludes = HasServiceSettings.class)
  LogCollector getLocalLogCollector() {
    return localLogCollectorFactory.build(this, new String[] {
      "/var/log/upstart/apache.log",
      "/var/log/apache/"
    });
  }

  @Override
  public ServiceSettings buildServiceSettings(DeploymentConfiguration deploymentConfiguration) {
    return new Settings(deploymentConfiguration.getSecurity().getUiSecurity())
        .setArtifactId(getArtifactId(deploymentConfiguration.getName()))
        .setEnabled(true);
  }

  @Override
  public String installArtifactCommand(DeploymentDetails deploymentDetails) {
    String install = LocalDebianService.super.installArtifactCommand(deploymentDetails);
    String ssl = deploymentDetails.getDeploymentConfiguration().getSecurity().getUiSecurity().getSsl().isEnabled() ? "a2enmod ssl" : "";
    return Strings.join("\n", install, ssl);
  }

  @Override
  public String stageProfilesCommand(DeploymentDetails details, GenerateService.ResolvedConfiguration resolvedConfiguration) {
    String stage = LocalDebianService.super.stageProfilesCommand(details, resolvedConfiguration);
    return Strings.join("\n", stage, "a2ensite spinnaker");

  }

  public String getArtifactId(String deploymentName) {
    return LocalDebianService.super.getArtifactId(deploymentName);
  }
}
